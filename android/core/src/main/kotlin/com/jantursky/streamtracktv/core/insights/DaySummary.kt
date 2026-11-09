package com.jantursky.streamtracktv.core.insights

import com.jantursky.streamtracktv.core.model.BucketSet
import com.jantursky.streamtracktv.core.model.KeyClass
import java.time.LocalDate

public data class AppTime(val packageName: String, val foregroundMs: Long)

/** Hours `[startHour, endHour)` of one day. */
public data class HourRange(val startHour: Int, val endHour: Int) {
    val hours: Int get() = endHour - startHour
}

public data class DaySummary(
    val day: LocalDate,
    val screenOnMs: Long,
    val keyPresses: Long,
    val keysByClass: Map<KeyClass, Long>,
    val apps: List<AppTime>,
    val screenOnByHour: List<Long>,
    val activeWindows: List<HourRange>,
)

/** Hours with less screen-on time than this do not count as active. */
public const val ACTIVE_HOUR_MIN_MS: Long = 5 * 60_000L

/** Totals for [day] from the buckets in [buckets]; buckets of other days are ignored. */
public fun summarizeDay(day: LocalDate, buckets: BucketSet): DaySummary {
    val usage = buckets.usage.filter { it.slot.day == day }
    val keys = buckets.keys.filter { it.slot.day == day }
    val screen = buckets.screen.filter { it.slot.day == day }

    val byHour = LongArray(HOURS_PER_DAY)
    screen.forEach { byHour[it.slot.hour] += it.screenOnMs }

    return DaySummary(
        day = day,
        screenOnMs = byHour.sum(),
        keyPresses = keys.sumOf { it.count },
        keysByClass = keys.groupingBy { it.keyClass }.fold(0L) { total, bucket -> total + bucket.count },
        apps =
            usage
                .groupingBy { it.packageName }
                .fold(0L) { total, bucket -> total + bucket.foregroundMs }
                .map { (pkg, ms) -> AppTime(pkg, ms) }
                .sortedWith(compareByDescending<AppTime> { it.foregroundMs }.thenBy { it.packageName }),
        screenOnByHour = byHour.toList(),
        activeWindows = activeWindows(byHour.toList()),
    )
}

/** Consecutive hours with at least [ACTIVE_HOUR_MIN_MS] of screen-on time, merged into ranges. */
public fun activeWindows(screenOnByHour: List<Long>): List<HourRange> {
    val windows = mutableListOf<HourRange>()
    var start: Int? = null
    screenOnByHour.forEachIndexed { hour, ms ->
        val active = ms >= ACTIVE_HOUR_MIN_MS
        val open = start
        if (active && open == null) start = hour
        if (!active && open != null) {
            windows += HourRange(open, hour)
            start = null
        }
    }
    start?.let { windows += HourRange(it, screenOnByHour.size) }
    return windows
}

internal const val HOURS_PER_DAY = 24
