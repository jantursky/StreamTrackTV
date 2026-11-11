package com.jantursky.streamtracktv.core

import com.jantursky.streamtracktv.core.insights.HistoryPeriod
import com.jantursky.streamtracktv.core.insights.PeriodTotals
import com.jantursky.streamtracktv.core.insights.PeriodUnit
import com.jantursky.streamtracktv.core.insights.QuickPeriod
import com.jantursky.streamtracktv.core.insights.buildHistory
import com.jantursky.streamtracktv.core.insights.compare
import com.jantursky.streamtracktv.core.insights.highlights
import com.jantursky.streamtracktv.core.insights.periodFor
import com.jantursky.streamtracktv.core.insights.previousPeriod
import com.jantursky.streamtracktv.core.insights.stepPeriod
import com.jantursky.streamtracktv.core.insights.totals
import com.jantursky.streamtracktv.core.model.BucketSet
import com.jantursky.streamtracktv.core.model.HourSlot
import com.jantursky.streamtracktv.core.model.ScreenBucket
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HistoryPeriodsTest {
    private val monday = DayOfWeek.MONDAY
    private val sunday = DayOfWeek.SUNDAY

    // Wednesday 2026-11-11
    private val today = LocalDate.parse("2026-11-11")

    private fun period(from: String, to: String) = HistoryPeriod(LocalDate.parse(from), LocalDate.parse(to))

    @Test
    fun `today and yesterday are single days`() {
        assertEquals(period("2026-11-11", "2026-11-11"), periodFor(QuickPeriod.TODAY, today, monday))
        assertEquals(period("2026-11-10", "2026-11-10"), periodFor(QuickPeriod.YESTERDAY, today, monday))
    }

    @Test
    fun `week starts on the first day of the week of the locale`() {
        assertEquals(period("2026-11-09", "2026-11-11"), periodFor(QuickPeriod.THIS_WEEK, today, monday))
        assertEquals(period("2026-11-08", "2026-11-11"), periodFor(QuickPeriod.THIS_WEEK, today, sunday))
        assertEquals(period("2026-11-02", "2026-11-08"), periodFor(QuickPeriod.LAST_WEEK, today, monday))
        assertEquals(period("2026-11-01", "2026-11-07"), periodFor(QuickPeriod.LAST_WEEK, today, sunday))
    }

    @Test
    fun `month in progress stops today and last month is whole`() {
        assertEquals(period("2026-11-01", "2026-11-11"), periodFor(QuickPeriod.THIS_MONTH, today, monday))
        assertEquals(period("2026-10-01", "2026-10-31"), periodFor(QuickPeriod.LAST_MONTH, today, monday))
    }

    @Test
    fun `last month can have 28 days and last month across a year boundary works`() {
        assertEquals(30 - 2, periodFor(QuickPeriod.LAST_MONTH, LocalDate.parse("2026-03-10"), monday).days)
        assertEquals(period("2025-12-01", "2025-12-31"), periodFor(QuickPeriod.LAST_MONTH, LocalDate.parse("2026-01-05"), monday))
        assertEquals(period("2025-12-29", "2026-01-04"), periodFor(QuickPeriod.LAST_WEEK, LocalDate.parse("2026-01-07"), monday))
    }

    @Test
    fun `stepping a week or month moves by one unit and never past today`() {
        val lastWeek = LocalDate.parse("2026-11-08")
        assertEquals(LocalDate.parse("2026-11-01"), stepPeriod(PeriodUnit.WEEK, lastWeek, -1, today))
        assertEquals(today, stepPeriod(PeriodUnit.WEEK, lastWeek, 1, today))
        assertEquals(today, stepPeriod(PeriodUnit.MONTH, LocalDate.parse("2026-10-31"), 1, today))
        assertEquals(LocalDate.parse("2026-09-30"), stepPeriod(PeriodUnit.MONTH, LocalDate.parse("2026-10-31"), -1, today))
        assertEquals(today, stepPeriod(PeriodUnit.DAY, today, 1, today))
        assertEquals(LocalDate.parse("2026-11-10"), stepPeriod(PeriodUnit.DAY, today, -1, today))
    }

    @Test
    fun `previous period of a week in progress covers the same weekdays`() {
        val thisWeek = periodFor(QuickPeriod.THIS_WEEK, today, monday)
        assertEquals(period("2026-11-02", "2026-11-04"), previousPeriod(thisWeek, PeriodUnit.WEEK))
    }

    @Test
    fun `previous period of rolling and day ranges is directly before`() {
        assertEquals(period("2026-11-04", "2026-11-10"), previousPeriod(period("2026-11-11", "2026-11-17"), null))
        assertEquals(period("2026-11-09", "2026-11-09"), previousPeriod(period("2026-11-10", "2026-11-10"), PeriodUnit.DAY))
    }

    @Test
    fun `previous period of months`() {
        assertEquals(period("2026-10-01", "2026-10-11"), previousPeriod(period("2026-11-01", "2026-11-11"), PeriodUnit.MONTH))
        assertEquals(period("2026-02-01", "2026-02-28"), previousPeriod(period("2026-03-01", "2026-03-31"), PeriodUnit.MONTH))
        assertEquals(period("2026-02-01", "2026-02-28"), previousPeriod(period("2026-03-01", "2026-03-30"), PeriodUnit.MONTH))
    }

    private fun totals(screen: Long, app: Long, keys: Long, data: Boolean = true) = PeriodTotals(screen, app, keys, data)

    @Test
    fun `comparison gives signed difference and percentage`() {
        val more = compare(totals(3 * HOUR, 0, 10), totals(2 * HOUR, 0, 20))
        assertNotNull(more)
        assertEquals(HOUR, more.screenOn.diff)
        assertEquals(50, more.screenOn.percent)
        assertEquals(-10, more.keyPresses.diff)
        assertEquals(-50, more.keyPresses.percent)
        assertEquals(0, compare(totals(1, 1, 1), totals(1, 1, 1))?.screenOn?.diff)
    }

    @Test
    fun `comparison is absent without earlier data and has no percentage for a zero earlier value`() {
        assertNull(compare(totals(1, 1, 1), totals(0, 0, 0, data = false)))
        val c = compare(totals(5, 0, 7), totals(5, 0, 0))
        assertEquals(7, c?.keyPresses?.diff)
        assertNull(c?.keyPresses?.percent)
    }

    private fun screen(day: String, hour: Int, ms: Long) = ScreenBucket(HourSlot(LocalDate.parse(day), hour), ms)

    @Test
    fun `highlights average only days with data and name busiest day and peak hour`() {
        val buckets =
            BucketSet(
                screen =
                    listOf(
                        screen("2026-11-09", 20, 2 * HOUR),
                        screen("2026-11-09", 21, HOUR),
                        screen("2026-11-10", 20, HOUR),
                        screen("2026-11-11", 8, HOUR),
                        screen("2026-11-12", 20, 3 * HOUR - 1),
                    ),
            )
        val model = buildHistory(period("2026-11-09", "2026-11-15"), buckets)
        val h = assertNotNull(highlights(model))
        assertEquals(LocalDate.parse("2026-11-09"), h.busiestDay)
        assertEquals(3 * HOUR, h.busiestDayMs)
        assertEquals(20, h.peakHour)
        assertEquals(model.screenOnMs / 4, h.dailyAverageMs)
    }

    @Test
    fun `highlights tie goes to the earlier day and single day gives peak hour only`() {
        val buckets = BucketSet(screen = listOf(screen("2026-11-09", 5, HOUR), screen("2026-11-10", 6, HOUR)))
        val week = assertNotNull(highlights(buildHistory(period("2026-11-09", "2026-11-15"), buckets)))
        assertEquals(LocalDate.parse("2026-11-09"), week.busiestDay)
        val day = assertNotNull(highlights(buildHistory(period("2026-11-10", "2026-11-10"), buckets)))
        assertNull(day.dailyAverageMs)
        assertNull(day.busiestDay)
        assertEquals(6, day.peakHour)
    }

    @Test
    fun `no screen time means no highlights and 24 hourly sums`() {
        val model = buildHistory(period("2026-11-09", "2026-11-09"), BucketSet())
        assertNull(highlights(model))
        assertEquals(24, model.screenByHourMs.size)
        assertEquals(false, model.totals().hasData)
        val busy = buildHistory(period("2026-11-09", "2026-11-09"), BucketSet(screen = listOf(screen("2026-11-09", 3, 5))))
        assertEquals(5, busy.screenByHourMs[3])
        assertEquals(5, busy.screenByHourMs.sum())
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
