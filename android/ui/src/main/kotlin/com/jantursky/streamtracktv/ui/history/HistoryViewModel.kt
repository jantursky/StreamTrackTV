package com.jantursky.streamtracktv.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jantursky.streamtracktv.core.insights.HistoryModel
import com.jantursky.streamtracktv.core.insights.HistoryRange
import com.jantursky.streamtracktv.core.insights.buildHistory
import com.jantursky.streamtracktv.core.insights.rangeStart
import com.jantursky.streamtracktv.core.insights.stepDay
import com.jantursky.streamtracktv.ui.UiGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** What the History screen is looking at: the last day of the range, the range and an optional app. */
data class HistoryState(
    val endDay: LocalDate,
    val range: HistoryRange = HistoryRange.DAY,
    val appFilter: String? = null,
) {
    /** One day earlier or later; never past [today]. */
    fun step(days: Int, today: LocalDate): HistoryState = copy(endDay = stepDay(endDay, days, today))

    fun goToday(today: LocalDate): HistoryState = copy(endDay = today)

    fun withRange(range: HistoryRange): HistoryState = copy(range = range)

    fun withApp(packageName: String?): HistoryState = copy(appFilter = packageName)

    /** True when stepping forward would not move, because the view already ends today. */
    fun atToday(today: LocalDate): Boolean = endDay >= today
}

/** History screen model plus the names to show for the apps in it. */
data class HistoryScreenModel(val history: HistoryModel, val labels: Map<String, String>) {
    fun label(packageName: String): String = labels[packageName] ?: packageName
}

class HistoryViewModel(private val graph: UiGraph, private val clock: () -> LocalDate = LocalDate::now) : ViewModel() {
    private val _state = MutableStateFlow(HistoryState(clock()))
    val state: StateFlow<HistoryState> = _state

    @OptIn(ExperimentalCoroutinesApi::class)
    val model: StateFlow<HistoryScreenModel?> =
        _state
            .flatMapLatest { state ->
                combine(
                    graph.usage.observeDays(rangeStart(state.endDay, state.range), state.endDay),
                    graph.settings.settings,
                    graph.usage.observeLabels(),
                ) { buckets, settings, labels ->
                    HistoryScreenModel(
                        buildHistory(state.endDay, state.range, buckets, settings.excludedPackages, state.appFilter),
                        labels,
                    )
                }
            }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun step(days: Int) {
        _state.value = _state.value.step(days, clock())
    }

    fun goToday() {
        _state.value = _state.value.goToday(clock())
    }

    fun setRange(range: HistoryRange) {
        _state.value = _state.value.withRange(range)
    }

    fun setApp(packageName: String?) {
        _state.value = _state.value.withApp(packageName)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
