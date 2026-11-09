package com.jantursky.streamtracktv.ui

import com.jantursky.streamtracktv.core.insights.HourRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L

/** `2 h 05 min`, `3 h`, `45 min` or `0 min`. Rounded down to whole minutes. */
fun formatDuration(ms: Long): String {
    val minutes = ms.coerceAtLeast(0) / MS_PER_MINUTE
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return when {
        hours == 0L -> "$rest min"
        rest == 0L -> "$hours h"
        else -> "$hours h ${rest.toString().padStart(2, '0')} min"
    }
}

/** `18:00–23:00`. The end of the day reads as `24:00`. */
fun formatHourRange(range: HourRange): String =
    "${range.startHour.toString().padStart(2, '0')}:00–${range.endHour.toString().padStart(2, '0')}:00"

/** `19:05` today, `Sat 19:05` within the last week, else the date. */
fun formatMoment(epochMs: Long, today: LocalDate, zone: ZoneId, locale: Locale = Locale.getDefault()): String {
    val local = Instant.ofEpochMilli(epochMs).atZone(zone)
    val time = local.format(DateTimeFormatter.ofPattern("HH:mm", locale))
    val days = today.toEpochDay() - local.toLocalDate().toEpochDay()
    return when {
        days == 0L -> time
        days in -1..DAYS_SHOWN_AS_WEEKDAY -> "${local.format(DateTimeFormatter.ofPattern("EEE", locale))} $time"
        else -> local.format(DateTimeFormatter.ofPattern("d MMM", locale))
    }
}

private const val DAYS_SHOWN_AS_WEEKDAY = 6L

/** `1,234` style grouping for counts. */
fun formatCount(count: Long, locale: Locale = Locale.getDefault()): String = String.format(locale, "%,d", count)
