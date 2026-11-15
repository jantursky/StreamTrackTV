package com.jantursky.streamtracktv.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jantursky.streamtracktv.collector.PackageManagerAppCatalog
import com.jantursky.streamtracktv.core.insights.GoalState
import com.jantursky.streamtracktv.core.insights.GoalStatus
import com.jantursky.streamtracktv.core.insights.GoalTarget
import com.jantursky.streamtracktv.core.insights.HourRange
import com.jantursky.streamtracktv.core.model.KeyClass
import com.jantursky.streamtracktv.ui.R
import com.jantursky.streamtracktv.ui.components.AppIcon
import com.jantursky.streamtracktv.ui.components.Caption
import com.jantursky.streamtracktv.ui.components.FocusSurface
import com.jantursky.streamtracktv.ui.components.PillButton
import com.jantursky.streamtracktv.ui.components.SectionTitle
import com.jantursky.streamtracktv.ui.components.ShareBar
import com.jantursky.streamtracktv.ui.formatCount
import com.jantursky.streamtracktv.ui.formatDayHour
import com.jantursky.streamtracktv.ui.formatDuration
import com.jantursky.streamtracktv.ui.formatHourRange
import com.jantursky.streamtracktv.ui.formatMoment
import com.jantursky.streamtracktv.ui.theme.LocalPalette
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val TOP_APPS = 5

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DashboardScreen(
    model: DashboardModel,
    catalog: PackageManagerAppCatalog,
    onOpenSetup: () -> Unit,
    onSyncNow: () -> Unit,
) {
    val syncNow = remember { FocusRequester() }
    LazyColumn(
        // Entering Today from the rail lands on Sync now, the first stop, not on the card beside the rail.
        modifier = Modifier.focusProperties { enter = { syncNow } }.focusGroup(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item { Header(model, onSyncNow, syncNow) }
        if (model.openSetupSteps > 0) {
            item {
                FocusSurface(onClick = onOpenSetup, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        pluralStringResource(R.plurals.today_setup_missing, model.openSetupSteps, model.openSetupSteps),
                        style = MaterialTheme.typography.titleMedium,
                        color = LocalPalette.current.ink,
                    )
                }
            }
        }
        item { StatRow(model) }
        item { TopApps(model, catalog) }
        item { HourlyActivity(model.summary.screenOnByHour, model.summary.activeWindows) }
        item { Goals(model) }
        item { Insights(model) }
        item { SyncCard(model) }
    }
}

/** Title and date on the left, Sync now on the right: the first focus stop of the content. */
@Composable
private fun Header(model: DashboardModel, onSyncNow: () -> Unit, syncNow: FocusRequester) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.today_title),
                style = MaterialTheme.typography.displayMedium,
                color = LocalPalette.current.ink,
            )
            Caption(model.today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)))
        }
        Spacer(Modifier.width(24.dp))
        PillButton(stringResource(R.string.sync_now), onSyncNow, Modifier.focusRequester(syncNow), primary = true)
    }
}

@Composable
private fun StatRow(model: DashboardModel) {
    Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        StatCard(
            stringResource(R.string.today_screen_time),
            formatDuration(model.summary.screenOnMs),
            null,
            Modifier.weight(1f),
        )
        val classes =
            model.summary.keysByClass.entries
                .sortedByDescending { it.value }
                .take(3)
        val breakdown = classes.map { (keyClass, count) -> "${stringResource(keyClass.label())} ${formatCount(count)}" }
        StatCard(
            stringResource(R.string.today_key_presses),
            formatCount(model.summary.keyPresses),
            breakdown.joinToString(" · "),
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(title: String, value: String, detail: String?, modifier: Modifier) {
    val palette = LocalPalette.current
    val detailText = detail?.takeIf { it.isNotEmpty() }
    val description =
        if (detailText == null) {
            stringResource(R.string.a11y_stat_card, title, value)
        } else {
            stringResource(R.string.a11y_stat_card_detail, title, value, detailText)
        }
    FocusSurface(onClick = null, modifier = modifier.fillMaxHeight(), description = description) {
        Caption(title)
        Text(value, style = MaterialTheme.typography.headlineMedium, color = palette.ink)
        detailText?.let { Caption(it) }
    }
}

@Composable
private fun TopApps(model: DashboardModel, catalog: PackageManagerAppCatalog) {
    val palette = LocalPalette.current
    FocusSurface(onClick = null, modifier = Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.today_top_apps))
        val apps = model.summary.apps.take(TOP_APPS)
        if (apps.isEmpty()) Caption(stringResource(R.string.today_no_apps))
        val longest = apps.firstOrNull()?.foregroundMs?.coerceAtLeast(1) ?: 1
        apps.forEach { app ->
            val name = model.label(app.packageName)
            val duration = formatDuration(app.foregroundMs)
            val rowDescription = stringResource(R.string.a11y_app_time, name, duration)
            Row(
                Modifier
                    .padding(vertical = 8.dp)
                    .semantics(mergeDescendants = true) { contentDescription = rowDescription },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(app.packageName, name, catalog)
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, color = palette.ink)
                    Spacer(Modifier.height(6.dp))
                    ShareBar(app.foregroundMs.toFloat() / longest, label = null)
                }
                Spacer(Modifier.width(24.dp))
                Text(duration, style = MaterialTheme.typography.bodyLarge, color = palette.ink)
            }
        }
    }
}

@Composable
private fun HourlyActivity(screenOnByHour: List<Long>, windows: List<HourRange>) {
    val palette = LocalPalette.current
    val windowsText = remember(windows) { windows.joinToString { formatHourRange(it) } }
    val screenOn = remember(screenOnByHour) { formatDuration(screenOnByHour.sum()) }
    val description =
        if (windows.isEmpty()) {
            stringResource(R.string.a11y_hourly_none, screenOn)
        } else {
            stringResource(R.string.a11y_hourly, screenOn, windowsText)
        }
    FocusSurface(onClick = null, modifier = Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.today_by_hour))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .semantics { contentDescription = description },
        ) {
            val gap = 6.dp.toPx()
            val barWidth = (size.width - gap * (screenOnByHour.size - 1)) / screenOnByHour.size
            val hourMs = 3_600_000f
            screenOnByHour.forEachIndexed { hour, ms ->
                val height = (ms / hourMs).coerceIn(0f, 1f) * size.height
                val active = windows.any { hour >= it.startHour && hour < it.endHour }
                drawRoundRect(
                    color = if (active) palette.focusRing else palette.borderStrong,
                    topLeft = Offset(hour * (barWidth + gap), size.height - height.coerceAtLeast(2f)),
                    size = Size(barWidth, height.coerceAtLeast(2f)),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
        }
        HourAxis(Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            if (windows.isEmpty()) {
                stringResource(
                    R.string.today_no_windows,
                )
            } else {
                stringResource(R.string.today_windows, windowsText)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = palette.ink,
            // The chart's summary description already says this.
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

@Composable
private fun Goals(model: DashboardModel) {
    FocusSurface(onClick = null, modifier = Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.today_goals))
        if (model.goals.isEmpty()) Caption(stringResource(R.string.today_no_goals))
        model.goals.forEach { GoalRow(it, model) }
    }
}

@Composable
private fun GoalRow(status: GoalStatus, model: DashboardModel) {
    val palette = LocalPalette.current
    val name =
        when (val target = status.goal.target) {
            GoalTarget.ScreenTime -> stringResource(R.string.goal_screen_time)
            is GoalTarget.App -> model.label(target.packageName)
        }
    val stateLabel =
        stringResource(
            when (status.state) {
                GoalState.ON_TRACK -> R.string.goal_on_track
                GoalState.NEAR_LIMIT -> R.string.goal_near_limit
                GoalState.EXCEEDED -> R.string.goal_exceeded
            },
        )
    val progressText =
        stringResource(R.string.goal_progress, formatDuration(status.usedMs), formatDuration(status.goal.dailyLimitMs))
    val rowDescription = stringResource(R.string.a11y_goal, name, stateLabel, progressText)
    Column(Modifier.padding(vertical = 8.dp).semantics(mergeDescendants = true) { contentDescription = rowDescription }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                color = palette.ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.goal_state_progress, stateLabel, progressText),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.ink,
            )
        }
        Spacer(Modifier.height(8.dp))
        ShareBar(status.progress.toFloat(), label = null)
    }
}

@Composable
private fun Insights(model: DashboardModel) {
    val palette = LocalPalette.current
    val title = stringResource(R.string.today_insights)
    val lines =
        model.binges.map { binge ->
            val start = formatDayHour(binge.start.day, binge.start.hour)
            pluralStringResource(R.plurals.today_binge, binge.hours, model.label(binge.packageName), binge.hours, start)
        } +
            model.idle.map { idle ->
                pluralStringResource(R.plurals.today_idle, idle.hours, idle.hours, formatDayHour(idle.start.day, idle.start.hour))
            }
    val empty = stringResource(R.string.today_no_insights)
    val description = (listOf(title) + lines.ifEmpty { listOf(empty) }).joinToString("\n")
    FocusSurface(onClick = null, modifier = Modifier.fillMaxWidth(), description = description) {
        SectionTitle(title)
        if (lines.isEmpty()) Caption(empty)
        lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, color = palette.ink) }
    }
}

@Composable
private fun SyncCard(model: DashboardModel) {
    val palette = LocalPalette.current
    val zone = ZoneId.systemDefault()
    val title = stringResource(R.string.today_sync)
    val last = model.lastSync
    val lastLine =
        when {
            last == null -> stringResource(R.string.sync_never)
            last.ok -> stringResource(R.string.sync_last, formatMoment(last.atMs, model.today, zone))
            else -> stringResource(R.string.sync_last_failed, formatMoment(last.atMs, model.today, zone), last.message.orEmpty())
        }
    val next = model.nextSyncMs
    val nextLine =
        if (next == null) {
            stringResource(R.string.sync_next_unknown)
        } else {
            stringResource(R.string.sync_next, formatMoment(next, model.today, zone))
        }
    val pendingLine = pluralStringResource(R.plurals.sync_pending, model.pendingRows, model.pendingRows)
    val description = listOf(title, lastLine, nextLine, pendingLine).joinToString("\n")
    FocusSurface(onClick = null, modifier = Modifier.fillMaxWidth(), description = description) {
        SectionTitle(title)
        Text(lastLine, style = MaterialTheme.typography.bodyLarge, color = palette.ink)
        Text(nextLine, style = MaterialTheme.typography.bodyLarge, color = palette.text)
        Text(pendingLine, style = MaterialTheme.typography.bodyLarge, color = palette.text)
    }
}

fun KeyClass.label(): Int =
    when (this) {
        KeyClass.DPAD -> R.string.key_class_dpad
        KeyClass.SELECT -> R.string.key_class_select
        KeyClass.BACK -> R.string.key_class_back
        KeyClass.HOME -> R.string.key_class_home
        KeyClass.MEDIA -> R.string.key_class_media
        KeyClass.VOLUME -> R.string.key_class_volume
        KeyClass.OTHER -> R.string.key_class_other
    }
