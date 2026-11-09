package com.jantursky.streamtracktv.core.insights

import com.jantursky.streamtracktv.core.model.BucketSet
import com.jantursky.streamtracktv.core.model.KeyClass
import java.time.LocalDate

/** How many days a history view covers, ending on the chosen day. */
public enum class HistoryRange(public val days: Int) {
    DAY(DAYS_IN_DAY_RANGE),
    WEEK(DAYS_IN_WEEK_RANGE),
    MONTH(DAYS_IN_MONTH_RANGE),
}

private const val DAYS_IN_DAY_RANGE = 1
private const val DAYS_IN_WEEK_RANGE = 7
private const val DAYS_IN_MONTH_RANGE = 30

/** One bar of the timeline: an hour in the day view, a day in the longer views. */
public data class HistoryPoint(
    val day: LocalDate,
    /** Hour of the day for the day view, null for a whole-day point. */
    val hour: Int?,
    val screenOnMs: Long,
    val foregroundMs: Long,
    val keyPresses: Long,
)

/** An app's foreground time in the range and its share of all listed apps. */
public data class AppShare(val packageName: String, val foregroundMs: Long, val share: Double)

public data class HistoryModel(
    val from: LocalDate,
    val to: LocalDate,
    val range: HistoryRange,
    val appFilter: String?,
    val points: List<HistoryPoint>,
    val apps: List<AppShare>,
    val keysByClass: Map<KeyClass, Long>,
    val screenOnMs: Long,
    val foregroundMs: Long,
    val keyPresses: Long,
    val isEmpty: Boolean,
)

/** First day of a [range] that ends on [end]. */
public fun rangeStart(end: LocalDate, range: HistoryRange): LocalDate = end.minusDays(range.days - 1L)

/** [day] moved by [steps] days, never past [today]. */
public fun stepDay(day: LocalDate, steps: Int, today: LocalDate): LocalDate = day.plusDays(steps.toLong()).coerceAtMost(today)

/**
 * The history of the days ending on [end], from the stored [buckets]. [excluded] apps are left out
 * everywhere. [appFilter] narrows foreground time to one app; screen-on time and key presses are not per
 * app, so they stay as they are.
 */
public fun buildHistory(
    end: LocalDate,
    range: HistoryRange,
    buckets: BucketSet,
    excluded: Set<String> = emptySet(),
    appFilter: String? = null,
): HistoryModel {
    val from = rangeStart(end, range)
    val inRange = { day: LocalDate -> day in from..end }
    val usage = buckets.usage.filter { inRange(it.slot.day) && it.packageName !in excluded }
    val keys = buckets.keys.filter { inRange(it.slot.day) }
    val screen = buckets.screen.filter { inRange(it.slot.day) }
    val filtered = if (appFilter == null) usage else usage.filter { it.packageName == appFilter }

    val points =
        if (range == HistoryRange.DAY) {
            (0 until HOURS_PER_DAY).map { hour ->
                HistoryPoint(
                    day = end,
                    hour = hour,
                    screenOnMs = screen.filter { it.slot.hour == hour }.sumOf { it.screenOnMs },
                    foregroundMs = filtered.filter { it.slot.hour == hour }.sumOf { it.foregroundMs },
                    keyPresses = keys.filter { it.slot.hour == hour }.sumOf { it.count },
                )
            }
        } else {
            generateSequence(from) { it.plusDays(1) }
                .takeWhile { it <= end }
                .map { day ->
                    HistoryPoint(
                        day = day,
                        hour = null,
                        screenOnMs = screen.filter { it.slot.day == day }.sumOf { it.screenOnMs },
                        foregroundMs = filtered.filter { it.slot.day == day }.sumOf { it.foregroundMs },
                        keyPresses = keys.filter { it.slot.day == day }.sumOf { it.count },
                    )
                }.toList()
        }

    val appTotals = usage.groupingBy { it.packageName }.fold(0L) { total, bucket -> total + bucket.foregroundMs }
    val appSum = appTotals.values.sum()
    val apps =
        appTotals
            .map { (pkg, ms) -> AppShare(pkg, ms, if (appSum == 0L) 0.0 else ms.toDouble() / appSum) }
            .sortedWith(compareByDescending<AppShare> { it.foregroundMs }.thenBy { it.packageName })

    return HistoryModel(
        from = from,
        to = end,
        range = range,
        appFilter = appFilter,
        points = points,
        apps = apps,
        keysByClass = keys.groupingBy { it.keyClass }.fold(0L) { total, bucket -> total + bucket.count },
        screenOnMs = screen.sumOf { it.screenOnMs },
        foregroundMs = filtered.sumOf { it.foregroundMs },
        keyPresses = keys.sumOf { it.count },
        isEmpty = usage.isEmpty() && keys.isEmpty() && screen.isEmpty(),
    )
}
