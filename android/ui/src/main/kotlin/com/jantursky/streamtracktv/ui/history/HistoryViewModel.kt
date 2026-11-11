package com.jantursky.streamtracktv.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jantursky.streamtracktv.core.insights.Comparison
import com.jantursky.streamtracktv.core.insights.Highlights
import com.jantursky.streamtracktv.core.insights.HistoryModel
import com.jantursky.streamtracktv.core.insights.HistoryPeriod
import com.jantursky.streamtracktv.core.insights.HistoryRange
import com.jantursky.streamtracktv.core.insights.PeriodUnit
import com.jantursky.streamtracktv.core.insights.QuickPeriod
import com.jantursky.streamtracktv.core.insights.anchorFor
import com.jantursky.streamtracktv.core.insights.buildHistory
import com.jantursky.streamtracktv.core.insights.calendarPeriod
import com.jantursky.streamtracktv.core.insights.compare
import com.jantursky.streamtracktv.core.insights.highlights
import com.jantursky.streamtracktv.core.insights.periodFor
import com.jantursky.streamtracktv.core.insights.previousPeriod
import com.jantursky.streamtracktv.core.insights.rangeStart
import com.jantursky.streamtracktv.core.insights.stepDay
import com.jantursky.streamtracktv.core.insights.stepPeriod
import com.jantursky.streamtracktv.core.insights.totals
import com.jantursky.streamtracktv.core.model.BucketSet
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

/** First day of the week in the TV's locale. */
fun localeFirstDayOfWeek(): DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

/**
 * What the History screen is looking at: an anchor day, a rolling [range] ending on it, or, when [unit] is set,
 * the calendar day, week or month that contains it. An optional app narrows the foreground time.
 */
data class HistoryState(
    val endDay: LocalDate,
    val range: HistoryRange = HistoryRange.DAY,
    val appFilter: String? = null,
    val unit: PeriodUnit? = null,
) {
    /** The days the view covers. A calendar period that has not ended stops at [today]. */
    fun period(today: LocalDate, firstDayOfWeek: DayOfWeek = localeFirstDayOfWeek()): HistoryPeriod =
        if (unit == null) {
            HistoryPeriod(rangeStart(endDay, range), endDay)
        } else {
            calendarPeriod(unit, endDay, today, firstDayOfWeek)
        }

    /** The quick period that is showing, or null for a rolling view or a calendar period without a button. */
    fun quick(today: LocalDate, firstDayOfWeek: DayOfWeek = localeFirstDayOfWeek()): QuickPeriod? =
        unit?.let {
            val shown = period(today, firstDayOfWeek)
            QuickPeriod.entries.firstOrNull { it.unit == unit && periodFor(it, today, firstDayOfWeek) == shown }
        }

    /** One day earlier or later, or one calendar unit for a calendar period; never past [today]. */
    fun step(days: Int, today: LocalDate): HistoryState =
        copy(endDay = if (unit == null) stepDay(endDay, days, today) else stepPeriod(unit, endDay, days, today))

    fun goToday(today: LocalDate): HistoryState = copy(endDay = today)

    /** A rolling view; from a calendar period it ends on that period's last day. */
    fun withRange(range: HistoryRange, today: LocalDate = endDay, firstDayOfWeek: DayOfWeek = localeFirstDayOfWeek()): HistoryState =
        copy(range = range, endDay = if (unit == null) endDay else period(today, firstDayOfWeek).to, unit = null)

    fun withQuick(quick: QuickPeriod, today: LocalDate, firstDayOfWeek: DayOfWeek = localeFirstDayOfWeek()): HistoryState =
        copy(endDay = anchorFor(quick, today, firstDayOfWeek), unit = quick.unit)

    /** The day view of [day], as a calendar day. */
    fun openDay(day: LocalDate): HistoryState = copy(endDay = day, unit = PeriodUnit.DAY)

    fun withApp(packageName: String?): HistoryState = copy(appFilter = packageName)

    /** True when stepping forward would not move, because the view already ends today. */
    fun atToday(today: LocalDate): Boolean = endDay >= today
}

/** History screen model, how it compares with the period before, plus the names to show for the apps in it. */
data class HistoryScreenModel(
    val history: HistoryModel,
    val labels: Map<String, String>,
    val comparison: Comparison? = null,
    val highlights: Highlights? = null,
) {
    fun label(packageName: String): String = labels[packageName] ?: packageName
}

/** First day the screen must read: the shown period and the one it is compared with. */
fun historyQueryStart(state: HistoryState, today: LocalDate, firstDayOfWeek: DayOfWeek = localeFirstDayOfWeek()): LocalDate =
    previousPeriod(state.period(today, firstDayOfWeek), state.unit).from

/** The screen model for [state] from [buckets] that cover [historyQueryStart] up to the end of the period. */
@Suppress("LongParameterList") // the inputs of one pure computation, kept apart so each is easy to fake in a test
fun buildScreenModel(
    state: HistoryState,
    today: LocalDate,
    buckets: BucketSet,
    excluded: Set<String>,
    labels: Map<String, String>,
    firstDayOfWeek: DayOfWeek = localeFirstDayOfWeek(),
): HistoryScreenModel {
    val period = state.period(today, firstDayOfWeek)
    val current = buildHistory(period, buckets, excluded, state.appFilter)
    val before = buildHistory(previousPeriod(period, state.unit), buckets, excluded, state.appFilter)
    return HistoryScreenModel(current, labels, compare(current.totals(), before.totals()), highlights(current))
}

class HistoryViewModel(private val graph: UiGraph, private val clock: () -> LocalDate = LocalDate::now) : ViewModel() {
    private val _state = MutableStateFlow(HistoryState(clock()))
    val state: StateFlow<HistoryState> = _state

    @OptIn(ExperimentalCoroutinesApi::class)
    val model: StateFlow<HistoryScreenModel?> =
        _state
            .flatMapLatest { state ->
                val today = clock()
                combine(
                    graph.usage.observeDays(historyQueryStart(state, today), state.period(today).to),
                    graph.settings.settings,
                    graph.usage.observeLabels(),
                ) { buckets, settings, labels ->
                    buildScreenModel(state, today, buckets, settings.excludedPackages, labels)
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
        _state.value = _state.value.withRange(range, clock())
    }

    fun setQuick(quick: QuickPeriod) {
        _state.value = _state.value.withQuick(quick, clock())
    }

    fun openDay(day: LocalDate) {
        _state.value = _state.value.openDay(day)
    }

    fun setApp(packageName: String?) {
        _state.value = _state.value.withApp(packageName)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
