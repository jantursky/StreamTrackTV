package com.apptiary.streamtracktv.core

import com.apptiary.streamtracktv.core.model.BucketSet
import com.apptiary.streamtracktv.core.model.HourSlot
import com.apptiary.streamtracktv.core.model.KeyBucket
import com.apptiary.streamtracktv.core.model.KeyClass
import com.apptiary.streamtracktv.core.model.ScreenBucket
import com.apptiary.streamtracktv.core.model.UsageBucket
import com.apptiary.streamtracktv.core.payload.MAX_BATCH_BYTES
import com.apptiary.streamtracktv.core.payload.buildBatch
import com.apptiary.streamtracktv.core.payload.encodedSize
import com.apptiary.streamtracktv.core.payload.formatSentAt
import com.apptiary.streamtracktv.core.payload.wireLabel
import com.apptiary.streamtracktv.core.wire.SyncPayloadDto
import com.apptiary.streamtracktv.core.wire.WireJson
import com.apptiary.streamtracktv.core.wire.WireLimits
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PayloadMappingTest {
    private val slot = HourSlot(LocalDate.parse("2026-11-08"), 19)
    private val now = Instant.parse("2026-11-08T19:15:00.789Z")

    @Test
    fun `builds the payload the schema sample describes`() {
        val pending =
            BucketSet(
                usage =
                    listOf(
                        UsageBucket(slot, "com.netflix.ninja", 1_800_000),
                        UsageBucket(slot, "com.google.android.youtube.tv", 600_000),
                    ),
                keys = listOf(KeyBucket(slot, KeyClass.DPAD, 42)),
                screen = listOf(ScreenBucket(slot, 3_000_000)),
            )
        val batch = buildBatch(pending, mapOf("com.netflix.ninja" to "Netflix"), emptySet(), now, BRATISLAVA)
        val expected = Json.parseToJsonElement(javaClass.getResource("/contract/sync-payload.json")!!.readText())
        assertEquals(expected, WireJson.encodeToJsonElement(SyncPayloadDto.serializer(), batch.payload))
        assertEquals(pending, batch.rows)
    }

    @Test
    fun `caps values at the schema limits but keeps stored values for bookkeeping`() {
        val pending =
            BucketSet(
                usage = listOf(UsageBucket(slot, "com.a.b", 2 * WireLimits.MS_PER_HOUR)),
                keys = listOf(KeyBucket(slot, KeyClass.OTHER, 5_000_000)),
                screen = listOf(ScreenBucket(slot, -5)),
            )
        val batch = buildBatch(pending, emptyMap(), emptySet(), now, ZoneOffset.UTC)
        assertEquals(
            WireLimits.MS_PER_HOUR,
            batch.payload.usage
                .single()
                .foregroundMs,
        )
        assertEquals(
            WireLimits.MAX_KEY_COUNT,
            batch.payload.keys
                .single()
                .count,
        )
        assertEquals(
            0,
            batch.payload.screen
                .single()
                .screenOnMs,
        )
        assertEquals(pending, batch.rows)
        assertEquals("2026-11-08T19:15:00Z", batch.payload.sentAt)
        assertEquals("Z", batch.payload.timezone)
    }

    @Test
    fun `leaves out excluded and invalid packages yet settles their rows`() {
        val pending =
            BucketSet(
                usage =
                    listOf(
                        UsageBucket(slot, "com.secret.app", 1),
                        UsageBucket(slot, "android", 1),
                        UsageBucket(slot, "com.ok.app", 1),
                    ),
            )
        val batch = buildBatch(pending, emptyMap(), setOf("com.secret.app"), now, BRATISLAVA)
        assertEquals(listOf("com.ok.app"), batch.payload.usage.map { it.packageName })
        assertEquals(3, batch.rows.usage.size)
    }

    @Test
    fun `splits more rows than one batch carries`() {
        val many = (0 until WireLimits.MAX_ROWS_PER_KIND + 5).map { KeyBucket(slot, KeyClass.DPAD, it.toLong()) }
        val pending = BucketSet(keys = many)
        val batch = buildBatch(pending, emptyMap(), emptySet(), now, BRATISLAVA)
        assertEquals(WireLimits.MAX_ROWS_PER_KIND, batch.payload.keys.size)
        assertEquals(WireLimits.MAX_ROWS_PER_KIND, batch.rows.size)
    }

    @Test
    fun `a row limit takes the oldest rows, usage first`() {
        val pending =
            BucketSet(
                usage = listOf(UsageBucket(slot, "com.a.b", 1), UsageBucket(slot, "com.c.d", 1)),
                keys = listOf(KeyBucket(slot, KeyClass.DPAD, 1)),
                screen = listOf(ScreenBucket(slot, 1)),
            )
        assertEquals(
            BucketSet(usage = pending.usage.take(1)),
            buildBatch(pending, emptyMap(), emptySet(), now, BRATISLAVA, maxRows = 1).rows,
        )
        assertEquals(pending.copy(screen = emptyList()), buildBatch(pending, emptyMap(), emptySet(), now, BRATISLAVA, maxRows = 3).rows)
    }

    @Test
    fun `a batch is halved until its JSON fits the byte limit`() {
        val labels = (0 until 2000).associate { "com.app.n$it" to "L".repeat(WireLimits.MAX_LABEL_LENGTH) }
        val pending = BucketSet(usage = labels.keys.map { UsageBucket(slot, it, 1) })
        val full = buildBatch(pending, labels, emptySet(), now, BRATISLAVA, maxBytes = Int.MAX_VALUE)
        assertEquals(2000, full.rows.size)

        val limit = encodedSize(full.payload) / 3
        val fitted = buildBatch(pending, labels, emptySet(), now, BRATISLAVA, maxBytes = limit)
        assertEquals(500, fitted.rows.size)
        assertTrue(encodedSize(fitted.payload) <= limit)
        // The real limit holds 2000 rows with long labels in a single batch.
        assertTrue(encodedSize(full.payload) <= MAX_BATCH_BYTES)
    }

    @Test
    fun `a single row is returned even when it is over the byte limit`() {
        val pending = BucketSet(screen = listOf(ScreenBucket(slot, 1), ScreenBucket(slot, 2)))
        assertEquals(1, buildBatch(pending, emptyMap(), emptySet(), now, BRATISLAVA, maxBytes = 1).rows.size)
    }

    @Test
    fun `labels are trimmed, capped and dropped when blank`() {
        assertEquals("Netflix", wireLabel("  Netflix "))
        assertEquals(WireLimits.MAX_LABEL_LENGTH, wireLabel("x".repeat(500))?.length)
        assertNull(wireLabel("   "))
        assertNull(wireLabel(null))
    }

    @Test
    fun `sent at carries the local offset and whole seconds`() {
        assertEquals("2026-11-08T20:15:00+01:00", formatSentAt(now, BRATISLAVA))
    }
}
