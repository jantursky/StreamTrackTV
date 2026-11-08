package com.apptiary.streamtracktv.ui

import com.apptiary.streamtracktv.core.insights.HourRange
import com.apptiary.streamtracktv.ui.pairing.formatCountdown
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {
    @Test
    fun `durations read in hours and minutes`() {
        assertEquals("0 min", formatDuration(59_000))
        assertEquals("45 min", formatDuration(45 * 60_000L))
        assertEquals("2 h 05 min", formatDuration(125 * 60_000L))
        assertEquals("0 min", formatDuration(-5))
        assertEquals("3 h", formatDuration(180 * 60_000L))
    }

    @Test
    fun `hour ranges and countdowns`() {
        assertEquals("18:00–24:00", formatHourRange(HourRange(18, 24)))
        assertEquals("9:05", formatCountdown(545_000))
        assertEquals("0:01", formatCountdown(1))
        assertEquals("0:00", formatCountdown(-10))
    }

    @Test
    fun `moments show the time today, the weekday this week and the date before`() {
        val today = LocalDate.parse("2026-11-08")

        fun at(text: String) = LocalDateTime.parse(text).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals("19:05", formatMoment(at("2026-11-08T19:05"), today, ZoneOffset.UTC, Locale.UK))
        assertEquals("Sat 07:30", formatMoment(at("2026-11-07T07:30"), today, ZoneOffset.UTC, Locale.UK))
        assertEquals("Mon 01:00", formatMoment(at("2026-11-09T01:00"), today, ZoneOffset.UTC, Locale.UK))
        assertEquals("1 Oct", formatMoment(at("2026-10-01T10:00"), today, ZoneOffset.UTC, Locale.UK))
        assertEquals("1,234", formatCount(1234, Locale.UK))
    }
}
