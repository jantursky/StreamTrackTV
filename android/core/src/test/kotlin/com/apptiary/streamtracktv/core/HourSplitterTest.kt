package com.apptiary.streamtracktv.core

import com.apptiary.streamtracktv.core.aggregation.SlotShare
import com.apptiary.streamtracktv.core.aggregation.splitByHour
import com.apptiary.streamtracktv.core.aggregation.sumBySlot
import com.apptiary.streamtracktv.core.model.HourSlot
import com.apptiary.streamtracktv.core.model.TimeInterval
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HourSplitterTest {
    private val day = LocalDate.parse("2026-11-08")

    @Test
    fun `keeps an interval inside one hour whole`() {
        val shares = splitByHour(TimeInterval(at("2026-11-08T19:10"), at("2026-11-08T19:40")), BRATISLAVA)
        assertEquals(listOf(SlotShare(HourSlot(day, 19), 30 * MINUTE)), shares)
    }

    @Test
    fun `splits at every hour boundary`() {
        val shares = splitByHour(TimeInterval(at("2026-11-08T19:45"), at("2026-11-08T21:15")), BRATISLAVA)
        assertEquals(
            listOf(
                SlotShare(HourSlot(day, 19), 15 * MINUTE),
                SlotShare(HourSlot(day, 20), HOUR),
                SlotShare(HourSlot(day, 21), 15 * MINUTE),
            ),
            shares,
        )
    }

    @Test
    fun `moves to the next day at midnight`() {
        val shares = splitByHour(TimeInterval(at("2026-11-08T23:30"), at("2026-11-09T00:20")), BRATISLAVA)
        assertEquals(
            listOf(
                SlotShare(HourSlot(day, 23), 30 * MINUTE),
                SlotShare(HourSlot(day.plusDays(1), 0), 20 * MINUTE),
            ),
            shares,
        )
    }

    @Test
    fun `returns nothing for an empty interval`() {
        assertTrue(splitByHour(TimeInterval(at("2026-11-08T10:00"), at("2026-11-08T10:00")), BRATISLAVA).isEmpty())
    }

    @Test
    fun `skips the missing hour when clocks go forward`() {
        // 2026-03-29 02:00 local does not exist in Bratislava: 01:30 to 03:30 is one real hour.
        val start = at("2026-03-29T01:30")
        val shares = splitByHour(TimeInterval(start, start + HOUR), BRATISLAVA)
        val dstDay = LocalDate.parse("2026-03-29")
        assertEquals(
            listOf(SlotShare(HourSlot(dstDay, 1), 30 * MINUTE), SlotShare(HourSlot(dstDay, 3), 30 * MINUTE)),
            shares,
        )
    }

    @Test
    fun `adds up the repeated hour when clocks go back`() {
        // 2026-10-25 02:00-03:00 local happens twice. Three real hours from 01:00 cover 01, 02, 02.
        val start = at("2026-10-25T01:00")
        val totals = sumBySlot(listOf(TimeInterval(start, start + 3 * HOUR)), BRATISLAVA)
        val dstDay = LocalDate.parse("2026-10-25")
        assertEquals(mapOf(HourSlot(dstDay, 1) to HOUR, HourSlot(dstDay, 2) to 2 * HOUR), totals)
    }

    @Test
    fun `uses local hours in half-hour zones`() {
        val kolkata = ZoneId.of("Asia/Kolkata")
        val shares =
            splitByHour(TimeInterval(at("2026-11-08T10:50", kolkata), at("2026-11-08T11:10", kolkata)), kolkata)
        assertEquals(listOf(10, 11), shares.map { it.slot.hour })
        assertEquals(listOf(10 * MINUTE, 10 * MINUTE), shares.map { it.durationMs })
    }

    @Test
    fun `sums overlapping intervals per slot`() {
        val a = TimeInterval(at("2026-11-08T19:00"), at("2026-11-08T19:20"))
        val b = TimeInterval(at("2026-11-08T19:50"), at("2026-11-08T20:10"))
        assertEquals(
            mapOf(HourSlot(day, 19) to 30 * MINUTE, HourSlot(day, 20) to 10 * MINUTE),
            sumBySlot(listOf(a, b), BRATISLAVA),
        )
    }
}
