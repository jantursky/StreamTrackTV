package com.jantursky.streamtracktv.ui

import com.jantursky.streamtracktv.core.insights.HistoryRange
import com.jantursky.streamtracktv.core.insights.QuickPeriod
import com.jantursky.streamtracktv.core.model.BucketSet
import com.jantursky.streamtracktv.core.model.HourSlot
import com.jantursky.streamtracktv.core.model.ScreenBucket
import com.jantursky.streamtracktv.ui.history.HistoryState
import com.jantursky.streamtracktv.ui.history.buildScreenModel
import com.jantursky.streamtracktv.ui.history.historyQueryStart
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryStateTest {
    private val today = LocalDate.parse("2026-11-08")

    @Test
    fun `starts on today in the day view with no filter`() {
        val state = HistoryState(today)
        assertEquals(HistoryRange.DAY, state.range)
        assertEquals(null, state.appFilter)
        assertTrue(state.atToday(today))
    }

    @Test
    fun `steps back and forward but never past today`() {
        val back = HistoryState(today).step(-1, today)
        assertEquals(today.minusDays(1), back.endDay)
        assertFalse(back.atToday(today))
        assertEquals(today, back.step(1, today).endDay)
        assertEquals(today, back.step(1, today).step(1, today).endDay)
    }

    @Test
    fun `jump to today and keep range and filter`() {
        val state = HistoryState(today.minusDays(5), HistoryRange.WEEK, "com.netflix.ninja").goToday(today)
        assertEquals(today, state.endDay)
        assertEquals(HistoryRange.WEEK, state.range)
        assertEquals("com.netflix.ninja", state.appFilter)
    }

    @Test
    fun `range and filter change independently and the filter clears`() {
        val state = HistoryState(today).withRange(HistoryRange.MONTH).withApp("a")
        assertEquals(HistoryRange.MONTH, state.range)
        assertEquals("a", state.appFilter)
        assertEquals(null, state.withApp(null).appFilter)
    }

    private val monday = DayOfWeek.MONDAY

    @Test
    fun `a quick period shows its days and is marked as selected`() {
        val state = HistoryState(today).withQuick(QuickPeriod.YESTERDAY, today, monday)
        assertEquals(today.minusDays(1), state.period(today, monday).from)
        assertEquals(QuickPeriod.YESTERDAY, state.quick(today, monday))
        assertEquals(null, HistoryState(today).quick(today, monday))
    }

    @Test
    fun `last week steps back by whole weeks and then forward again`() {
        val last = HistoryState(today).withQuick(QuickPeriod.LAST_WEEK, today, monday)
        assertEquals(LocalDate.parse("2026-10-26"), last.period(today, monday).from)
        val before = last.step(-1, today)
        assertEquals(LocalDate.parse("2026-10-19"), before.period(today, monday).from)
        assertEquals(null, before.quick(today, monday))
        assertEquals(QuickPeriod.LAST_WEEK, before.step(1, today).quick(today, monday))
    }

    @Test
    fun `next never leaves the month that holds today`() {
        val month = HistoryState(today).withQuick(QuickPeriod.THIS_MONTH, today, monday)
        assertEquals(month.period(today, monday), month.step(1, today).period(today, monday))
    }

    @Test
    fun `choosing a rolling range from a quick period ends on its last day`() {
        val state = HistoryState(today).withQuick(QuickPeriod.LAST_WEEK, today, monday).withRange(HistoryRange.WEEK, today, monday)
        assertEquals(null, state.unit)
        assertEquals(LocalDate.parse("2026-11-01"), state.endDay)
        assertEquals(HistoryRange.WEEK, state.range)
    }

    @Test
    fun `opening a day from a list shows that calendar day`() {
        val saturday = LocalDate.parse("2026-11-07")
        val state = HistoryState(today, HistoryRange.WEEK).openDay(saturday)
        assertEquals(saturday, state.period(today, monday).from)
        assertEquals(saturday, state.period(today, monday).to)
    }

    private fun screen(day: LocalDate, ms: Long) = ScreenBucket(HourSlot(day, 20), ms)

    @Test
    fun `yesterday reads a span that covers the day before and compares with it`() {
        val state = HistoryState(today).withQuick(QuickPeriod.YESTERDAY, today, monday)
        val yesterday = today.minusDays(1)
        assertEquals(yesterday.minusDays(1), historyQueryStart(state, today, monday))
        val buckets =
            BucketSet(
                screen =
                    listOf(
                        screen(yesterday, 3 * HOUR),
                        screen(yesterday.minusDays(1), 2 * HOUR),
                        screen(
                            today,
                            9 * HOUR,
                        ),
                    ),
            )
        val model = buildScreenModel(state, today, buckets, emptySet(), emptyMap(), monday)
        assertEquals(3 * HOUR, model.history.screenOnMs)
        assertEquals(HOUR, model.comparison?.screenOn?.diff)
        assertEquals(50, model.comparison?.screenOn?.percent)
    }

    @Test
    fun `last week reads two weeks and has no comparison when the week before is empty`() {
        val state = HistoryState(today).withQuick(QuickPeriod.LAST_WEEK, today, monday)
        assertEquals(LocalDate.parse("2026-10-19"), historyQueryStart(state, today, monday))
        val buckets = BucketSet(screen = listOf(screen(LocalDate.parse("2026-10-28"), HOUR)))
        val model = buildScreenModel(state, today, buckets, emptySet(), emptyMap(), monday)
        assertEquals(HOUR, model.history.screenOnMs)
        assertEquals(null, model.comparison)
        assertEquals(20, model.highlights?.peakHour)
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
