package com.jantursky.streamtracktv.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jantursky.streamtracktv.ui.UiGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class DashboardViewModel(private val graph: UiGraph, private val clock: () -> LocalDate = LocalDate::now) : ViewModel() {
    private val today = MutableStateFlow(clock())
    private val openSteps = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val model: StateFlow<DashboardModel?> =
        today
            .flatMapLatest { day ->
                combine(
                    graph.usage.observeDays(day.minusDays(WEEK_DAYS - 1), day),
                    graph.settings.settings,
                    graph.usage.observeLabels(),
                    graph.usage.observePendingCount(),
                    combine(graph.syncScheduler.nextRunAt(), openSteps) { next, open -> next to open },
                ) { week, settings, labels, pending, (next, open) ->
                    dashboardModel(DashboardInputs(day, week, settings, labels, pending, next, open))
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Called whenever the screen comes back: the day may have changed and permissions may be granted. */
    fun refresh() {
        today.value = clock()
        viewModelScope.launch {
            openSteps.value = withContext(Dispatchers.IO) { graph.permissions.statuses().count { !it.granted } }
            withContext(Dispatchers.IO) {
                try {
                    graph.collectNow()
                } catch (_: SecurityException) {
                    // Usage access missing; the setup card already says so.
                }
            }
        }
    }

    fun syncNow() = graph.syncScheduler.syncNow()

    private companion object {
        const val WEEK_DAYS = 7L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
