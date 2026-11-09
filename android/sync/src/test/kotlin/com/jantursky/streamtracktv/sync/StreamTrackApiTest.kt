package com.jantursky.streamtracktv.sync

import com.jantursky.streamtracktv.core.pairing.ServerUrl
import com.jantursky.streamtracktv.core.wire.ApiErrorCode
import com.jantursky.streamtracktv.core.wire.ApiErrorDto
import com.jantursky.streamtracktv.core.wire.PairPollResponseDto
import com.jantursky.streamtracktv.core.wire.PairStartRequestDto
import com.jantursky.streamtracktv.core.wire.PairStartResponseDto
import com.jantursky.streamtracktv.core.wire.PinResetPollResponseDto
import com.jantursky.streamtracktv.core.wire.PinResetStartResponseDto
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class StreamTrackApiTest {
    private val server = ServerUrl.parse("https://tv.example.com", allowHttp = false)!!
    private val token = "a".repeat(32)

    @Test
    fun `starts pairing with a JSON body and decodes the code`() =
        runTest {
            val body = """{"code":"K7M2QX","pollToken":"$token","expiresAt":"2026-11-08T20:25:00Z","extra":1}"""
            val fake = FakeServer(listOf(json(body, HttpStatusCode.Created, HttpHeaders.Date to "Sun, 08 Nov 2026 20:15:00 GMT")))
            val result = fake.api.startPairing(server, PairStartRequestDto("Living room TV"))
            val code = PairStartResponseDto("K7M2QX", token, "2026-11-08T20:25:00Z")
            assertEquals(ApiResult.Ok(IssuedCode(code, Instant.parse("2026-11-08T20:15:00Z").toEpochMilli())), result)
            val request = fake.requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("https://tv.example.com/api/v1/pair/start", request.url.toString())
            assertEquals("""{"deviceName":"Living room TV"}""", fake.bodyOf(0))
            assertEquals("application/json", request.body.contentType.toString())
            assertEquals("StreamTrackTV/test", request.headers[HttpHeaders.UserAgent])
            assertEquals(null, request.headers[HttpHeaders.Authorization])
        }

    @Test
    fun `starts a PIN reset with the device token and an empty body`() =
        runTest {
            val body = """{"code":"K7M2QX","pollToken":"$token","expiresAt":"2026-11-08T20:25:00Z"}"""
            val fake = FakeServer(listOf(json(body, HttpStatusCode.Created)))
            val result = fake.api.startPinReset(server, token)
            assertEquals(ApiResult.Ok(PinResetStartResponseDto("K7M2QX", token, "2026-11-08T20:25:00Z")), result)
            val request = fake.requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("https://tv.example.com/api/v1/pin-reset/start", request.url.toString())
            assertEquals("Bearer $token", request.headers[HttpHeaders.Authorization])
            assertEquals("{}", fake.bodyOf(0))
        }

    @Test
    fun `decodes every PIN reset poll status and sends the poll token`() =
        runTest {
            val fake =
                FakeServer(
                    listOf(
                        json("""{"status":"pending"}"""),
                        json("""{"status":"expired"}"""),
                        json("""{"status":"confirmed"}"""),
                    ),
                )
            val poll = "p".repeat(32)
            assertEquals(ApiResult.Ok(PinResetPollResponseDto.Pending), fake.api.pollPinReset(server, token, poll))
            assertEquals(ApiResult.Ok(PinResetPollResponseDto.Expired), fake.api.pollPinReset(server, token, poll))
            assertEquals(ApiResult.Ok(PinResetPollResponseDto.Confirmed), fake.api.pollPinReset(server, token, poll))
            assertEquals("""{"pollToken":"$poll"}""", fake.bodyOf(0))
            assertEquals("https://tv.example.com/api/v1/pin-reset/poll", fake.requests[0].url.toString())
            assertEquals("Bearer $token", fake.requests[0].headers[HttpHeaders.Authorization])
        }

    @Test
    fun `a revoked device gets Unauthorized from the PIN reset calls`() =
        runTest {
            val unauthorized = json("""{"error":"unauthorized","message":"Unknown or revoked device token"}""", HttpStatusCode.Unauthorized)
            val fake = FakeServer(listOf(unauthorized, unauthorized))
            assertEquals(ApiResult.Failed(ApiFailure.Unauthorized), fake.api.startPinReset(server, token))
            assertEquals(ApiResult.Failed(ApiFailure.Unauthorized), fake.api.pollPinReset(server, token, token))
        }

    @Test
    fun `reads only proper HTTP dates`() {
        assertEquals(784_111_777_000, parseHttpDateMs("Sun, 06 Nov 1994 08:49:37 GMT"))
        assertEquals(null, parseHttpDateMs("yesterday"))
    }

    @Test
    fun `decodes every poll status`() =
        runTest {
            val fake =
                FakeServer(
                    listOf(
                        json("""{"status":"pending"}"""),
                        json("""{"status":"expired"}"""),
                        json("""{"status":"claimed","deviceToken":"$token"}"""),
                    ),
                )
            assertEquals(ApiResult.Ok(PairPollResponseDto.Pending), fake.api.pollPairing(server, token))
            assertEquals(ApiResult.Ok(PairPollResponseDto.Expired), fake.api.pollPairing(server, token))
            assertEquals(ApiResult.Ok(PairPollResponseDto.Claimed(token)), fake.api.pollPairing(server, token))
            assertEquals("""{"pollToken":"$token"}""", fake.bodyOf(0))
        }

    @Test
    fun `maps error statuses to failures`() =
        runTest {
            val fake =
                FakeServer(
                    listOf(
                        json("""{"error":"unauthorized","message":"Unknown device"}""", HttpStatusCode.Unauthorized),
                        { respond("", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "120")) },
                        { respond("", HttpStatusCode.TooManyRequests) },
                        json("""{"error":"invalid_payload","message":"usage.0.hour"}""", HttpStatusCode.BadRequest),
                        json("<html>teapot</html>", HttpStatusCode.fromValue(418)),
                        json("""{"error":"server_error","message":"boom"}""", HttpStatusCode.ServiceUnavailable),
                        json("not json"),
                        { throw IOException("Unable to resolve host") },
                    ),
                )
            val results = List(8) { fake.api.pollPairing(server, token) }
            assertEquals(
                listOf(
                    ApiResult.Failed(ApiFailure.Unauthorized),
                    ApiResult.Failed(ApiFailure.RateLimited(120)),
                    ApiResult.Failed(ApiFailure.RateLimited(null)),
                    ApiResult.Failed(ApiFailure.Rejected(400, ApiErrorDto(ApiErrorCode.INVALID_PAYLOAD, "usage.0.hour"))),
                    ApiResult.Failed(ApiFailure.Rejected(418, null)),
                    ApiResult.Failed(ApiFailure.ServerError(503)),
                    ApiResult.Failed(ApiFailure.BadResponse("Unexpected response from the server")),
                    ApiResult.Failed(ApiFailure.Network("Unable to resolve host")),
                ),
                results,
            )
        }

    @Test
    fun `describes failures for the status line`() {
        assertEquals("HTTP 401", ApiFailure.Unauthorized.describe())
        assertEquals("HTTP 429", ApiFailure.RateLimited(5).describe())
        assertEquals("HTTP 400 bad hour", ApiFailure.Rejected(400, ApiErrorDto(ApiErrorCode.INVALID_PAYLOAD, "bad hour")).describe())
        assertEquals("HTTP 404", ApiFailure.Rejected(404, null).describe())
        assertEquals("HTTP 500", ApiFailure.ServerError(500).describe())
        assertEquals("offline", ApiFailure.Network("offline").describe())
        assertEquals("odd", ApiFailure.BadResponse("odd").describe())
    }
}
