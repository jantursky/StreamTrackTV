package com.jantursky.streamtracktv.core

import com.jantursky.streamtracktv.core.insights.HistoryRange
import com.jantursky.streamtracktv.core.insights.buildHistory
import com.jantursky.streamtracktv.core.insights.rangeStart
import com.jantursky.streamtracktv.core.insights.stepDay
import com.jantursky.streamtracktv.core.model.BucketSet
import com.jantursky.streamtracktv.core.model.HourSlot
import com.jantursky.streamtracktv.core.model.KeyBucket
import com.jantursky.streamtracktv.core.model.KeyClass
import com.jantursky.streamtracktv.core.model.ScreenBucket
import com.jantursky.streamtracktv.core.model.UsageBucket
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistoryTest {
    private val today = LocalDate.parse("2026-11-08")
    private val yesterday = today.minusDays(1)
    private val netflix = "com.netflix.ninja"
    private val youtube = "com.google.android.youtube.tv"

    private val buckets =
        BucketSet(
            usage =
                listOf(
                    UsageBucket(HourSlot(today, 19), netflix, 40 * MINUTE),
                    UsageBucket(HourSlot(today, 20), youtube, 20 * MINUTE),
                    UsageBucket(HourSlot(yesterday, 21), netflix, 10 * MINUTE),
                ),
            keys =
                listOf(
                    KeyBucket(HourSlot(today, 19), KeyClass.DPAD, 30),
                    KeyBucket(HourSlot(yesterday, 21), KeyClass.SELECT, 5),
                ),
            screen =
                listOf(
                    ScreenBucket(HourSlot(today, 19), 50 * MINUTE),
                    ScreenBucket(HourSlot(yesterday, 21), 15 * MINUTE),
                ),
        )

    @Test
    fun `day view has an entry for each of the 24 hours`() {
        val history = buildHistory(today, HistoryRange.DAY, buckets)
        assertEquals(24, history.points.size)
        assertEquals(40 * MINUTE, history.points[19].foregroundMs)
        assertEquals(50 * MINUTE, history.points[19].screenOnMs)
        assertEquals(30, history.points[19].keyPresses)
        assertEquals(0, history.points[3].foregroundMs)
        assertEquals(60 * MINUTE, history.foregroundMs)
    }

    @Test
    fun `week view has one point per day and counts only days in range`() {
        val history = buildHistory(today, HistoryRange.WEEK, buckets)
        assertEquals(7, history.points.size)
        assertEquals(rangeStart(today, HistoryRange.WEEK), history.from)
        assertEquals(70 * MINUTE, history.foregroundMs)
        assertEquals(65 * MINUTE, history.screenOnMs)
        assertEquals(35, history.keyPresses)
        assertNull(history.points.first().hour)
    }

    @Test
    fun `previous day shows its own data`() {
        val history = buildHistory(yesterday, HistoryRange.DAY, buckets)
        assertEquals(10 * MINUTE, history.foregroundMs)
        assertEquals(mapOf(KeyClass.SELECT to 5L), history.keysByClass)
    }

    @Test
    fun `apps are ranked with their share`() {
        val history = buildHistory(today, HistoryRange.WEEK, buckets)
        assertEquals(listOf(netflix, youtube), history.apps.map { it.packageName })
        assertEquals(50.0 / 70.0, history.apps.first().share, 1e-9)
    }

    @Test
    fun `filter narrows foreground time but not screen or keys`() {
        val history = buildHistory(today, HistoryRange.DAY, buckets, appFilter = youtube)
        assertEquals(20 * MINUTE, history.foregroundMs)
        assertEquals(0, history.points[19].foregroundMs)
        assertEquals(50 * MINUTE, history.screenOnMs)
        assertEquals(30, history.keyPresses)
    }

    @Test
    fun `excluded apps never appear`() {
        val history = buildHistory(today, HistoryRange.WEEK, buckets, excluded = setOf(netflix))
        assertEquals(listOf(youtube), history.apps.map { it.packageName })
        assertEquals(20 * MINUTE, history.foregroundMs)
    }

    @Test
    fun `a day without data is empty`() {
        val history = buildHistory(today.minusDays(10), HistoryRange.DAY, buckets)
        assertTrue(history.isEmpty)
        assertTrue(history.apps.isEmpty())
        assertEquals(0, history.apps.size)
    }

    @Test
    fun `stepping never passes today`() {
        assertEquals(yesterday, stepDay(today, -1, today))
        assertEquals(today, stepDay(today, 1, today))
        assertEquals(today, stepDay(yesterday, 5, today))
    }
}
