package com.jantursky.streamtracktv.ui

import androidx.test.core.app.ApplicationProvider
import com.jantursky.streamtracktv.collector.PackageManagerAppCatalog
import com.jantursky.streamtracktv.core.seed.demoWeek
import com.jantursky.streamtracktv.data.settings.Settings
import com.jantursky.streamtracktv.ui.dashboard.DashboardInputs
import com.jantursky.streamtracktv.ui.dashboard.DashboardModel
import com.jantursky.streamtracktv.ui.dashboard.dashboardModel
import java.time.LocalDate

/** A Today model built from the demo week, shared by the accessibility and focus tests. */
object DashboardFixture {
    val today: LocalDate = LocalDate.parse("2026-11-08")

    fun model(settings: Settings = Settings(screenLimitMinutes = 240)): DashboardModel {
        val week = demoWeek(today, currentHour = 23)
        return dashboardModel(
            DashboardInputs(today, week.buckets, settings, week.labels, pendingRows = 4, nextSyncMs = null, openSetupSteps = 0),
        )
    }

    fun catalog() = PackageManagerAppCatalog(ApplicationProvider.getApplicationContext())
}
