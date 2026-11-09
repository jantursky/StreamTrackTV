package com.jantursky.streamtracktv.ui

import com.jantursky.streamtracktv.core.insights.HistoryRange
import com.jantursky.streamtracktv.ui.history.HistoryState
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
}
