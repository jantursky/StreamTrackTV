package com.jantursky.streamtracktv.core

import com.jantursky.streamtracktv.core.model.HourSlot
import com.jantursky.streamtracktv.core.model.KeyBucket
import com.jantursky.streamtracktv.core.model.KeyClass
import com.jantursky.streamtracktv.core.model.ScreenBucket
import com.jantursky.streamtracktv.core.model.SyncInterval
import com.jantursky.streamtracktv.core.model.TimeInterval
import com.jantursky.streamtracktv.core.model.UsageBucket
import com.jantursky.streamtracktv.core.model.bucketKey
import com.jantursky.streamtracktv.core.model.union
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ModelTest {
    private val slot = HourSlot(LocalDate.parse("2026-11-08"), 7)

    @Test
    fun `bucket keys match the schema package`() {
        assertEquals("2026-11-08T07|app|com.a.b", UsageBucket(slot, "com.a.b", 1).bucketKey())
        assertEquals("2026-11-08T07|key|back", KeyBucket(slot, KeyClass.BACK, 1).bucketKey())
        assertEquals("2026-11-08T07|screen", ScreenBucket(slot, 1).bucketKey())
    }

    @Test
    fun `hour slots order by day then hour and roll over at midnight`() {
        val late = HourSlot(LocalDate.parse("2026-11-08"), 23)
        assertEquals(HourSlot(LocalDate.parse("2026-11-09"), 0), late.next())
        assertEquals(HourSlot(slot.day, 8), slot.next())
        assertEquals(listOf(slot, late, late.next()), listOf(late.next(), late, slot).sorted())
        assertEquals(slot, HourSlot.parse("2026-11-08", 7))
    }

    @Test
    fun `rejects hours outside the day`() {
        assertFailsWith<IllegalArgumentException> { HourSlot(slot.day, 24) }
        assertFailsWith<IllegalArgumentException> { HourSlot(slot.day, -1) }
    }

    @Test
    fun `key classes round trip through their wire names`() {
        assertEquals(
            listOf("dpad", "select", "back", "home", "media", "volume", "other"),
            KeyClass.entries.map { it.wireName },
        )
        KeyClass.entries.forEach { assertEquals(it, KeyClass.fromWireName(it.wireName)) }
        assertNull(KeyClass.fromWireName("KEYCODE_DPAD_UP"))
    }

    @Test
    fun `intervals clip and merge`() {
        val interval = TimeInterval(10, 20)
        assertEquals(TimeInterval(12, 20), interval.clip(12, 30))
        assertNull(interval.clip(20, 30))
        assertEquals(
            listOf(TimeInterval(0, 25), TimeInterval(30, 40)),
            listOf(TimeInterval(30, 40), TimeInterval(10, 20), TimeInterval(0, 12), TimeInterval(20, 25)).union(),
        )
        assertFailsWith<IllegalArgumentException> { TimeInterval(5, 4) }
    }

    @Test
    fun `sync interval stays within what WorkManager allows`() {
        assertEquals(15, SyncInterval.clamp(1))
        assertEquals(1440, SyncInterval.clamp(5000))
        assertEquals(30, SyncInterval.next(15))
        assertEquals(15, SyncInterval.next(1440))
        assertEquals(60, SyncInterval.next(45))
    }
}
