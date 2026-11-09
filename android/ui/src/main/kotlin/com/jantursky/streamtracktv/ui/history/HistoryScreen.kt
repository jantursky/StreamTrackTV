package com.jantursky.streamtracktv.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.jantursky.streamtracktv.core.insights.HistoryModel
import com.jantursky.streamtracktv.core.insights.HistoryPoint
import com.jantursky.streamtracktv.core.insights.HistoryRange
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

const val HISTORY_PREVIOUS_TAG = "history-previous"
const val HISTORY_NEXT_TAG = "history-next"
const val HISTORY_TODAY_TAG = "history-today"
const val HISTORY_CLEAR_FILTER_TAG = "history-clear-filter"

fun historyRangeTag(range: HistoryRange) = "history-range-${range.name.lowercase()}"

fun historyAppTag(packageName: String) = "history-app-$packageName"

/** Actions of the History screen; each maps to one control. */
data class HistoryActions(
    val step: (Int) -> Unit,
    val goToday: () -> Unit,
    val setRange: (HistoryRange) -> Unit,
    val setApp: (String?) -> Unit,
)

@Composable
fun HistoryRoute(graph: UiGraph) {
    val viewModel = viewModel { HistoryViewModel(graph) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val model by viewModel.model.collectAsStateWithLifecycle()
    val actions = HistoryActions(viewModel::step, viewModel::goToday, viewModel::setRange, viewModel::setApp)
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
            item { Timeline(history) }
            item { AppFilterHeader(history, model, actions) }
            items(history.apps, key = { it.packageName }) { app ->
                AppRow(app, model, history.appFilter == app.packageName, catalog, actions)
            }
            item { KeyClasses(history) }
        }
    }
}

@Composable
private fun Controls(state: HistoryState, today: LocalDate, actions: HistoryActions) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HistoryRange.entries.forEach { range ->
                PillButton(
                    stringResource(range.label()),
                    onClick = { actions.setRange(range) },
                    modifier = Modifier.testTag(historyRangeTag(range)),
                    primary = state.range == range,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(
                stringResource(R.string.history_previous),
                onClick = { actions.step(-1) },
                modifier = Modifier.testTag(HISTORY_PREVIOUS_TAG),
            )
            if (!state.atToday(today)) {
                PillButton(
                    stringResource(R.string.history_next),
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
