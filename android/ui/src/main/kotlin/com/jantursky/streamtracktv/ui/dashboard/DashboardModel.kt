package com.jantursky.streamtracktv.ui.dashboard

import com.jantursky.streamtracktv.core.insights.BingeSession
import com.jantursky.streamtracktv.core.insights.DaySummary
import com.jantursky.streamtracktv.core.insights.GoalStatus
import com.jantursky.streamtracktv.core.insights.IdleScreenWindow
import com.jantursky.streamtracktv.core.insights.detectBinges
import com.jantursky.streamtracktv.core.insights.detectIdleScreen
import com.jantursky.streamtracktv.core.insights.evaluateGoals
import com.jantursky.streamtracktv.core.insights.summarizeDay
import com.jantursky.streamtracktv.core.model.BucketSet
import com.jantursky.streamtracktv.data.settings.Settings
import com.jantursky.streamtracktv.data.settings.SyncRecord
import java.time.LocalDate

/** Everything the Today screen shows, computed from stored data in one pure step. */
data class DashboardModel(
    val today: LocalDate,
    val summary: DaySummary,
    val labels: Map<String, String>,
    val goals: List<GoalStatus>,
    val binges: List<BingeSession>,
    val idle: List<IdleScreenWindow>,
    val lastSync: SyncRecord?,
    val lastSuccessMs: Long?,
    val nextSyncMs: Long?,
    val pendingRows: Int,
    val openSetupSteps: Int,
) {
    fun label(packageName: String): String = labels[packageName] ?: packageName
}

/** Inputs of [dashboardModel] that come from outside the database. */
data class DashboardInputs(
    val today: LocalDate,
    val week: BucketSet,
    val settings: Settings,
    val labels: Map<String, String>,
    val pendingRows: Int,
    val nextSyncMs: Long?,
    val openSetupSteps: Int,
)

fun dashboardModel(inputs: DashboardInputs): DashboardModel {
    val summary = summarizeDay(inputs.today, inputs.week)
    val excluded = inputs.settings.excludedPackages
    return DashboardModel(
        today = inputs.today,
        summary = summary.copy(apps = summary.apps.filter { it.packageName !in excluded }),
        labels = inputs.labels,
        goals = evaluateGoals(inputs.settings.goals, summary),
        binges = detectBinges(inputs.week.usage.filter { it.packageName !in excluded }).sortedByDescending { it.start },
        idle = detectIdleScreen(inputs.week.screen, inputs.week.keys).sortedByDescending { it.start },
        lastSync = inputs.settings.lastSync,
        lastSuccessMs = inputs.settings.lastSuccessMs,
        nextSyncMs = inputs.nextSyncMs,
        pendingRows = inputs.pendingRows,
        openSetupSteps = inputs.openSetupSteps,
    )
}
