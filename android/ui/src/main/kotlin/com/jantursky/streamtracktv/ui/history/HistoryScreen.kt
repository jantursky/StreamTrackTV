package com.jantursky.streamtracktv.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jantursky.streamtracktv.collector.PackageManagerAppCatalog
import com.jantursky.streamtracktv.core.insights.AppShare
import com.jantursky.streamtracktv.core.insights.Comparison
import com.jantursky.streamtracktv.core.insights.Delta
import com.jantursky.streamtracktv.core.insights.Highlights
import com.jantursky.streamtracktv.core.insights.HistoryModel
import com.jantursky.streamtracktv.core.insights.HistoryPoint
import com.jantursky.streamtracktv.core.insights.HistoryRange
import com.jantursky.streamtracktv.core.insights.PeriodUnit
import com.jantursky.streamtracktv.core.insights.QuickPeriod
import com.jantursky.streamtracktv.ui.R
import com.jantursky.streamtracktv.ui.UiGraph
import com.jantursky.streamtracktv.ui.components.AppIcon
import com.jantursky.streamtracktv.ui.components.Caption
import com.jantursky.streamtracktv.ui.components.FocusSurface
import com.jantursky.streamtracktv.ui.components.PillButton
import com.jantursky.streamtracktv.ui.components.SectionTitle
import com.jantursky.streamtracktv.ui.components.ShareBar
import com.jantursky.streamtracktv.ui.dashboard.label
import com.jantursky.streamtracktv.ui.formatCount
import com.jantursky.streamtracktv.ui.formatDuration
import com.jantursky.streamtracktv.ui.theme.LocalPalette
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs

const val HISTORY_PREVIOUS_TAG = "history-previous"
const val HISTORY_NEXT_TAG = "history-next"
const val HISTORY_TODAY_TAG = "history-today"
const val HISTORY_CLEAR_FILTER_TAG = "history-clear-filter"

fun historyRangeTag(range: HistoryRange) = "history-range-${range.name.lowercase()}"

fun historyAppTag(packageName: String) = "history-app-$packageName"

fun historyQuickTag(quick: QuickPeriod) = "history-quick-${quick.name.lowercase()}"

fun historyDayTag(day: LocalDate) = "history-day-$day"

const val HISTORY_COMPARE_TAG = "history-compare"
const val HISTORY_HIGHLIGHTS_TAG = "history-highlights"

/** Actions of the History screen; each maps to one control. */
data class HistoryActions(
    val step: (Int) -> Unit,
    val goToday: () -> Unit,
    val setRange: (HistoryRange) -> Unit,
    val setApp: (String?) -> Unit,
    val setQuick: (QuickPeriod) -> Unit = {},
    val openDay: (LocalDate) -> Unit = {},
)

@Composable
fun HistoryRoute(graph: UiGraph) {
    val viewModel = viewModel { HistoryViewModel(graph) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val model by viewModel.model.collectAsStateWithLifecycle()
    val actions =
        HistoryActions(
            viewModel::step,
            viewModel::goToday,
            viewModel::setRange,
            viewModel::setApp,
            viewModel::setQuick,
            viewModel::openDay,
        )
    model?.let { HistoryScreen(state, it, graph.catalog, LocalDate.now(), actions) }
}

@Composable
fun HistoryScreen(
    state: HistoryState,
    model: HistoryScreenModel,
    catalog: PackageManagerAppCatalog?,
    today: LocalDate,
    actions: HistoryActions,
) {
    val palette = LocalPalette.current
    val history = model.history
    LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp), contentPadding = PaddingValues(bottom = 48.dp)) {
        item {
            Text(
                stringResource(R.string.history_title),
                style = MaterialTheme.typography.displayMedium,
                color = palette.ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                rangeLabel(history),
                style = MaterialTheme.typography.titleMedium,
                color = palette.text,
            )
        }
        item { Controls(state, today, actions) }
        if (history.isEmpty) {
            item { Caption(stringResource(R.string.history_empty)) }
        } else {
            item { Totals(history) }
            model.comparison?.let { comparison -> item { Comparison(comparison) } }
            model.highlights?.let { highlights -> item { HighlightsCard(highlights) } }
            item { Timeline(history) }
            if (history.points.first().hour == null) {
                item { DaysHeader() }
                items(history.points.reversed(), key = { "day-${it.day}" }) { point -> DayRow(point, actions) }
            }
            item { AppFilterHeader(history, model, actions) }
            items(history.apps, key = { it.packageName }) { app ->
                AppRow(app, model, history.appFilter == app.packageName, catalog, actions)
            }
            item { KeyClasses(history) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Controls(state: HistoryState, today: LocalDate, actions: HistoryActions) {
    val quick = state.quick(today)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // A flow row: six pills do not fit on one line of a TV, and a squeezed pill wraps its text letter by letter.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickPeriod.entries.forEach { period ->
                PillButton(
                    stringResource(period.label()),
                    onClick = { actions.setQuick(period) },
                    modifier = Modifier.testTag(historyQuickTag(period)),
                    primary = quick == period,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HistoryRange.entries.forEach { range ->
                PillButton(
                    stringResource(range.label()),
                    onClick = { actions.setRange(range) },
                    modifier = Modifier.testTag(historyRangeTag(range)),
                    primary = state.unit == null && state.range == range,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(
                stringResource(previousLabel(state.unit)),
                onClick = { actions.step(-1) },
                modifier = Modifier.testTag(HISTORY_PREVIOUS_TAG),
            )
            if (!state.atToday(today)) {
                PillButton(
                    stringResource(nextLabel(state.unit)),
                    onClick = { actions.step(1) },
                    modifier = Modifier.testTag(HISTORY_NEXT_TAG),
                )
                PillButton(
                    stringResource(R.string.history_today),
                    onClick = { actions.goToday() },
                    modifier = Modifier.testTag(HISTORY_TODAY_TAG),
                )
            }
        }
    }
}

private fun QuickPeriod.label(): Int =
    when (this) {
        QuickPeriod.TODAY -> R.string.history_today
        QuickPeriod.YESTERDAY -> R.string.history_yesterday
        QuickPeriod.THIS_WEEK -> R.string.history_this_week
        QuickPeriod.LAST_WEEK -> R.string.history_last_week
        QuickPeriod.THIS_MONTH -> R.string.history_this_month
        QuickPeriod.LAST_MONTH -> R.string.history_last_month
    }

private fun previousLabel(unit: PeriodUnit?): Int =
    when (unit) {
        PeriodUnit.WEEK -> R.string.history_previous_week
        PeriodUnit.MONTH -> R.string.history_previous_month
        else -> R.string.history_previous
    }

private fun nextLabel(unit: PeriodUnit?): Int =
    when (unit) {
        PeriodUnit.WEEK -> R.string.history_next_week
        PeriodUnit.MONTH -> R.string.history_next_month
        else -> R.string.history_next
    }

@Composable
private fun rangeLabel(history: HistoryModel): String {
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    return if (history.from == history.to) {
        history.to.format(format)
    } else {
        "${history.from.format(format)} – ${history.to.format(format)}"
    }
}

private fun HistoryRange.label(): Int =
    when (this) {
        HistoryRange.DAY -> R.string.history_range_day
        HistoryRange.WEEK -> R.string.history_range_week
        HistoryRange.MONTH -> R.string.history_range_month
    }

@Composable
private fun Totals(history: HistoryModel) {
    val palette = LocalPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf(
            stringResource(R.string.today_screen_time) to formatDuration(history.screenOnMs),
            stringResource(R.string.history_foreground) to formatDuration(history.foregroundMs),
            stringResource(R.string.today_key_presses) to formatCount(history.keyPresses),
        ).forEach { (title, value) ->
            FocusSurface(onClick = null, modifier = Modifier.weight(1f).fillMaxHeight()) {
                Caption(title)
                Text(
                    value,
                    style = MaterialTheme.typography.headlineSmall,
                    color = palette.ink,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun Comparison(comparison: Comparison) {
    val palette = LocalPalette.current
    FocusSurface(onClick = null, modifier = Modifier.fillMaxWidth().testTag(HISTORY_COMPARE_TAG)) {
        SectionTitle(stringResource(R.string.history_compare_title))
        listOf(
            R.string.today_screen_time to formatDelta(comparison.screenOn, ::formatDuration),
            R.string.history_foreground to formatDelta(comparison.foreground, ::formatDuration),
            R.string.today_key_presses to formatDelta(comparison.keyPresses, ::formatCount),
        ).forEach { (title, value) ->
            Text(
                stringResource(R.string.history_compare_value, stringResource(title), value),
                style = MaterialTheme.typography.bodyLarge,
                color = palette.text,
            )
        }
    }
}

/** `+1 h (+50 %)`, `−10 (−50 %)`, `0` and no percentage when there is nothing to divide by. */
private fun formatDelta(delta: Delta, format: (Long) -> String): String {
    val sign =
        if (delta.diff > 0) {
            "+"
        } else if (delta.diff < 0) {
            "\u2212"
        } else {
            ""
        }
    val value = "$sign${format(abs(delta.diff))}"
    val percent = delta.percent ?: return value
    val percentSign =
        if (percent > 0) {
            "+"
        } else if (percent < 0) {
            "\u2212"
        } else {
            ""
        }
    return "$value ($percentSign${abs(percent)} %)"
}

@Composable
private fun HighlightsCard(highlights: Highlights) {
    val palette = LocalPalette.current
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    FocusSurface(onClick = null, modifier = Modifier.fillMaxWidth().testTag(HISTORY_HIGHLIGHTS_TAG)) {
        SectionTitle(stringResource(R.string.history_highlights))
        val rows =
            listOfNotNull(
                highlights.dailyAverageMs?.let { stringResource(R.string.history_daily_average) to formatDuration(it) },
                highlights.busiestDay?.let {
                    stringResource(R.string.history_busiest_day) to
                        stringResource(R.string.history_busiest_day_value, it.format(dateFormat), formatDuration(highlights.busiestDayMs))
                },
                stringResource(R.string.history_peak_hour) to "%02d:00".format(highlights.peakHour),
            )
        rows.forEach { (title, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = palette.text)
                Text(value, style = MaterialTheme.typography.bodyLarge, color = palette.ink)
            }
        }
    }
}

@Composable
private fun DaysHeader() {
    Column {
        SectionTitle(stringResource(R.string.history_days))
        Caption(stringResource(R.string.history_days_hint))
    }
}

@Composable
private fun DayRow(point: HistoryPoint, actions: HistoryActions) {
    val palette = LocalPalette.current
    val date = point.day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    val description =
        stringResource(R.string.history_day_row, date, formatDuration(point.screenOnMs), formatDuration(point.foregroundMs))
    FocusSurface(
        onClick = { actions.openDay(point.day) },
        modifier = Modifier.fillMaxWidth().testTag(historyDayTag(point.day)).semantics { contentDescription = description },
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(date, style = MaterialTheme.typography.titleMedium, color = palette.ink, modifier = Modifier.weight(1f))
            Text(formatDuration(point.screenOnMs), style = MaterialTheme.typography.bodyLarge, color = palette.ink)
            Spacer(Modifier.width(24.dp))
            Caption(formatDuration(point.foregroundMs))
        }
    }
}

@Composable
private fun Timeline(history: HistoryModel) {
    val palette = LocalPalette.current
    val points = history.points
    val peak = points.maxOf { maxOf(it.screenOnMs, it.foregroundMs) }.coerceAtLeast(1)
    val description = timelineDescription(points)
    FocusSurface(onClick = null, modifier = Modifier.fillMaxWidth()) {
        SectionTitle(
            stringResource(if (history.range == HistoryRange.DAY) R.string.history_by_hour else R.string.history_by_day),
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .semantics { contentDescription = description },
        ) {
            val slot = size.width / points.size
            val bar = slot * 0.7f
            points.forEachIndexed { index, point ->
                val x = index * slot + (slot - bar) / 2
                val screenHeight = size.height * point.screenOnMs / peak
                val foregroundHeight = size.height * point.foregroundMs / peak
                drawRoundRect(
                    palette.border,
                    Offset(x, size.height - screenHeight),
                    Size(bar, screenHeight),
                    CornerRadius(4f),
                )
                drawRoundRect(
                    palette.accent,
                    Offset(x, size.height - foregroundHeight),
                    Size(bar, foregroundHeight),
                    CornerRadius(4f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Caption(timelineStart(points))
            Caption(stringResource(R.string.history_legend))
            Caption(timelineEnd(points))
        }
    }
}

private fun timelineStart(points: List<HistoryPoint>): String =
    points.first().let { it.hour?.let { hour -> "%02d:00".format(hour) } ?: it.day.toString() }

private fun timelineEnd(points: List<HistoryPoint>): String =
    points.last().let { it.hour?.let { hour -> "%02d:00".format(hour + 1) } ?: it.day.toString() }

private fun timelineDescription(points: List<HistoryPoint>): String =
    points.joinToString(separator = "; ") { point ->
        val name = point.hour?.let { "%02d:00".format(it) } ?: point.day.toString()
        "$name ${formatDuration(point.foregroundMs)}"
    }

@Composable
private fun AppFilterHeader(history: HistoryModel, model: HistoryScreenModel, actions: HistoryActions) {
    Column {
        SectionTitle(stringResource(R.string.history_apps))
        val filter = history.appFilter
        if (filter != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Caption(stringResource(R.string.history_filtered, model.label(filter)))
                PillButton(
                    stringResource(R.string.history_clear_filter),
                    onClick = { actions.setApp(null) },
                    modifier = Modifier.testTag(HISTORY_CLEAR_FILTER_TAG),
                )
            }
        } else if (history.apps.isEmpty()) {
            Caption(stringResource(R.string.today_no_apps))
        } else {
            Caption(stringResource(R.string.history_filter_hint))
        }
    }
}

@Composable
private fun AppRow(
    app: AppShare,
    model: HistoryScreenModel,
    selected: Boolean,
    catalog: PackageManagerAppCatalog?,
    actions: HistoryActions,
) {
    val palette = LocalPalette.current
    FocusSurface(
        onClick = { actions.setApp(if (selected) null else app.packageName) },
        modifier = Modifier.fillMaxWidth().testTag(historyAppTag(app.packageName)),
        color = if (selected) palette.accentWash else palette.surface,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (catalog != null) {
                AppIcon(app.packageName, model.label(app.packageName), catalog)
                Spacer(Modifier.width(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(model.label(app.packageName), style = MaterialTheme.typography.titleMedium, color = palette.ink)
                Spacer(Modifier.height(6.dp))
                ShareBar(app.share.toFloat())
            }
            Spacer(Modifier.width(24.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(formatDuration(app.foregroundMs), style = MaterialTheme.typography.titleMedium, color = palette.ink)
                Caption("${(app.share * PERCENT).toInt()} %")
            }
        }
    }
}

private const val PERCENT = 100

@Composable
private fun KeyClasses(history: HistoryModel) {
    val palette = LocalPalette.current
    FocusSurface(onClick = null, modifier = Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.history_keys))
        if (history.keysByClass.isEmpty()) Caption(stringResource(R.string.history_no_keys))
        history.keysByClass.entries.sortedByDescending { it.value }.forEach { (keyClass, count) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(keyClass.label()), style = MaterialTheme.typography.bodyLarge, color = palette.text)
                Text(formatCount(count), style = MaterialTheme.typography.bodyLarge, color = palette.ink)
            }
        }
    }
}
