package com.jantursky.streamtracktv.sync

import com.jantursky.streamtracktv.core.model.SecurityEventType
import com.jantursky.streamtracktv.data.pin.PinCheck
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PinResetSessionTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val pollToken = "p".repeat(32)
    private val deviceToken = "d".repeat(64)
    private val started =
        json(
            """{"code":"K7M2QX","pollToken":"$pollToken","expiresAt":"1970-01-01T00:10:00Z"}""",
            HttpStatusCode.Created,
        )
    private val pending = json("""{"status":"pending"}""")
    private val confirmed = json("""{"status":"confirmed"}""")
    private val unauthorized = json("""{"error":"unauthorized","message":"x"}""", HttpStatusCode.Unauthorized)

    private suspend fun pairedStores(scope: kotlinx.coroutines.CoroutineScope): Stores {
        val stores = Stores(scope, folder.root)
        stores.settings.setServerUrl("https://tv.example.com")
        stores.credentials.save(deviceToken)
        stores.pin.setPin("4821")
        return stores
    }

    private val recorder = ListRecorder()

    private fun TestScope.session(stores: Stores, fake: FakeServer) =
        PinResetSession(
            fake.api,
            stores.settings,
            stores.credentials,
            stores.pin,
            now = { testScheduler.currentTime },
            recorder = recorder,
        )

    @Test
    fun `shows the code, polls until confirmed and clears the PIN`() =
        runTest {
            val stores = pairedStores(backgroundScope)
            val fake = FakeServer(listOf(started, pending, confirmed))
            val states = session(stores, fake).reset().toList()
            assertEquals(
                listOf(
                    PinResetState.Requesting,
                    PinResetState.ShowingCode("K7M2QX", "https://tv.example.com/pin-reset?code=K7M2QX", 600_000),
                    PinResetState.Cleared,
                ),
                states,
            )
            assertEquals(PinCheck.NotSet, stores.pin.verify("4821"))
            assertEquals("https://tv.example.com/api/v1/pin-reset/start", fake.requests[0].url.toString())
            assertEquals("Bearer $deviceToken", fake.requests[1].headers[HttpHeaders.Authorization])
            assertEquals("""{"pollToken":"$pollToken"}""", fake.bodyOf(1))
            assertEquals(10_000, testScheduler.currentTime)
        }

    @Test
    fun `an unpaired TV cannot ask for a reset`() =
        runTest {
            val stores = Stores(backgroundScope, folder.root)
            stores.pin.setPin("4821")
            val fake = FakeServer(emptyList())
            assertEquals(
                listOf<PinResetState>(PinResetState.Failed(PinResetFailure.NOT_PAIRED)),
                session(stores, fake).reset().toList(),
            )
            assertTrue(fake.requests.isEmpty())
            assertEquals(PinCheck.Correct, stores.pin.verify("4821"))
        }

    @Test
    fun `a revoked device keeps its PIN and is told it must pair again`() =
        runTest {
            val stores = pairedStores(backgroundScope)
            val states = session(stores, FakeServer(listOf(unauthorized))).reset().toList()
            assertEquals(PinResetState.Failed(PinResetFailure.NOT_PAIRED), states.last())
            assertEquals(PinCheck.Correct, stores.pin.verify("4821"))
        }

    @Test
    fun `an offline TV keeps its PIN`() =
        runTest {
            val stores = pairedStores(backgroundScope)
            val fake = FakeServer(listOf({ throw IOException("Unable to resolve host") }))
            val states = session(stores, fake).reset().toList()
            assertEquals(PinResetState.Failed(PinResetFailure.NETWORK), states.last())
            assertEquals(PinCheck.Correct, stores.pin.verify("4821"))
        }

    @Test
    fun `a code nobody confirms expires and keeps the PIN`() =
        runTest {
            val stores = pairedStores(backgroundScope)
            val fake = FakeServer(listOf(started) + List(200) { pending })
            val states = session(stores, fake).reset().toList()
            assertEquals(PinResetState.Failed(PinResetFailure.EXPIRED), states.last())
            assertEquals(600_000, testScheduler.currentTime)
            assertEquals(PinCheck.Correct, stores.pin.verify("4821"))
        }

    @Test
    fun `the server saying expired ends it and keeps the PIN`() =
        runTest {
            val stores = pairedStores(backgroundScope)
            val fake = FakeServer(listOf(started, json("""{"status":"expired"}""")))
            assertEquals(PinResetState.Failed(PinResetFailure.EXPIRED), session(stores, fake).reset().toList().last())
            assertEquals(PinCheck.Correct, stores.pin.verify("4821"))
        }

    @Test
    fun `slow-down answers are waited out and polling goes on`() =
        runTest {
            val stores = pairedStores(backgroundScope)
            val fake =
                FakeServer(
                    listOf(started, { respond("", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "30")) }, confirmed),
                )
            val states = session(stores, fake).reset().toList()
            assertEquals(PinResetState.Cleared, states.last())
            assertEquals(5_000 + 30_000, testScheduler.currentTime)
            assertEquals(PinCheck.NotSet, stores.pin.verify("4821"))
        }

    @Test
    fun `gives up after repeated failures`() =
        runTest {
            val stores = pairedStores(backgroundScope)
            val fake =
                FakeServer(listOf(started) + List(6) { json("""{"error":"server_error","message":"x"}""", HttpStatusCode.BadGateway) })
            assertEquals(PinResetState.Failed(PinResetFailure.SERVER), session(stores, fake).reset().first { it is PinResetState.Failed })
        }

    @Test
    fun `a confirmed reset is recorded, an expired one is not`() =
        runTest {
            val stores = pairedStores(backgroundScope)
            session(stores, FakeServer(listOf(started, pending, confirmed))).reset().toList()
            assertEquals(listOf<Pair<SecurityEventType, Int?>>(SecurityEventType.PIN_RESET_CONFIRMED to null), recorder.events)
            recorder.events.clear()
            session(stores, FakeServer(listOf(started, json("""{"status":"expired"}""")))).reset().toList()
            assertTrue(recorder.events.isEmpty())
        }
}
