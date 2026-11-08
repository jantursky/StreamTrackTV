package com.apptiary.streamtracktv.ui.dashboard

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.apptiary.streamtracktv.collector.PackageManagerAppCatalog
import com.apptiary.streamtracktv.core.insights.GoalState
import com.apptiary.streamtracktv.core.insights.GoalStatus
import com.apptiary.streamtracktv.core.insights.GoalTarget
import com.apptiary.streamtracktv.core.insights.HourRange
import com.apptiary.streamtracktv.core.model.KeyClass
import com.apptiary.streamtracktv.ui.R
import com.apptiary.streamtracktv.ui.components.AppIcon
import com.apptiary.streamtracktv.ui.components.Caption
import com.apptiary.streamtracktv.ui.components.FocusSurface
import com.apptiary.streamtracktv.ui.components.PillButton
import com.apptiary.streamtracktv.ui.components.SectionTitle
import com.apptiary.streamtracktv.ui.components.ShareBar
import com.apptiary.streamtracktv.ui.formatCount
import com.apptiary.streamtracktv.ui.formatDuration
import com.apptiary.streamtracktv.ui.formatHourRange
import com.apptiary.streamtracktv.ui.formatMoment
import com.apptiary.streamtracktv.ui.theme.LocalPalette
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val TOP_APPS = 5

@Composable
fun DashboardScreen(
    model: DashboardModel,
    catalog: PackageManagerAppCatalog,
    onOpenSetup: () -> Unit,
    onSyncNow: () -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp), contentPadding = PaddingValues(bottom = 48.dp)) {
        item {
            Text(
                stringResource(R.string.today_title),
                style = MaterialTheme.typography.displayMedium,
                color = LocalPalette.current.ink,
            )
            Caption(model.today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)))
        }
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
        item { SyncCard(model, onSyncNow) }
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
    FocusSurface(onClick = null, modifier = modifier.fillMaxHeight()) {
        Caption(title)
        Text(value, style = MaterialTheme.typography.headlineMedium, color = palette.ink)
        detail?.takeIf { it.isNotEmpty() }?.let { Caption(it) }
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
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app.packageName, model.label(app.packageName), catalog)
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        model.label(app.packageName),
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.ink,
                    )
                    Spacer(Modifier.height(6.dp))
                    ShareBar(app.foregroundMs.toFloat() / longest)
                }
                Spacer(Modifier.width(24.dp))
                Text(formatDuration(app.foregroundMs), style = MaterialTheme.typography.bodyLarge, color = palette.ink)
            }
        }
    }
}

@Composable
private fun HourlyActivity(screenOnByHour: List<Long>, windows: List<HourRange>) {
    val palette = LocalPalette.current
    val description = windows.joinToString { formatHourRange(it) }
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
                    color = if (active) palette.accent else palette.borderStrong,
                    topLeft = Offset(hour * (barWidth + gap), size.height - height.coerceAtLeast(2f)),
                    size = Size(barWidth, height.coerceAtLeast(2f)),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("00", "06", "12", "18", "24").forEach { Caption(it) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (windows.isEmpty()) {
                stringResource(
                    R.string.today_no_windows,
                )
            } else {
                stringResource(R.string.today_windows, description)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = palette.ink,
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
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                color = palette.ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                "$stateLabel · ${stringResource(
                    R.string.goal_progress,
                    formatDuration(status.usedMs),
                    formatDuration(status.goal.dailyLimitMs),
                )}",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.ink,
            )
        }
        Spacer(Modifier.height(8.dp))
        ShareBar(status.progress.toFloat())
    }
}

@Composable
private fun Insights(model: DashboardModel) {
    val palette = LocalPalette.current
    FocusSurface(onClick = null, modifier = Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.today_insights))
        if (model.binges.isEmpty() && model.idle.isEmpty()) Caption(stringResource(R.string.today_no_insights))
        val dayFormat = DateTimeFormatter.ofPattern("EEE HH:00")
        model.binges.forEach { binge ->
            val start =
                binge.start.day
                    .atTime(binge.start.hour, 0)
                    .format(dayFormat)
            Text(
                pluralStringResource(R.plurals.today_binge, binge.hours, model.label(binge.packageName), binge.hours, start),
                style = MaterialTheme.typography.bodyLarge,
                color = palette.ink,
            )
        }
        model.idle.forEach { idle ->
            val start =
                idle.start.day
                    .atTime(idle.start.hour, 0)
                    .format(dayFormat)
            Text(
                pluralStringResource(R.plurals.today_idle, idle.hours, idle.hours, start),
                style = MaterialTheme.typography.bodyLarge,
                color = palette.ink,
            )
        }
    }
}

@Composable
private fun SyncCard(model: DashboardModel, onSyncNow: () -> Unit) {
    val palette = LocalPalette.current
    val zone = ZoneId.systemDefault()
    Column(Modifier.fillMaxWidth()) {
        FocusSurface(onClick = null, modifier = Modifier.fillMaxWidth()) {
            SectionTitle(stringResource(R.string.today_sync))
            val last = model.lastSync
            val lastLine =
                when {
                    last == null -> {
                        stringResource(R.string.sync_never)
                    }

                    last.ok -> {
                        stringResource(R.string.sync_last, formatMoment(last.atMs, model.today, zone))
                    }

                    else -> {
                        stringResource(
                            R.string.sync_last_failed,
                            formatMoment(last.atMs, model.today, zone),
                            last.message.orEmpty(),
                        )
                    }
                }
            Text(lastLine, style = MaterialTheme.typography.bodyLarge, color = palette.ink)
            val next = model.nextSyncMs
            Text(
                if (next ==
                    null
                ) {
                    stringResource(R.string.sync_next_unknown)
                } else {
                    stringResource(R.string.sync_next, formatMoment(next, model.today, zone))
                },
                style = MaterialTheme.typography.bodyLarge,
                color = palette.text,
            )
            Text(
                pluralStringResource(R.plurals.sync_pending, model.pendingRows, model.pendingRows),
                style = MaterialTheme.typography.bodyLarge,
                color = palette.text,
            )
        }
        Spacer(Modifier.height(16.dp))
        PillButton(stringResource(R.string.sync_now), onSyncNow, primary = true)
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
