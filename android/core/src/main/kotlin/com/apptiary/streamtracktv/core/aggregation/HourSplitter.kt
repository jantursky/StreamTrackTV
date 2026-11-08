package com.apptiary.streamtracktv.core.aggregation

import com.apptiary.streamtracktv.core.model.HourSlot
import com.apptiary.streamtracktv.core.model.TimeInterval
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** How much of an interval falls into one local hour. */
public data class SlotShare(val slot: HourSlot, val durationMs: Long)

/**
 * Splits [interval] at local hour boundaries of [zone].
 *
 * Hours are walked on the instant timeline, so a daylight saving change yields a 23 or 25 hour day.
 * When clocks go back, the repeated local hour maps to the same [HourSlot] and its two shares add up
 * in [sumBySlot]; the payload mapper caps them at one hour.
 */
public fun splitByHour(interval: TimeInterval, zone: ZoneId): List<SlotShare> {
    val shares = mutableListOf<SlotShare>()
    var cursor = interval.startMs
    while (cursor < interval.endMs) {
        val local = Instant.ofEpochMilli(cursor).atZone(zone)
        val nextHourMs =
            local
                .truncatedTo(ChronoUnit.HOURS)
                .plusHours(1)
                .toInstant()
                .toEpochMilli()
        val end = minOf(nextHourMs, interval.endMs)
        shares += SlotShare(HourSlot(local.toLocalDate(), local.hour), end - cursor)
        cursor = end
    }
    return shares
}

/** Total time per hour slot over all [intervals]. */
public fun sumBySlot(intervals: List<TimeInterval>, zone: ZoneId): Map<HourSlot, Long> =
    intervals
        .flatMap { splitByHour(it, zone) }
        .groupingBy { it.slot }
        .fold(0L) { total, share -> total + share.durationMs }
