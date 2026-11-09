package com.jantursky.streamtracktv.sync

import com.jantursky.streamtracktv.core.model.BucketSet
import com.jantursky.streamtracktv.core.model.HourSlot
import com.jantursky.streamtracktv.core.model.KeyBucket
import com.jantursky.streamtracktv.core.model.KeyClass
import com.jantursky.streamtracktv.core.model.ScreenBucket
import com.jantursky.streamtracktv.core.model.SecurityEventType
import com.jantursky.streamtracktv.core.model.UsageBucket
import com.jantursky.streamtracktv.core.payload.MAX_BATCH_BYTES
import com.jantursky.streamtracktv.core.wire.EventsRequestDto
import com.jantursky.streamtracktv.core.wire.SyncPayloadDto
import com.jantursky.streamtracktv.core.wire.WireJson
import com.jantursky.streamtracktv.core.wire.WireLimits
import com.jantursky.streamtracktv.data.settings.SyncRecord
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SyncEngineTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val token = "t".repeat(64)
    private val slot = HourSlot(LocalDate.parse("2026-11-08"), 19)
    private val now = Instant.parse("2026-11-08T19:15:00Z")
    private val ok = json("""{"accepted":{"usage":1,"keys":1,"screen":1},"serverTime":"2026-11-08T19:15:01Z"}""")

    private suspend fun TestScope.paired(): Stores {
        val stores = Stores(backgroundScope, folder.root)
        stores.settings.setServerUrl("https://tv.example.com")
        stores.credentials.save(token)
        return stores
    }

    private fun engine(stores: Stores, server: FakeServer) =
        SyncEngine(
            server.api,
            stores.usage,
            stores.settings,
            stores.credentials,
            stores.securityLog,
            { now },
            { ZoneId.of("Europe/Bratislava") },
        )

    private fun payloadOf(server: FakeServer, index: Int): SyncPayloadDto =
        WireJson.decodeFromString(SyncPayloadDto.serializer(), server.bodyOf(index))

    private val rows =
        BucketSet(
            usage = listOf(UsageBucket(slot, "com.netflix.ninja", 1_800_000)),
            screen = listOf(ScreenBucket(slot, 3_000_000)),
        )

    @Test
    fun `does nothing before pairing`() =
        runTest {
            val stores = Stores(backgroundScope, folder.root)
            stores.usage.record(rows)
            val server = FakeServer(emptyList())
            assertEquals(SyncOutcome.NotPaired, engine(stores, server).sync())
            assertTrue(server.requests.isEmpty())
            assertNull(stores.settings.current().lastSync)
        }

    @Test
    fun `uploads pending rows with the device token and marks them sent`() =
        runTest {
            val stores = paired()
            stores.usage.record(rows)
            stores.usage.addKeyPresses(listOf(KeyBucket(slot, KeyClass.DPAD, 42)))
            stores.usage.saveLabels(mapOf("com.netflix.ninja" to "Netflix"))
            val server = FakeServer(listOf(ok))

            assertEquals(SyncOutcome.Synced(3), engine(stores, server).sync())

            val request = server.requests.single()
            assertEquals("https://tv.example.com/api/v1/sync", request.url.toString())
            assertEquals("Bearer $token", request.headers[HttpHeaders.Authorization])
            assertEquals(
                """{"schemaVersion":1,"sentAt":"2026-11-08T20:15:00+01:00","timezone":"Europe/Bratislava",""" +
                    """"usage":[{"day":"2026-11-08","hour":19,"packageName":"com.netflix.ninja",""" +
                    """"label":"Netflix","foregroundMs":1800000}],""" +
                    """"keys":[{"day":"2026-11-08","hour":19,"keyClass":"dpad","count":42}],""" +
                    """"screen":[{"day":"2026-11-08","hour":19,"screenOnMs":3000000}]}""",
                server.bodyOf(0),
            )
            assertEquals(0, stores.usage.observePendingCount().first())
            assertEquals(SyncRecord(now.toEpochMilli(), ok = true, message = null), stores.settings.current().lastSync)
        }

    @Test
    fun `nothing pending means no request`() =
        runTest {
            val stores = paired()
            val server = FakeServer(emptyList())
            assertEquals(SyncOutcome.Synced(0), engine(stores, server).sync())
            assertTrue(server.requests.isEmpty())
        }

    @Test
    fun `a failed upload is re-sent with the same absolute values`() =
        runTest {
            val stores = paired()
            stores.usage.record(rows)
            val server = FakeServer(listOf(json("""{"error":"server_error","message":"x"}""", HttpStatusCode.BadGateway), ok))
            val engine = engine(stores, server)

            assertEquals(SyncOutcome.RetryLater("HTTP 502"), engine.sync())
            assertEquals(2, stores.usage.observePendingCount().first())
            assertEquals(SyncRecord(now.toEpochMilli(), ok = false, message = "HTTP 502"), stores.settings.current().lastSync)

            assertEquals(SyncOutcome.Synced(2), engine.sync())
            assertEquals(payloadOf(server, 0), payloadOf(server, 1))
            assertEquals(0, stores.usage.observePendingCount().first())
        }

    @Test
    fun `only changed buckets go out again, with their new total`() =
        runTest {
            val stores = paired()
            stores.usage.record(rows)
            val server = FakeServer(listOf(ok, ok))
            val engine = engine(stores, server)
            engine.sync()
            stores.usage.record(rows.copy(usage = listOf(UsageBucket(slot, "com.netflix.ninja", 2_400_000))))
            engine.sync()
            val second = payloadOf(server, 1)
            assertEquals(listOf(2_400_000L), second.usage.map { it.foregroundMs })
            assertTrue(second.screen.isEmpty())
        }

    @Test
    fun `a revoked token unpairs the TV`() =
        runTest {
            val stores = paired()
            stores.usage.record(rows)
            val server = FakeServer(listOf(json("""{"error":"unauthorized","message":"Device removed"}""", HttpStatusCode.Unauthorized)))
            assertEquals(SyncOutcome.Unauthorized, engine(stores, server).sync())
            assertNull(stores.credentials.token())
            assertFalse(stores.credentials.isPaired.first())
            assertEquals(
                "HTTP 401",
                stores.settings
                    .current()
                    .lastSync
                    ?.message,
            )
            assertEquals(2, stores.usage.observePendingCount().first(), "Data stays local for the next pairing")
        }

    @Test
    fun `rate limiting and network errors ask for a retry`() =
        runTest {
            val stores = paired()
            stores.usage.record(rows)
            val server =
                FakeServer(
                    listOf(
                        json(
                            """{"error":"rate_limited","message":"Slow down"}""",
                            HttpStatusCode.TooManyRequests,
                            HttpHeaders.RetryAfter to "3600",
                        ),
                        json("""{"error":"rate_limited","message":"Slow down"}""", HttpStatusCode.TooManyRequests),
                        { throw IOException("timeout") },
                    ),
                )
            val engine = engine(stores, server)
            assertEquals(SyncOutcome.RetryLater("HTTP 429", retryAfterSeconds = 3600), engine.sync())
            assertEquals(SyncOutcome.RetryLater("HTTP 429"), engine.sync())
            assertEquals(SyncOutcome.RetryLater("timeout"), engine.sync())
            assertEquals(2, stores.usage.observePendingCount().first())
        }

    @Test
    fun `a refused batch is halved and a row refused on its own is set aside`() =
        runTest {
            val stores = paired()
            stores.usage.record(rows)
            val server =
                FakeServer(
                    listOf(
                        json("""{"error":"invalid_payload","message":"too large"}""", HttpStatusCode.PayloadTooLarge),
                        json("""{"error":"invalid_payload","message":"usage.0.packageName"}""", HttpStatusCode.BadRequest),
                        ok,
                    ),
                )
            val engine = engine(stores, server)

            assertEquals(SyncOutcome.Synced(1), engine.sync())
            assertEquals(listOf(2, 1, 1), List(3) { payloadOf(server, it).let { p -> p.usage.size + p.screen.size } })
            assertEquals(listOf("com.netflix.ninja"), payloadOf(server, 1).usage.map { it.packageName })
            assertEquals(listOf(3_000_000L), payloadOf(server, 2).screen.map { it.screenOnMs })
            assertEquals(0, stores.usage.observePendingCount().first())
            assertEquals(1, stores.settings.current().rejectedRows)

            // A new value for the set-aside row gets another chance.
            stores.usage.record(rows.copy(usage = listOf(UsageBucket(slot, "com.netflix.ninja", 2_400_000)), screen = emptyList()))
            assertEquals(1, stores.usage.observePendingCount().first())
        }

    @Test
    fun `the queue keeps moving past a bad row in a large backlog`() =
        runTest {
            val stores = paired()
            val day = LocalDate.parse("2026-01-01")
            val many = (0 until 8).map { i -> ScreenBucket(HourSlot(day, i), 1000L + i) }
            stores.usage.record(BucketSet(screen = many))
            val refused = json("""{"error":"invalid_payload","message":"screen.2"}""", HttpStatusCode.BadRequest)
            // 8 refused, 4 refused, 2 sent, 2 refused, the bad row set aside, then the rest one by one.
            val server = FakeServer(listOf(refused, refused, ok, refused, refused, ok, ok, ok, ok, ok))
            assertEquals(SyncOutcome.Synced(7), engine(stores, server).sync())
            assertEquals(listOf(8, 4, 2, 2, 1, 1, 1, 1, 1, 1), List(10) { payloadOf(server, it).screen.size })
            assertEquals(listOf(1002L), payloadOf(server, 4).screen.map { it.screenOnMs })
            assertEquals(1, stores.settings.current().rejectedRows)
            assertEquals(0, stores.usage.observePendingCount().first())
        }

    @Test
    fun `a batch over the byte limit is split before sending`() =
        runTest {
            val stores = paired()
            val names = (0 until WireLimits.MAX_ROWS_PER_KIND).map { "com.n$it." + "a".repeat(WireLimits.MAX_PACKAGE_LENGTH - 12) }
            val day = LocalDate.parse("2026-01-01")
            val screen = names.indices.map { i -> ScreenBucket(HourSlot(day.plusDays((i / 24).toLong()), i % 24), 1000L) }
            stores.usage.record(BucketSet(usage = names.map { UsageBucket(slot, it, 1000) }, screen = screen))
            stores.usage.saveLabels(names.associateWith { "L".repeat(WireLimits.MAX_LABEL_LENGTH) })
            val server = FakeServer(listOf(ok, ok))
            // Both kinds together are over the limit, so they go out one after the other.
            assertEquals(SyncOutcome.Synced(2 * names.size), engine(stores, server).sync())
            assertEquals(
                listOf(names.size to 0, 0 to names.size),
                List(2) {
                    payloadOf(server, it).let { p ->
                        p.usage.size to p.screen.size
                    }
                },
            )
            assertTrue(server.requests.indices.all { server.bodyOf(it).length <= MAX_BATCH_BYTES })
        }

    @Test
    fun `other refusals fail without retry`() =
        runTest {
            val stores = paired()
            stores.usage.record(rows)
            val server = FakeServer(listOf(json("""{"error":"invalid_payload","message":"nope"}""", HttpStatusCode.Forbidden)))
            assertEquals(SyncOutcome.Failed("HTTP 403 nope"), engine(stores, server).sync())
            assertEquals(2, stores.usage.observePendingCount().first())
        }

    @Test
    fun `splits a large backlog into batches within the row limit`() =
        runTest {
            val stores = paired()
            val day = LocalDate.parse("2026-01-01")
            val many =
                (0 until WireLimits.MAX_ROWS_PER_KIND + 10).map { i ->
                    ScreenBucket(HourSlot(day.plusDays((i / 24).toLong()), i % 24), 1000L)
                }
            stores.usage.record(BucketSet(screen = many))
            val server = FakeServer(listOf(ok, ok))
            assertEquals(SyncOutcome.Synced(many.size), engine(stores, server).sync())
            assertEquals(listOf(WireLimits.MAX_ROWS_PER_KIND, 10), List(2) { payloadOf(server, it).screen.size })
        }

    @Test
    fun `the privacy preview is the next payload without excluded apps`() =
        runTest {
            val stores = paired()
            stores.usage.record(rows.copy(usage = rows.usage + UsageBucket(slot, "com.secret.app", 5)))
            stores.settings.setExcluded("com.secret.app", true)
            val preview = engine(stores, FakeServer(emptyList())).nextBatch()
            assertEquals(listOf("com.netflix.ninja"), preview.payload.usage.map { it.packageName })
            assertEquals(2, preview.rows.usage.size)
        }

    // Security events

    private val eventsOk = json("""{"accepted":3}""")

    private fun eventsOf(server: FakeServer, index: Int): EventsRequestDto =
        WireJson.decodeFromString(EventsRequestDto.serializer(), server.bodyOf(index))

    @Test
    fun `sends pending events after the usage rows and marks them sent`() =
        runTest {
            val stores = paired()
            stores.usage.record(rows)
            stores.securityLog.record(SecurityEventType.PIN_WRONG, 3)
            stores.securityLog.record(SecurityEventType.FORGOT_PIN_OPENED)
            val server = FakeServer(listOf(ok, eventsOk))

            assertEquals(SyncOutcome.Synced(2), engine(stores, server).sync())

            assertEquals("https://tv.example.com/api/v1/events", server.requests[1].url.toString())
            assertEquals("Bearer $token", server.requests[1].headers[HttpHeaders.Authorization])
            val sent = eventsOf(server, 1).events
            assertEquals(listOf(SecurityEventType.PIN_WRONG, SecurityEventType.FORGOT_PIN_OPENED), sent.map { it.type })
            assertEquals(listOf(3, null), sent.map { it.value })
            assertTrue(stores.securityLog.pending().isEmpty())
        }

    @Test
    fun `events are sent even when there is no usage to send`() =
        runTest {
            val stores = paired()
            stores.securityLog.record(SecurityEventType.APP_OPENED)
            val server = FakeServer(listOf(eventsOk))
            assertEquals(SyncOutcome.Synced(0), engine(stores, server).sync())
            assertEquals(1, server.requests.size)
            assertTrue(stores.securityLog.pending().isEmpty())
        }

    @Test
    fun `a failed event upload keeps the events and does not fail the sync`() =
        runTest {
            val stores = paired()
            stores.securityLog.record(SecurityEventType.PIN_WRONG, 1)
            val server = FakeServer(listOf({ throw IOException("offline") }))
            assertEquals(SyncOutcome.Synced(0), engine(stores, server).sync())
            assertEquals(1, stores.securityLog.pending().size)
            val retry = FakeServer(listOf(eventsOk))
            engine(stores, retry).sync()
            assertTrue(stores.securityLog.pending().isEmpty())
        }

    @Test
    fun `a server without the events route leaves the events for later`() =
        runTest {
            val stores = paired()
            stores.securityLog.record(SecurityEventType.APP_OPENED)
            val missing = json("""{"error":"not_found","message":"x"}""", HttpStatusCode.NotFound)
            val server = FakeServer(listOf(missing))
            assertEquals(SyncOutcome.Synced(0), engine(stores, server).sync())
            assertEquals(1, stores.securityLog.pending().size)
        }

    @Test
    fun `an unpaired TV keeps its events`() =
        runTest {
            val stores = Stores(backgroundScope, folder.root)
            stores.securityLog.record(SecurityEventType.APP_OPENED)
            val server = FakeServer(emptyList())
            assertEquals(SyncOutcome.NotPaired, engine(stores, server).sync())
            assertTrue(server.requests.isEmpty())
            assertEquals(1, stores.securityLog.pending().size)
        }

    @Test
    fun `a refused batch is halved and a single refused event is set aside`() =
        runTest {
            val stores = paired()
            repeat(2) { stores.securityLog.record(SecurityEventType.PIN_WRONG, it) }
            val refused = json("""{"error":"invalid_payload","message":"x"}""", HttpStatusCode.BadRequest)
            val server = FakeServer(listOf(refused, eventsOk, refused))
            assertEquals(SyncOutcome.Synced(0), engine(stores, server).sync())
            assertEquals(listOf(2, 1, 1), server.requests.indices.map { eventsOf(server, it).events.size })
            assertTrue(stores.securityLog.pending().isEmpty())
        }

    @Test
    fun `more than one batch of events is sent in the same run`() =
        runTest {
            val stores = paired()
            repeat(WireLimits.MAX_EVENTS_PER_BATCH + 20) { stores.securityLog.record(SecurityEventType.APP_OPENED) }
            val server = FakeServer(listOf(eventsOk, eventsOk))
            engine(stores, server).sync()
            assertEquals(
                listOf(WireLimits.MAX_EVENTS_PER_BATCH, 20),
                server.requests.indices.map { eventsOf(server, it).events.size },
            )
            assertTrue(stores.securityLog.pending().isEmpty())
        }
}
