package com.jantursky.streamtracktv.sync

import com.jantursky.streamtracktv.core.pairing.ServerUrl
import com.jantursky.streamtracktv.core.wire.WireLimits
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PairingSessionTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = ServerUrl.parse("https://tv.example.com", allowHttp = false)!!
    private val pollToken = "p".repeat(32)
    private val deviceToken = "d".repeat(64)

    // Virtual clock starts at the epoch; codes expire ten minutes later.
    private fun started(
        code: String,
        expiresAt: String = "1970-01-01T00:10:00Z",
        date: String? = null,
    ) = json(
        """{"code":"$code","pollToken":"$pollToken","expiresAt":"$expiresAt"}""",
        HttpStatusCode.Created,
        *listOfNotNull(date?.let { HttpHeaders.Date to it }).toTypedArray(),
    )

    private val tooFast = json("""{"error":"rate_limited","message":"x"}""", HttpStatusCode.TooManyRequests, HttpHeaders.RetryAfter to "30")

    private val pending = json("""{"status":"pending"}""")
    private val claimed = json("""{"status":"claimed","deviceToken":"$deviceToken"}""")

    private fun TestScope.session(stores: Stores, fake: FakeServer, clockOffsetMs: Long = 0) =
        PairingSession(fake.api, stores.settings, stores.credentials, now = { testScheduler.currentTime + clockOffsetMs })

    @Test
    fun `shows the code, polls until claimed and stores the token`() =
        runTest {
            val stores = Stores(backgroundScope, folder.root)
            val fake = FakeServer(listOf(started("K7M2QX"), pending, pending, claimed))
            val states = session(stores, fake).pair(server, "Living room TV", "Chromecast HD").toList()
            assertEquals(
                listOf(
                    PairingState.Requesting,
                    PairingState.ShowingCode(
                        code = "K7M2QX",
                        link = "https://tv.example.com/pair?code=K7M2QX",
                        expiresAtMs = Instant.parse("1970-01-01T00:10:00Z").toEpochMilli(),
                    ),
                    PairingState.Paired,
                ),
                states,
            )
            assertEquals(deviceToken, stores.credentials.token())
            assertEquals("https://tv.example.com", stores.settings.current().serverUrl)
            assertEquals("""{"deviceName":"Living room TV","model":"Chromecast HD"}""", fake.bodyOf(0))
            assertEquals("https://tv.example.com/api/v1/pair/poll", fake.requests[1].url.toString())
            assertEquals(15_000, testScheduler.currentTime)
        }

    @Test
    fun `an expired code is replaced by a new one`() =
        runTest {
            val stores = Stores(backgroundScope, folder.root)
            val fake = FakeServer(listOf(started("AAAAAA"), json("""{"status":"expired"}"""), started("BBBBBB"), claimed))
            val states = session(stores, fake).pair(server, "TV", null).toList()
            assertEquals(listOf("AAAAAA", "BBBBBB"), states.filterIsInstance<PairingState.ShowingCode>().map { it.code })
        }

    @Test
    fun `a code lives as long as the server says, measured from its arrival`() =
        runTest {
            val stores = Stores(backgroundScope, folder.root)
            val fake =
                FakeServer(
                    listOf(
                        started("AAAAAA", "1970-01-01T00:00:05Z", date = "Thu, 01 Jan 1970 00:00:00 GMT"),
                        pending,
                        started("BBBBBB", "garbage"),
                        claimed,
                    ),
                )
            val states = session(stores, fake).pair(server, "TV", null).toList()
            val codes = states.filterIsInstance<PairingState.ShowingCode>()
            assertEquals(listOf("AAAAAA", "BBBBBB"), codes.map { it.code })
            assertEquals(5_000, codes[0].expiresAtMs)
            // One poll, the pause before the next request, then the schema's ten minutes for an unreadable expiry.
            assertEquals(10_000 + 600_000, codes[1].expiresAtMs)
            assertEquals(PairingState.Paired, states.last())
        }

    @Test
    fun `a TV clock an hour fast still shows the code for its full lifetime`() =
        runTest {
            val stores = Stores(backgroundScope, folder.root)
            val hour = 3_600_000L
            val fake = FakeServer(listOf(started("AAAAAA", date = "Thu, 01 Jan 1970 00:00:00 GMT"), pending, pending, claimed))
            val states = session(stores, fake, clockOffsetMs = hour).pair(server, "TV", null).toList()
            assertEquals(listOf(hour + 600_000), states.filterIsInstance<PairingState.ShowingCode>().map { it.expiresAtMs })
            assertEquals(PairingState.Paired, states.last())
            assertEquals(1, fake.requests.count { it.url.encodedPath.endsWith("pair/start") })
        }

    @Test
    fun `a TV clock an hour slow replaces the code after ten minutes and polls within the server limit`() =
        runTest {
            val stores = Stores(backgroundScope, folder.root)
            val polls = (WireLimits.PAIR_CODE_TTL_SECONDS * 1_000 / PairingSession.DEFAULT_POLL_INTERVAL_MS).toInt()
            // The server allows 150 polls per poll token in a code's lifetime (PAIR_LIMITS.poll in apps/web).
            assertEquals(120, polls)
            assertTrue(polls < 150)
            val fake = FakeServer(listOf(started("AAAAAA")) + List(polls) { pending } + listOf(started("BBBBBB"), claimed))
            val states = session(stores, fake, clockOffsetMs = -3_600_000).pair(server, "TV", null).toList()
            assertEquals(listOf("AAAAAA", "BBBBBB"), states.filterIsInstance<PairingState.ShowingCode>().map { it.code })
            assertEquals(PairingState.Paired, states.last())
        }

    @Test
    fun `stops asking for codes after an hour of unclaimed ones, pausing between requests`() =
        runTest {
            val stores = Stores(backgroundScope, folder.root)
            val expired = json("""{"status":"expired"}""")
            val fake = FakeServer(List(PairingSession.MAX_CODES) { listOf(started("AAAAAA"), expired) }.flatten())
            val states = session(stores, fake).pair(server, "TV", null).toList()
            assertEquals(6, states.count { it is PairingState.ShowingCode })
            assertEquals(PairingState.Failed(PairingFailure.EXPIRED), states.last())
            // Six polls five seconds apart and five pauses of five seconds before a new code.
            assertEquals(55_000, testScheduler.currentTime)
        }

    @Test
    fun `waits out a Retry-After on a poll without giving up`() =
        runTest {
            val stores = Stores(backgroundScope, folder.root)
            val fake = FakeServer(listOf(started("AAAAAA"), tooFast, tooFast, pending, tooFast, tooFast, claimed))
            assertEquals(PairingState.Paired, session(stores, fake).pair(server, "TV", null).toList().last())
            assertEquals(5_000 + 30_000 + 30_000 + 5_000 + 30_000 + 30_000, testScheduler.currentTime)
        }

    @Test
    fun `gives up when the server keeps asking to slow down`() =
        runTest {
            val stores = Stores(backgroundScope, folder.root)
            val fake = FakeServer(listOf(started("AAAAAA"), tooFast, tooFast, tooFast))
            assertEquals(PairingState.Failed(PairingFailure.RATE_LIMITED), session(stores, fake).pair(server, "TV", null).toList().last())
        }

    @Test
    fun `a forgotten poll token counts as expired`() =
        runTest {
            val stores = Stores(backgroundScope, folder.root)
            val fake =
                FakeServer(
                    listOf(
                        started("AAAAAA"),
                        json("""{"error":"not_found","message":"gone"}""", HttpStatusCode.NotFound),
                        started("BBBBBB"),
                        claimed,
                    ),
                )
            assertEquals(PairingState.Paired, session(stores, fake).pair(server, "TV", null).toList().last())
        }

    @Test
    fun `fails when the server cannot be reached`() =
        runTest {
            val stores = Stores(backgroundScope, folder.root)
            val fake = FakeServer(listOf({ throw IOException("no route") }))
            assertEquals(
                listOf(PairingState.Requesting, PairingState.Failed(PairingFailure.NETWORK)),
                session(stores, fake).pair(server, "TV", null).toList(),
            )
            assertNull(stores.credentials.token())
        }

    @Test
    fun `gives up after repeated poll errors but tolerates a few`() =
        runTest {
            val stores = Stores(backgroundScope, folder.root)
            val error = json("""{"error":"server_error","message":"x"}""", HttpStatusCode.InternalServerError)
            val fake = FakeServer(listOf(started("AAAAAA"), error, error, pending) + List(5) { error })
            assertEquals(PairingState.Failed(PairingFailure.SERVER), session(stores, fake).pair(server, "TV", null).toList().last())
            assertEquals(9, fake.requests.size)
        }

    @Test
    fun `maps start failures to reasons`() =
        runTest {
            val cases =
                listOf(
                    json("""{"error":"rate_limited","message":"x"}""", HttpStatusCode.TooManyRequests) to PairingFailure.RATE_LIMITED,
                    json("""{"error":"invalid_payload","message":"x"}""", HttpStatusCode.BadRequest) to PairingFailure.REJECTED,
                    json("""{"error":"unauthorized","message":"x"}""", HttpStatusCode.Unauthorized) to PairingFailure.REJECTED,
                    json("[]") to PairingFailure.SERVER,
                )
            cases.forEachIndexed { index, (answer, reason) ->
                val stores = Stores(backgroundScope, folder.newFolder("case$index"))
                val states = session(stores, FakeServer(listOf(answer))).pair(server, " ", " ").toList()
                assertEquals(PairingState.Failed(reason), states.last())
            }
        }

    @Test
    fun `a rejected poll fails the pairing`() =
        runTest {
            val stores = Stores(backgroundScope, folder.root)
            val rejected = json("""{"error":"invalid_payload","message":"x"}""", HttpStatusCode.BadRequest)
            val states = session(stores, FakeServer(listOf(started("AAAAAA"), rejected))).pair(server, "TV", null).toList()
            assertEquals(PairingState.Failed(PairingFailure.REJECTED), states.last())
        }
}
