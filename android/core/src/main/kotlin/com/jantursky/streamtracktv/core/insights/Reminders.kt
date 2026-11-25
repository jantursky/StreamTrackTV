package com.jantursky.streamtracktv.core.insights

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Bedtime window as minutes since local midnight. It may cross midnight; equal bounds mean no window. */
public data class BedtimeWindow(val startMinute: Int, val endMinute: Int) {
    init {
        require(startMinute in 0 until MINUTES_PER_DAY && endMinute in 0 until MINUTES_PER_DAY) {
            "Bedtime minutes must be within one day"
        }
    }

    public fun contains(minuteOfDay: Int): Boolean =
        when {
            startMinute == endMinute -> false
            startMinute < endMinute -> minuteOfDay in startMinute until endMinute
            else -> minuteOfDay >= startMinute || minuteOfDay < endMinute
        }
}

public sealed interface Reminder {
    /** A daily limit reached [threshold] percent; [usedMs] and [limitMs] are for the day. */
    public data class Limit(val target: GoalTarget, val threshold: Int, val usedMs: Long, val limitMs: Long) : Reminder

    public data object Bedtime : Reminder
}

/** What to show now, and the record to store for the next check. */
public data class ReminderPlan(
    val reminders: List<Reminder>,
    /** Keys of the day already shown, see [limitReminderKey]. Entries of other days are dropped. */
    val shown: Set<String>,
    val lastBedtimeMs: Long?,
)

/** Bedtime reminders are at least this far apart while the screen stays on. */
public const val BEDTIME_REMINDER_GAP_MS: Long = 30 * 60_000L

private const val MINUTES_PER_DAY = 24 * 60
private const val SCREEN_TARGET = "screen"
private const val APP_TARGET_PREFIX = "app:"

/** Record key for one threshold of one limit on [day]; the limit value re-arms the threshold when it changes. */
public fun limitReminderKey(day: LocalDate, target: GoalTarget, threshold: Int, limitMs: Long): String {
    val targetKey =
        when (target) {
            GoalTarget.ScreenTime -> SCREEN_TARGET
            is GoalTarget.App -> APP_TARGET_PREFIX + target.packageName
        }
    return "$day|$targetKey|$threshold|${limitMs / 60_000L}"
}

/**
 * Decides which reminders to show at [nowMs] given today's [day] summary.
 *
 * Pure: no clock, no storage, no drawing. Nothing is returned while reminders are off or the screen is not
 * interactive. Each limit reports 80% and 100% once per day and limit value; passing 100% at once shows only
 * the 100% reminder. The bedtime reminder shows on the first check inside the window and then at most every
 * [BEDTIME_REMINDER_GAP_MS] while the screen stays on.
 */
public fun planReminders(
    goals: List<Goal>,
    bedtime: BedtimeWindow?,
    enabled: Boolean,
    day: DaySummary,
    nowMs: Long,
    zone: ZoneId,
    interactive: Boolean,
    shown: Set<String>,
    lastBedtimeMs: Long?,
): ReminderPlan {
    val today = day.day.toString()
    val kept = shown.filterTo(mutableSetOf()) { it.startsWith("$today|") }
    if (!enabled || !interactive) return ReminderPlan(emptyList(), kept, lastBedtimeMs)

    val reminders = mutableListOf<Reminder>()
    for (status in evaluateGoals(goals, day)) {
        val target = status.goal.target
        val limitMs = status.goal.dailyLimitMs

        fun key(threshold: Int) = limitReminderKey(day.day, target, threshold, limitMs)
        when (status.state) {
            GoalState.ON_TRACK -> {
                Unit
            }

            GoalState.NEAR_LIMIT -> {
                if (kept.add(key(NEAR_THRESHOLD))) {
                    reminders += Reminder.Limit(target, NEAR_THRESHOLD, status.usedMs, limitMs)
                }
            }

            GoalState.EXCEEDED -> {
                // Passing 100% directly: the 80% step is recorded as seen, but only the 100% reminder is shown.
                kept.add(key(NEAR_THRESHOLD))
                if (kept.add(key(FULL_THRESHOLD))) {
                    reminders += Reminder.Limit(target, FULL_THRESHOLD, status.usedMs, limitMs)
                }
            }
        }
    }

    var nextBedtime = lastBedtimeMs
    if (bedtime == null) {
        nextBedtime = null
    } else if (bedtime.contains(minuteOfDay(nowMs, zone))) {
        val last = lastBedtimeMs
        if (last == null || nowMs - last >= BEDTIME_REMINDER_GAP_MS) {
            reminders += Reminder.Bedtime
            nextBedtime = nowMs
        }
    }
    return ReminderPlan(reminders, kept, nextBedtime)
}

private fun minuteOfDay(nowMs: Long, zone: ZoneId): Int {
    val local = Instant.ofEpochMilli(nowMs).atZone(zone)
    return local.hour * 60 + local.minute
}

private const val NEAR_THRESHOLD = 80
private const val FULL_THRESHOLD = 100
