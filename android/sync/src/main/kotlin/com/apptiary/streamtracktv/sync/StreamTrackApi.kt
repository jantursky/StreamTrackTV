package com.apptiary.streamtracktv.sync

import com.apptiary.streamtracktv.core.pairing.ServerUrl
import com.apptiary.streamtracktv.core.wire.ApiErrorDto
import com.apptiary.streamtracktv.core.wire.PairPollRequestDto
import com.apptiary.streamtracktv.core.wire.PairPollResponseDto
import com.apptiary.streamtracktv.core.wire.PairStartRequestDto
import com.apptiary.streamtracktv.core.wire.PairStartResponseDto
import com.apptiary.streamtracktv.core.wire.SyncPayloadDto
import com.apptiary.streamtracktv.core.wire.SyncResponseDto
import com.apptiary.streamtracktv.core.wire.WireJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** A fresh pairing code, with the server's clock (its `Date` header) at the moment it answered. */
data class IssuedCode(val response: PairStartResponseDto, val serverTimeMs: Long?)

/** An HTTP date such as `Sun, 08 Nov 2026 19:15:00 GMT`, or null when it is not one. */
internal fun parseHttpDateMs(value: String): Long? =
    try {
        ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }

/** HTTP client for the StreamTrack TV server API (`/api/v1`). */
class StreamTrackApi(private val client: HttpClient) {
    suspend fun startPairing(server: ServerUrl, request: PairStartRequestDto): ApiResult<IssuedCode> =
        post(
            server.endpoint("pair/start"),
            null,
            request,
            PairStartRequestDto.serializer(),
            PairStartResponseDto.serializer(),
        ).map { IssuedCode(it.value, it.headers[HttpHeaders.Date]?.let(::parseHttpDateMs)) }

    suspend fun pollPairing(server: ServerUrl, pollToken: String): ApiResult<PairPollResponseDto> =
        post(
            server.endpoint("pair/poll"),
            null,
            PairPollRequestDto(pollToken),
            PairPollRequestDto.serializer(),
            PairPollResponseDto.serializer(),
        ).map { it.value }

    suspend fun sync(server: ServerUrl, deviceToken: String, payload: SyncPayloadDto): ApiResult<SyncResponseDto> =
        post(server.endpoint("sync"), deviceToken, payload, SyncPayloadDto.serializer(), SyncResponseDto.serializer())
            .map { it.value }

    private class Reply<T>(val value: T, val headers: Headers)

    private suspend fun <Req, Res> post(
        url: String,
        token: String?,
        body: Req,
        requestSerializer: KSerializer<Req>,
        responseSerializer: KSerializer<Res>,
    ): ApiResult<Reply<Res>> {
        val response =
            try {
                client.post(url) {
                    token?.let { bearerAuth(it) }
                    setBody(TextContent(WireJson.encodeToString(requestSerializer, body), ContentType.Application.Json))
                }
            } catch (e: IOException) {
                return ApiResult.Failed(ApiFailure.Network(e.message ?: e.javaClass.simpleName))
            }
        val text = response.bodyAsText()
        val status = response.status
        return when {
            status.isSuccess() -> {
                decode(text, responseSerializer)
                    ?.let { ApiResult.Ok(Reply(it, response.headers)) }
                    ?: ApiResult.Failed(ApiFailure.BadResponse("Unexpected response from the server"))
            }

            status == HttpStatusCode.Unauthorized -> {
                ApiResult.Failed(ApiFailure.Unauthorized)
            }

            status == HttpStatusCode.TooManyRequests -> {
                ApiResult.Failed(
                    ApiFailure.RateLimited(response.headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull()),
                )
            }

            status.value >= HttpStatusCode.InternalServerError.value -> {
                ApiResult.Failed(ApiFailure.ServerError(status.value))
            }

            else -> {
                ApiResult.Failed(ApiFailure.Rejected(status.value, decode(text, ApiErrorDto.serializer())))
            }
        }
    }

    private fun <T> decode(text: String, serializer: KSerializer<T>): T? =
        try {
            WireJson.decodeFromString(serializer, text)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000L
        private const val REQUEST_TIMEOUT_MS = 60_000L

        /** The production client, on the platform's HTTP stack. */
        fun create(appVersion: String): StreamTrackApi = StreamTrackApi(httpClient(Android.create(), appVersion))

        /** Client with timeouts and a user agent naming the app version. */
        fun httpClient(engine: HttpClientEngine, appVersion: String): HttpClient =
            HttpClient(engine) {
                expectSuccess = false
                install(HttpTimeout) {
                    connectTimeoutMillis = CONNECT_TIMEOUT_MS
                    requestTimeoutMillis = REQUEST_TIMEOUT_MS
                }
                defaultRequest { header(HttpHeaders.UserAgent, "StreamTrackTV/$appVersion") }
            }
    }
}
