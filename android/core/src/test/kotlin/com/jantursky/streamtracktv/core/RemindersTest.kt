package com.jantursky.streamtracktv.core

import com.jantursky.streamtracktv.core.insights.AppTime
import com.jantursky.streamtracktv.core.insights.BedtimeWindow
import com.jantursky.streamtracktv.core.insights.DaySummary
import com.jantursky.streamtracktv.core.insights.Goal
import com.jantursky.streamtracktv.core.insights.GoalTarget
import com.jantursky.streamtracktv.core.insights.Reminder
import com.jantursky.streamtracktv.core.insights.ReminderPlan
import com.jantursky.streamtracktv.core.insights.limitReminderKey
import com.jantursky.streamtracktv.core.insights.planReminders
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemindersTest {
    private val day = LocalDate.parse("2026-11-08")
    private val netflix = "com.netflix.ninja"
    private val screenLimit = Goal(GoalTarget.ScreenTime, 180 * MINUTE)
    private val appLimit = Goal(GoalTarget.App(netflix), 60 * MINUTE)

    private fun summary(screenMin: Long, appMin: Long = 0, on: LocalDate = day) =
        DaySummary(
            day = on,
            screenOnMs = screenMin * MINUTE,
            keyPresses = 0,
            keysByClass = emptyMap(),
            apps = listOf(AppTime(netflix, appMin * MINUTE)),
            screenOnByHour = emptyList(),
            activeWindows = emptyList(),
        )

    private fun plan(
        goals: List<Goal> = listOf(screenLimit, appLimit),
        bedtime: BedtimeWindow? = null,
        enabled: Boolean = true,
        day: DaySummary = summary(0),
        nowMs: Long = at("2026-11-08T15:00"),
        interactive: Boolean = true,
        shown: Set<String> = emptySet(),
        lastBedtimeMs: Long? = null,
    ): ReminderPlan =
        planReminders(goals, bedtime, enabled, day, nowMs, BRATISLAVA, interactive, shown, lastBedtimeMs)

    private fun limitsOf(p: ReminderPlan) = p.reminders.filterIsInstance<Reminder.Limit>()

    private fun limit(target: GoalTarget, threshold: Int, limitMin: Long) =
        limitReminderKey(day, target, threshold, limitMin * MINUTE)

    @Test
    fun `nothing below 80 percent`() {
        val p = plan(goals = listOf(screenLimit), day = summary(142)) // 78.9 %
        assertTrue(p.reminders.isEmpty())
        assertTrue(p.shown.isEmpty())
    }

    @Test
    fun `80 percent of the screen limit shows the near reminder once`() {
        val p = plan(goals = listOf(screenLimit), day = summary(144)) // 80 %
        assertEquals(listOf(Reminder.Limit(GoalTarget.ScreenTime, 80, 144 * MINUTE, 180 * MINUTE)), p.reminders)
        assertEquals(setOf(limit(GoalTarget.ScreenTime, 80, 180)), p.shown)
    }

    @Test
    fun `99 percent still shows only the 80 percent reminder`() {
        val p = plan(goals = listOf(screenLimit), day = summary(178)) // 98.9 %
        assertEquals(1, limitsOf(p).size)
        assertEquals(80, limitsOf(p).single().threshold)
    }

    @Test
    fun `100 percent shows the full reminder`() {
        val p = plan(goals = listOf(screenLimit), day = summary(180))
        assertEquals(listOf(Reminder.Limit(GoalTarget.ScreenTime, 100, 180 * MINUTE, 180 * MINUTE)), p.reminders)
        assertTrue(limit(GoalTarget.ScreenTime, 80, 180) in p.shown, "80 is recorded so it is not shown later")
    }

    @Test
    fun `140 percent shows one 100 percent reminder, not two`() {
        val p = plan(goals = listOf(screenLimit), day = summary(252))
        assertEquals(listOf(100), limitsOf(p).map { it.threshold })
        assertEquals(setOf(limit(GoalTarget.ScreenTime, 80, 180), limit(GoalTarget.ScreenTime, 100, 180)), p.shown)
    }

    @Test
    fun `app limit at 80 percent and at 100 percent`() {
        val near = plan(goals = listOf(appLimit), day = summary(0, appMin = 48))
        assertEquals(Reminder.Limit(GoalTarget.App(netflix), 80, 48 * MINUTE, 60 * MINUTE), near.reminders.single())
        val full = plan(goals = listOf(appLimit), day = summary(0, appMin = 61))
        assertEquals(100, limitsOf(full).single().threshold)
    }

    @Test
    fun `no repeat on a second call`() {
        val first = plan(goals = listOf(screenLimit), day = summary(180))
        val second = plan(goals = listOf(screenLimit), day = summary(181), shown = first.shown)
        assertTrue(second.reminders.isEmpty())
    }

    @Test
    fun `raising the limit re-arms the thresholds`() {
        val shown = setOf(limit(GoalTarget.ScreenTime, 80, 180))
        val raised = Goal(GoalTarget.ScreenTime, 190 * MINUTE)
        val p = plan(goals = listOf(raised), day = summary(152), shown = shown) // 80 % of 190
        assertEquals(80, limitsOf(p).single().threshold)
    }

    @Test
    fun `a new local day re-arms and drops yesterday's entries`() {
        val yesterday = "2026-11-07|screen|80|180"
        val p = plan(goals = listOf(screenLimit), day = summary(150), shown = setOf(yesterday))
        assertEquals(80, limitsOf(p).single().threshold)
        assertFalse(yesterday in p.shown)
    }

    @Test
    fun `old day entries are dropped even when nothing is shown`() {
        val old = "2026-11-07|screen|100|180"
        val p = plan(goals = listOf(screenLimit), day = summary(10), shown = setOf(old))
        assertTrue(p.reminders.isEmpty())
        assertTrue(p.shown.isEmpty())
    }

    @Test
    fun `reminders off returns nothing and keeps the limits`() {
        val p = plan(goals = listOf(screenLimit), day = summary(200), enabled = false, bedtime = BedtimeWindow(0, 1))
        assertTrue(p.reminders.isEmpty())
        assertTrue(p.shown.isEmpty())
    }

    @Test
    fun `screen not interactive returns nothing`() {
        val p = plan(goals = listOf(screenLimit), day = summary(200), interactive = false)
        assertTrue(p.reminders.isEmpty())
        assertTrue(p.shown.isEmpty())
    }

    @Test
    fun `bedtime window across midnight`() {
        val window = BedtimeWindow(22 * 60, 7 * 60)
        assertTrue(window.contains(23 * 60 + 30))
        assertTrue(window.contains(22 * 60))
        assertTrue(window.contains(6 * 60 + 59))
        assertFalse(window.contains(7 * 60))
        assertFalse(window.contains(12 * 60))
        assertFalse(BedtimeWindow(9 * 60, 9 * 60).contains(9 * 60), "equal bounds mean no window")
    }

    @Test
    fun `bedtime reminder inside and outside the window`() {
        val window = BedtimeWindow(22 * 60, 7 * 60)
        val inside = plan(bedtime = window, nowMs = at("2026-11-08T23:30"))
        assertEquals(listOf<Reminder>(Reminder.Bedtime), inside.reminders)
        assertEquals(at("2026-11-08T23:30"), inside.lastBedtimeMs)
        val outside = plan(bedtime = window, nowMs = at("2026-11-08T12:00"))
        assertTrue(outside.reminders.isEmpty())
    }

    @Test
    fun `bedtime reminder is spaced by 30 minutes`() {
        val window = BedtimeWindow(22 * 60, 7 * 60)
        val first = plan(bedtime = window, nowMs = at("2026-11-08T22:05"))
        assertEquals(listOf<Reminder>(Reminder.Bedtime), first.reminders)
        val tooSoon = plan(bedtime = window, nowMs = at("2026-11-08T22:34"), lastBedtimeMs = first.lastBedtimeMs)
        assertTrue(tooSoon.reminders.isEmpty())
        assertEquals(first.lastBedtimeMs, tooSoon.lastBedtimeMs)
        val again = plan(bedtime = window, nowMs = at("2026-11-08T22:35"), lastBedtimeMs = first.lastBedtimeMs)
        assertEquals(listOf<Reminder>(Reminder.Bedtime), again.reminders)
    }

    @Test
    fun `bedtime spacing counts real time across a DST change`() {
        val window = BedtimeWindow(1 * 60, 4 * 60)
        // Europe/Bratislava springs forward on 2026-03-29 at 02:00: 01:50 CET is 00:50 UTC,
        // 03:10 CEST is 01:10 UTC, only 20 minutes later.
        val first = plan(bedtime = window, nowMs = at("2026-03-29T01:50", BRATISLAVA), day = summary(0, on = LocalDate.parse("2026-03-29")))
        assertEquals(listOf<Reminder>(Reminder.Bedtime), first.reminders)
        val early =
            planReminders(
                emptyList(),
                window,
                true,
                DaySummary(LocalDate.parse("2026-03-29"), 0, 0, emptyMap(), emptyList(), emptyList(), emptyList()),
                at("2026-03-29T03:10", BRATISLAVA),
                BRATISLAVA,
                true,
                emptySet(),
                first.lastBedtimeMs,
            )
        assertTrue(early.reminders.isEmpty())
        val late =
            planReminders(
                emptyList(),
                window,
                true,
                DaySummary(LocalDate.parse("2026-03-29"), 0, 0, emptyMap(), emptyList(), emptyList(), emptyList()),
                at("2026-03-29T03:25", BRATISLAVA),
                BRATISLAVA,
                true,
                emptySet(),
                first.lastBedtimeMs,
            )
        assertEquals(listOf<Reminder>(Reminder.Bedtime), late.reminders)
    }

    @Test
    fun `cleared window shows nothing and forgets the last bedtime reminder`() {
        val p = plan(bedtime = null, nowMs = at("2026-11-08T23:00"), lastBedtimeMs = at("2026-11-08T22:05"))
        assertTrue(p.reminders.isEmpty())
        assertNull(p.lastBedtimeMs)
    }

    @Test
    fun `bedtime is silent with the screen off or reminders off`() {
        val window = BedtimeWindow(22 * 60, 7 * 60)
        assertTrue(plan(bedtime = window, nowMs = at("2026-11-08T23:00"), interactive = false).reminders.isEmpty())
        assertTrue(plan(bedtime = window, nowMs = at("2026-11-08T23:00"), enabled = false).reminders.isEmpty())
    }
}
