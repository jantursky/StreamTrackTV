package com.apptiary.streamtracktv.core.insights

/** What a daily limit applies to. */
public sealed interface GoalTarget {
    public data object ScreenTime : GoalTarget

    public data class App(val packageName: String) : GoalTarget
}

public data class Goal(val target: GoalTarget, val dailyLimitMs: Long) {
    init {
        require(dailyLimitMs > 0) { "A limit must be positive" }
    }
}

public enum class GoalState { ON_TRACK, NEAR_LIMIT, EXCEEDED }

public data class GoalStatus(val goal: Goal, val usedMs: Long) {
    /** Share of the limit used; above 1 when exceeded. */
    val progress: Double get() = usedMs.toDouble() / goal.dailyLimitMs

    val state: GoalState
        get() =
            when {
                usedMs >= goal.dailyLimitMs -> GoalState.EXCEEDED
                progress >= NEAR_LIMIT_SHARE -> GoalState.NEAR_LIMIT
                else -> GoalState.ON_TRACK
            }

    val remainingMs: Long get() = (goal.dailyLimitMs - usedMs).coerceAtLeast(0)
}

/** From this share of the limit on, a goal reports [GoalState.NEAR_LIMIT]. */
public const val NEAR_LIMIT_SHARE: Double = 0.8

/** Progress of each goal against one day. */
public fun evaluateGoals(goals: List<Goal>, day: DaySummary): List<GoalStatus> {
    val appTime = day.apps.associate { it.packageName to it.foregroundMs }
    return goals.map { goal ->
        val used =
            when (val target = goal.target) {
                GoalTarget.ScreenTime -> day.screenOnMs
                is GoalTarget.App -> appTime[target.packageName] ?: 0L
            }
        GoalStatus(goal, used)
    }
}
