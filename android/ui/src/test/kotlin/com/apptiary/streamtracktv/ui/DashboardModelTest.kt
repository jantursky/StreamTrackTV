package com.apptiary.streamtracktv.ui

import com.apptiary.streamtracktv.core.insights.GoalState
import com.apptiary.streamtracktv.core.seed.demoWeek
import com.apptiary.streamtracktv.data.settings.Settings
import com.apptiary.streamtracktv.ui.dashboard.DashboardInputs
import com.apptiary.streamtracktv.ui.dashboard.dashboardModel
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DashboardModelTest {
    private val today = LocalDate.parse("2026-11-08")
    private val week = demoWeek(today, currentHour = 23)

    private fun model(settings: Settings = Settings()) =
        dashboardModel(
            DashboardInputs(
                today,
                week.buckets,
                settings,
                week.labels,
                pendingRows = 4,
                nextSyncMs = null,
                openSetupSteps = 1,
            ),
        )

    @Test
    fun `summarizes today and finds the week's patterns`() {
        val model = model()
        assertTrue(model.summary.screenOnMs > 0)
        assertTrue(model.binges.isNotEmpty())
        assertTrue(model.idle.isNotEmpty())
        assertEquals("Netflix", model.label("com.netflix.ninja"))
        assertEquals("com.unknown.app", model.label("com.unknown.app"))
        assertEquals(4, model.pendingRows)
    }

    @Test
    fun `hides excluded apps and evaluates limits`() {
        val top =
            model()
                .summary.apps
                .first()
                .packageName
        val filtered = model(Settings(excludedPackages = setOf(top), screenLimitMinutes = 1))
        assertTrue(filtered.summary.apps.none { it.packageName == top })
        assertTrue(filtered.binges.none { it.packageName == top })
        assertEquals(GoalState.EXCEEDED, filtered.goals.single().state)
    }
}
