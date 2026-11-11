package com.jantursky.streamtracktv.core.insights

import com.jantursky.streamtracktv.core.model.BucketSet
import com.jantursky.streamtracktv.core.model.KeyClass
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

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
    /** Screen-on time summed per hour of the day over the whole period, 24 values. Not narrowed by the app filter. */
    val screenByHourMs: List<Long> = List(HOURS_PER_DAY) { 0L },
)

/** A run of whole days, [from] to [to] inclusive. */
public data class HistoryPeriod(val from: LocalDate, val to: LocalDate) {
    val days: Int get() = ChronoUnit.DAYS.between(from, to).toInt() + 1
}

/** The unit of a calendar period: a day, a calendar week or a calendar month. */
public enum class PeriodUnit { DAY, WEEK, MONTH }

/** The named periods History offers as one button. */
public enum class QuickPeriod(public val unit: PeriodUnit) {
    TODAY(PeriodUnit.DAY),
    YESTERDAY(PeriodUnit.DAY),
    THIS_WEEK(PeriodUnit.WEEK),
    LAST_WEEK(PeriodUnit.WEEK),
    THIS_MONTH(PeriodUnit.MONTH),
    LAST_MONTH(PeriodUnit.MONTH),
}

private const val DAYS_PER_WEEK = 7L

/** A day that lies inside [quick], used as the anchor of a calendar view. */
public fun anchorFor(quick: QuickPeriod, today: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate =
    when (quick) {
        QuickPeriod.TODAY, QuickPeriod.THIS_WEEK, QuickPeriod.THIS_MONTH -> today
        QuickPeriod.YESTERDAY -> today.minusDays(1)
        QuickPeriod.LAST_WEEK -> weekStart(today, firstDayOfWeek).minusDays(1)
        QuickPeriod.LAST_MONTH -> today.withDayOfMonth(1).minusDays(1)
    }

/** The whole days of [quick]; a period that has not ended stops at [today]. */
public fun periodFor(quick: QuickPeriod, today: LocalDate, firstDayOfWeek: DayOfWeek): HistoryPeriod =
    calendarPeriod(quick.unit, anchorFor(quick, today, firstDayOfWeek), today, firstDayOfWeek)

/** The calendar [unit] that contains [anchor], cut off at [today]. */
public fun calendarPeriod(unit: PeriodUnit, anchor: LocalDate, today: LocalDate, firstDayOfWeek: DayOfWeek): HistoryPeriod {
    val (from, to) =
        when (unit) {
            PeriodUnit.DAY -> anchor to anchor
            PeriodUnit.WEEK -> weekStart(anchor, firstDayOfWeek).let { it to it.plusDays(DAYS_PER_WEEK - 1) }
            PeriodUnit.MONTH -> anchor.withDayOfMonth(1) to anchor.with(TemporalAdjusters.lastDayOfMonth())
        }
    return HistoryPeriod(from, to.coerceAtMost(today))
}

private fun weekStart(day: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate = day.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))

/** [anchor] moved by one [unit] in the direction of [direction] (negative is back), never past [today]. */
public fun stepPeriod(unit: PeriodUnit, anchor: LocalDate, direction: Int, today: LocalDate): LocalDate {
    val sign = if (direction < 0) -1L else 1L
    val moved =
        when (unit) {
            PeriodUnit.DAY -> anchor.plusDays(sign)
            PeriodUnit.WEEK -> anchor.plusDays(sign * DAYS_PER_WEEK)
            PeriodUnit.MONTH -> anchor.plusMonths(sign)
        }
    return moved.coerceAtMost(today)
}

/**
 * The period [period] is compared with. A rolling period ([unit] null) is compared with the same number of
 * days directly before it. A calendar period is compared with the same days of the unit before it, so a week
 * in progress meets the same weekdays of the week before.
 */
public fun previousPeriod(period: HistoryPeriod, unit: PeriodUnit?): HistoryPeriod =
    when (unit) {
        null, PeriodUnit.DAY -> {
            HistoryPeriod(period.from.minusDays(period.days.toLong()), period.from.minusDays(1))
        }

        PeriodUnit.WEEK -> {
            val from = period.from.minusDays(DAYS_PER_WEEK)
            HistoryPeriod(from, from.plusDays(period.days - 1L))
        }

        PeriodUnit.MONTH -> {
            val from = period.from.minusMonths(1)
            val end = from.with(TemporalAdjusters.lastDayOfMonth())
            val complete = period.to == period.to.with(TemporalAdjusters.lastDayOfMonth())
            HistoryPeriod(from, if (complete) end else from.plusDays(period.days - 1L).coerceAtMost(end))
        }
    }

/** The three totals of a period and whether it holds any data. */
public data class PeriodTotals(val screenOnMs: Long, val foregroundMs: Long, val keyPresses: Long, val hasData: Boolean)

public fun HistoryModel.totals(): PeriodTotals = PeriodTotals(screenOnMs, foregroundMs, keyPresses, !isEmpty)

/** Change of one total: [diff] is current minus earlier; [percent] is null when the earlier value was zero. */
public data class Delta(val diff: Long, val percent: Int?)

public data class Comparison(val screenOn: Delta, val foreground: Delta, val keyPresses: Delta)

/** How [current] differs from [previous]; null when [previous] holds no data. */
public fun compare(current: PeriodTotals, previous: PeriodTotals): Comparison? =
    if (!previous.hasData) {
        null
    } else {
        Comparison(
            delta(current.screenOnMs, previous.screenOnMs),
            delta(current.foregroundMs, previous.foregroundMs),
            delta(current.keyPresses, previous.keyPresses),
        )
    }

private fun delta(current: Long, earlier: Long): Delta =
    Delta(current - earlier, if (earlier == 0L) null else Math.round((current - earlier) * PERCENT_BASE / earlier.toDouble()).toInt())

private const val PERCENT_BASE = 100.0

/**
 * What stands out in a period. [dailyAverageMs] and [busiestDay] are null for a single day, where only the
 * [peakHour] is of use.
 */
public data class Highlights(
    val dailyAverageMs: Long?,
    val busiestDay: LocalDate?,
    val busiestDayMs: Long,
    val peakHour: Int,
)

/** Highlights of [model]; null when it holds no screen time. The earlier of two equally busy days wins. */
public fun highlights(model: HistoryModel): Highlights? {
    val peakHour = model.screenByHourMs.indices.maxByOrNull { model.screenByHourMs[it] }
    if (model.screenOnMs <= 0 || peakHour == null) return null
    if (model.points.firstOrNull()?.hour != null) return Highlights(null, null, 0, peakHour)
    val active = model.points.filter { it.screenOnMs > 0 }
    val busiest = active.maxByOrNull { it.screenOnMs }
    return Highlights(model.screenOnMs / active.size, busiest?.day, busiest?.screenOnMs ?: 0, peakHour)
}

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
): HistoryModel = buildHistory(HistoryPeriod(rangeStart(end, range), end), buckets, excluded, appFilter)

/** The same for any [period]; one day gives the hourly view, more days give one point per day. */
public fun buildHistory(
    period: HistoryPeriod,
    buckets: BucketSet,
    excluded: Set<String> = emptySet(),
    appFilter: String? = null,
): HistoryModel {
    val from = period.from
    val end = period.to
    val range =
        when {
            period.days == 1 -> HistoryRange.DAY
            period.days <= HistoryRange.WEEK.days -> HistoryRange.WEEK
            else -> HistoryRange.MONTH
        }
    val inRange = { day: LocalDate -> day in from..end }
    val usage = buckets.usage.filter { inRange(it.slot.day) && it.packageName !in excluded }
    val keys = buckets.keys.filter { inRange(it.slot.day) }
    val screen = buckets.screen.filter { inRange(it.slot.day) }
    val filtered = if (appFilter == null) usage else usage.filter { it.packageName == appFilter }

    val points =
        if (period.days == 1) {
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
        screenByHourMs = (0 until HOURS_PER_DAY).map { hour -> screen.filter { it.slot.hour == hour }.sumOf { it.screenOnMs } },
    )
}
