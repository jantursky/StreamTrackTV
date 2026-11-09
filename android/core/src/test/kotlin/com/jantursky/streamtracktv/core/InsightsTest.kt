package com.jantursky.streamtracktv.core

import com.jantursky.streamtracktv.core.insights.AppTime
import com.jantursky.streamtracktv.core.insights.BingeSession
import com.jantursky.streamtracktv.core.insights.Goal
import com.jantursky.streamtracktv.core.insights.GoalState
import com.jantursky.streamtracktv.core.insights.GoalTarget
import com.jantursky.streamtracktv.core.insights.HourRange
import com.jantursky.streamtracktv.core.insights.IdleScreenWindow
import com.jantursky.streamtracktv.core.insights.activeWindows
import com.jantursky.streamtracktv.core.insights.detectBinges
import com.jantursky.streamtracktv.core.insights.detectIdleScreen
import com.jantursky.streamtracktv.core.insights.evaluateGoals
import com.jantursky.streamtracktv.core.insights.summarizeDay
import com.jantursky.streamtracktv.core.model.BucketSet
import com.jantursky.streamtracktv.core.model.HourSlot
import com.jantursky.streamtracktv.core.model.KeyBucket
import com.jantursky.streamtracktv.core.model.KeyClass
import com.jantursky.streamtracktv.core.model.ScreenBucket
import com.jantursky.streamtracktv.core.model.UsageBucket
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InsightsTest {
    private val day = LocalDate.parse("2026-11-08")
    private val netflix = "com.netflix.ninja"
    private val youtube = "com.google.android.youtube.tv"

    private fun slot(hour: Int, on: LocalDate = day) = HourSlot(on, hour)

    private val buckets =
        BucketSet(
            usage =
                listOf(
                    UsageBucket(slot(19), netflix, 40 * MINUTE),
                    UsageBucket(slot(20), netflix, 30 * MINUTE),
                    UsageBucket(slot(20), youtube, 20 * MINUTE),
                    UsageBucket(slot(20, day.minusDays(1)), youtube, 50 * MINUTE),
                ),
            keys =
                listOf(
                    KeyBucket(slot(19), KeyClass.DPAD, 30),
                    KeyBucket(slot(20), KeyClass.DPAD, 10),
                    KeyBucket(slot(20), KeyClass.BACK, 2),
                ),
            screen =
                listOf(
                    ScreenBucket(slot(8), 2 * MINUTE),
                    ScreenBucket(slot(19), 45 * MINUTE),
                    ScreenBucket(slot(20), 55 * MINUTE),
                    ScreenBucket(slot(23), 10 * MINUTE),
                ),
        )

    @Test
    fun `summarizes one day and ignores other days`() {
        val summary = summarizeDay(day, buckets)
        assertEquals(112 * MINUTE, summary.screenOnMs)
        assertEquals(42, summary.keyPresses)
        assertEquals(mapOf(KeyClass.DPAD to 40L, KeyClass.BACK to 2L), summary.keysByClass)
        assertEquals(listOf(AppTime(netflix, 70 * MINUTE), AppTime(youtube, 20 * MINUTE)), summary.apps)
        assertEquals(24, summary.screenOnByHour.size)
        assertEquals(listOf(HourRange(19, 21), HourRange(23, 24)), summary.activeWindows)
    }

    @Test
    fun `active windows skip short hours and close at the end of the day`() {
        val hours = List(24) { if (it in 0..1 || it == 23) 10 * MINUTE else 0L }
        assertEquals(listOf(HourRange(0, 2), HourRange(23, 24)), activeWindows(hours))
        assertEquals(2, HourRange(0, 2).hours)
    }

    @Test
    fun `goals report progress and state`() {
        val summary = summarizeDay(day, buckets)
        val statuses =
            evaluateGoals(
                listOf(
                    Goal(GoalTarget.ScreenTime, 3 * HOUR),
                    Goal(GoalTarget.App(netflix), HOUR),
                    Goal(GoalTarget.App(youtube), HOUR),
                    Goal(GoalTarget.App("com.unused.app"), HOUR),
                ),
                summary,
            )
        assertEquals(
            listOf(GoalState.ON_TRACK, GoalState.EXCEEDED, GoalState.ON_TRACK, GoalState.ON_TRACK),
            statuses.map { it.state },
        )
        assertEquals(0L, statuses[1].remainingMs)
        assertEquals(40 * MINUTE, statuses[2].remainingMs)
        assertEquals(0L, statuses[3].usedMs)
        val near = evaluateGoals(listOf(Goal(GoalTarget.ScreenTime, 130 * MINUTE)), summary).single()
        assertEquals(GoalState.NEAR_LIMIT, near.state)
        assertTrue(near.progress in 0.8..1.0)
        assertFailsWith<IllegalArgumentException> { Goal(GoalTarget.ScreenTime, 0) }
    }

    @Test
    fun `detects a binge across midnight and ignores short or thin runs`() {
        val saturday = LocalDate.parse("2026-11-07")
        val usage =
            listOf(
                UsageBucket(slot(22, saturday), netflix, 50 * MINUTE),
                UsageBucket(slot(23, saturday), netflix, 60 * MINUTE),
                UsageBucket(slot(0), netflix, 46 * MINUTE),
                UsageBucket(slot(1), netflix, 20 * MINUTE),
                UsageBucket(slot(10), youtube, 60 * MINUTE),
                UsageBucket(slot(11), youtube, 60 * MINUTE),
            )
        assertEquals(listOf(BingeSession(netflix, slot(22, saturday), 3, 156 * MINUTE)), detectBinges(usage))
        assertEquals(2, detectBinges(usage, minHours = 2).size)
    }

    @Test
    fun `detects a screen left on without key presses`() {
        val screen = (19..23).map { ScreenBucket(slot(it), 60 * MINUTE) }
        val keys = listOf(KeyBucket(slot(19), KeyClass.DPAD, 3), KeyBucket(slot(20), KeyClass.BACK, 0))
        assertEquals(listOf(IdleScreenWindow(slot(20), 4, 4 * HOUR)), detectIdleScreen(screen, keys))
        assertTrue(detectIdleScreen(screen, (19..23).map { KeyBucket(slot(it), KeyClass.DPAD, 1) }).isEmpty())
    }
}
