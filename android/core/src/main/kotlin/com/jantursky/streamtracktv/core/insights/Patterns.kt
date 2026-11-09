package com.jantursky.streamtracktv.core.insights

import com.jantursky.streamtracktv.core.model.HourSlot
import com.jantursky.streamtracktv.core.model.KeyBucket
import com.jantursky.streamtracktv.core.model.ScreenBucket
import com.jantursky.streamtracktv.core.model.UsageBucket

/** A run of consecutive, almost fully used hours of one app. */
public data class BingeSession(val packageName: String, val start: HourSlot, val hours: Int, val foregroundMs: Long)

/** A run of consecutive hours with the screen on and no key pressed: the TV was probably left on. */
public data class IdleScreenWindow(val start: HourSlot, val hours: Int, val screenOnMs: Long)

/** An hour counts towards a pattern from this much time on. */
public const val BUSY_HOUR_MIN_MS: Long = 45 * 60_000L

public const val BINGE_MIN_HOURS: Int = 3

/** Long enough that a feature film watched without touching the remote rarely qualifies. */
public const val IDLE_MIN_HOURS: Int = 3

/** Runs of at least [minHours] consecutive hours where one app had [BUSY_HOUR_MIN_MS] or more. */
public fun detectBinges(usage: List<UsageBucket>, minHours: Int = BINGE_MIN_HOURS): List<BingeSession> =
    usage
        .filter { it.foregroundMs >= BUSY_HOUR_MIN_MS }
        .groupBy { it.packageName }
        .flatMap { (pkg, buckets) ->
            consecutiveRuns(buckets.associate { it.slot to it.foregroundMs })
                .filter { it.size >= minHours }
                .map { run -> BingeSession(pkg, run.first().first, run.size, run.sumOf { it.second }) }
        }.sortedWith(compareBy({ it.start }, { it.packageName }))

/** Runs of at least [minHours] hours with [BUSY_HOUR_MIN_MS] of screen-on time and no key press. */
public fun detectIdleScreen(
    screen: List<ScreenBucket>,
    keys: List<KeyBucket>,
    minHours: Int = IDLE_MIN_HOURS,
): List<IdleScreenWindow> {
    val pressedSlots = keys.filter { it.count > 0 }.map { it.slot }.toSet()
    val idle =
        screen
            .filter { it.screenOnMs >= BUSY_HOUR_MIN_MS && it.slot !in pressedSlots }
            .groupingBy { it.slot }
            .fold(0L) { total, bucket -> total + bucket.screenOnMs }
    return consecutiveRuns(idle)
        .filter { it.size >= minHours }
        .map { run -> IdleScreenWindow(run.first().first, run.size, run.sumOf { it.second }) }
}

/** Groups slot values into runs of directly consecutive hours, in time order. */
private fun consecutiveRuns(values: Map<HourSlot, Long>): List<List<Pair<HourSlot, Long>>> {
    val runs = mutableListOf<MutableList<Pair<HourSlot, Long>>>()
    for (slot in values.keys.sorted()) {
        val run = runs.lastOrNull()
        if (run != null && run.last().first.next() == slot) {
            run += slot to values.getValue(slot)
        } else {
            runs += mutableListOf(slot to values.getValue(slot))
        }
    }
    return runs
}
