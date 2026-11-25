package com.jantursky.streamtracktv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jantursky.streamtracktv.collector.PackageManagerAppCatalog
import com.jantursky.streamtracktv.data.settings.Settings
import com.jantursky.streamtracktv.ui.R
import com.jantursky.streamtracktv.ui.UiGraph
import com.jantursky.streamtracktv.ui.components.AppIcon
import com.jantursky.streamtracktv.ui.components.Caption
import com.jantursky.streamtracktv.ui.components.ConfirmDialog
import com.jantursky.streamtracktv.ui.components.OptionRow
import com.jantursky.streamtracktv.ui.components.PillButton
import com.jantursky.streamtracktv.ui.formatDuration
import com.jantursky.streamtracktv.ui.lock.PinFlowMode
import com.jantursky.streamtracktv.ui.lock.PinFlowRoute
import com.jantursky.streamtracktv.ui.theme.LocalPalette

private enum class Pending { UNPAIR, CHANGE_SERVER, DELETE }

/** What the settings list can ask for; the route maps these to the view model. */
class SettingsActions(
    val cycleInterval: (Int) -> Unit,
    val editScreenLimit: () -> Unit,
    val editBedtime: () -> Unit,
    val setReminders: (Boolean) -> Unit,
    val toggleTheme: (Boolean) -> Unit,
    val openApps: () -> Unit,
    val unpair: () -> Unit,
    val deleteLocalData: () -> Unit,
    val loadDemoWeek: (() -> Unit)?,
    val openPin: (PinFlowMode) -> Unit = {},
)

/** The entry screen before [edit] in the same flow, or null for the list it was opened from. */
private fun previousEdit(edit: SettingsEdit): SettingsEdit? =
    when (edit) {
        is SettingsEdit.ScreenLimit -> if (edit.weekend) SettingsEdit.ScreenLimit(false) else null
        is SettingsEdit.AppLimit -> if (edit.weekend) SettingsEdit.AppLimit(edit.app, false) else null
        is SettingsEdit.Bedtime -> if (edit.editingEnd) SettingsEdit.Bedtime(false, edit.start) else null
    }

@Composable
fun SettingsRoute(viewModel: SettingsViewModel, graph: UiGraph, demoAvailable: Boolean) {
    val model by viewModel.model.collectAsStateWithLifecycle()
    val current = model ?: return
    var showApps by rememberSaveable { mutableStateOf(false) }
    var pinMode by rememberSaveable { mutableStateOf<PinFlowMode?>(null) }
    var edit by remember { mutableStateOf<SettingsEdit?>(null) }
    val activePin = pinMode
    val activeEdit = edit
    if (activePin != null) {
        PinFlowRoute(activePin, graph) { pinMode = null }
    } else if (activeEdit != null) {
        BackHandler { edit = previousEdit(activeEdit) }
        SettingsEditScreen(activeEdit, current.settings, viewModel) { edit = it }
    } else if (showApps) {
        BackHandler { showApps = false }
        AppsScreen(current.apps, graph.catalog, viewModel::toggleExcluded, { edit = SettingsEdit.AppLimit(it, false) }) {
            showApps = false
        }
    } else {
        SettingsScreen(
            current,
            SettingsActions(
                cycleInterval = viewModel::cycleInterval,
                editScreenLimit = { edit = SettingsEdit.ScreenLimit(false) },
                editBedtime = { edit = SettingsEdit.Bedtime(false, null) },
                setReminders = viewModel::setRemindersEnabled,
                toggleTheme = viewModel::toggleTheme,
                openApps = { showApps = true },
                unpair = viewModel::unpair,
                deleteLocalData = viewModel::deleteLocalData,
                loadDemoWeek = if (demoAvailable) viewModel::loadDemoWeek else null,
                openPin = { pinMode = it },
            ),
        )
    }
}

/**
 * One typed value. [show] moves to another entry screen, or to the list it came from when given null.
 * Saving a value or going back returns to the screen before this one.
 */
@Composable
private fun SettingsEditScreen(edit: SettingsEdit, settings: Settings, viewModel: SettingsViewModel, show: (SettingsEdit?) -> Unit) {
    val back = { show(previousEdit(edit)) }
    when (edit) {
        is SettingsEdit.ScreenLimit -> {
            MinutesEntryScreen(
                title = stringResource(if (edit.weekend) R.string.settings_screen_limit_weekend else R.string.settings_screen_limit),
                kind = EntryKind.MINUTES,
                current = if (edit.weekend) settings.screenLimitWeekendMinutes else settings.screenLimitMinutes,
                onSave = { minutes ->
                    if (edit.weekend) viewModel.setScreenLimitWeekend(minutes) else viewModel.setScreenLimit(minutes)
                    back()
                },
                onBack = back,
                weekendRow = if (edit.weekend) null else ({ show(SettingsEdit.ScreenLimit(true)) }),
            )
        }

        is SettingsEdit.AppLimit -> {
            val pkg = edit.app.packageName
            MinutesEntryScreen(
                title =
                    stringResource(
                        if (edit.weekend) R.string.entry_app_limit_weekend else R.string.entry_app_limit,
                        edit.app.label,
                    ),
                kind = EntryKind.MINUTES,
                current = if (edit.weekend) settings.appLimitsWeekendMinutes[pkg] else settings.appLimitsMinutes[pkg],
                onSave = { minutes ->
                    if (edit.weekend) viewModel.setAppLimitWeekend(pkg, minutes) else viewModel.setAppLimit(pkg, minutes)
                    back()
                },
                onBack = back,
                weekendRow = if (edit.weekend) null else ({ show(SettingsEdit.AppLimit(edit.app, true)) }),
            )
        }

        is SettingsEdit.Bedtime -> {
            MinutesEntryScreen(
                title = stringResource(if (edit.editingEnd) R.string.entry_bedtime_end else R.string.entry_bedtime_start),
                kind = EntryKind.CLOCK,
                current = if (edit.editingEnd) settings.bedtimeEndMinute else settings.bedtimeStartMinute,
                onSave = { time ->
                    when {
                        time == null -> {
                            viewModel.setBedtime(null, null)
                            show(null)
                        }

                        !edit.editingEnd -> {
                            show(SettingsEdit.Bedtime(true, time))
                        }

                        else -> {
                            viewModel.setBedtime(edit.start, time)
                            show(null)
                        }
                    }
                },
                onBack = back,
            )
        }
    }
}

@Composable
fun SettingsScreen(model: SettingsModel, actions: SettingsActions) {
    val settings = model.settings
    var pending by rememberSaveable { mutableStateOf<Pending?>(null) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 48.dp)) {
        item {
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.displayMedium,
                color = LocalPalette.current.ink,
            )
            Spacer(Modifier.height(8.dp))
        }
        item {
            OptionRow(
                stringResource(R.string.settings_interval),
                formatDuration(settings.syncIntervalMinutes * MINUTE_MS),
                { actions.cycleInterval(settings.syncIntervalMinutes) },
                helper = stringResource(R.string.settings_interval_helper),
            )
        }
        item {
            OptionRow(
                stringResource(R.string.settings_screen_limit),
                settings.screenLimitMinutes?.let { formatDuration(it * MINUTE_MS) }
                    ?: stringResource(R.string.settings_off),
                actions.editScreenLimit,
                helper =
                    settings.screenLimitWeekendMinutes?.let {
                        stringResource(R.string.settings_weekend_value_set, formatDuration(it * MINUTE_MS))
                    },
            )
        }
        item {
            val start = settings.bedtimeStartMinute
            val end = settings.bedtimeEndMinute
            OptionRow(
                stringResource(R.string.settings_bedtime),
                if (start != null && end != null) {
                    stringResource(R.string.settings_bedtime_value, formatClock(start), formatClock(end))
                } else {
                    stringResource(R.string.settings_off)
                },
                actions.editBedtime,
                helper = stringResource(R.string.settings_bedtime_helper),
            )
        }
        item {
            OptionRow(
                stringResource(R.string.settings_reminders),
                stringResource(if (settings.remindersEnabled) R.string.settings_on else R.string.settings_off),
                { actions.setReminders(!settings.remindersEnabled) },
                helper = stringResource(R.string.settings_reminders_helper),
            )
        }
        item {
            OptionRow(
                stringResource(R.string.settings_apps),
                pluralStringResource(R.plurals.settings_apps_value, settings.excludedPackages.size, settings.excludedPackages.size),
                actions.openApps,
                helper = stringResource(R.string.settings_apps_helper),
            )
        }
        item {
            OptionRow(
                stringResource(R.string.settings_theme),
                stringResource(
                    if (settings.lightTheme) R.string.settings_theme_light else R.string.settings_theme_dark,
                ),
                { actions.toggleTheme(settings.lightTheme) },
            )
        }
        pinRows(model.pinEnabled, actions.openPin)
        item {
            OptionRow(
                stringResource(R.string.settings_server),
                settings.serverUrl.orEmpty(),
                { pending = Pending.CHANGE_SERVER },
                helper = stringResource(R.string.settings_server_helper),
            )
        }
        item {
            OptionRow(stringResource(R.string.settings_unpair), "", {
                pending = Pending.UNPAIR
            }, helper = stringResource(R.string.settings_unpair_helper))
        }
        item {
            OptionRow(stringResource(R.string.settings_delete), "", {
                pending = Pending.DELETE
            }, helper = stringResource(R.string.settings_delete_helper))
        }
        actions.loadDemoWeek?.let { load ->
            item {
                OptionRow(
                    stringResource(R.string.settings_demo),
                    stringResource(R.string.settings_run),
                    load,
                    helper = stringResource(R.string.settings_demo_helper),
                )
            }
        }
    }
    val dismiss = { pending = null }
    when (pending) {
        Pending.UNPAIR -> {
            Confirm(R.string.confirm_unpair_title, stringResource(R.string.confirm_unpair_body), actions.unpair, dismiss)
        }

        Pending.CHANGE_SERVER -> {
            val body = stringResource(R.string.confirm_server_body, settings.serverUrl.orEmpty())
            Confirm(R.string.confirm_server_title, body, actions.unpair, dismiss)
        }

        Pending.DELETE -> {
            Confirm(R.string.confirm_delete_title, stringResource(R.string.confirm_delete_body), actions.deleteLocalData, dismiss)
        }

        null -> {}
    }
}

/** Turn on, or change and turn off, depending on whether a PIN is set. */
private fun LazyListScope.pinRows(enabled: Boolean, open: (PinFlowMode) -> Unit) {
    if (enabled) {
        item {
            OptionRow(
                stringResource(R.string.settings_pin),
                stringResource(R.string.settings_pin_on),
                { open(PinFlowMode.CHANGE) },
                helper = stringResource(R.string.settings_pin_change),
            )
        }
        item { OptionRow(stringResource(R.string.settings_pin_turn_off), "", { open(PinFlowMode.DISABLE) }) }
    } else {
        item {
            OptionRow(
                stringResource(R.string.settings_pin),
                stringResource(R.string.settings_off),
                { open(PinFlowMode.SET) },
                helper = stringResource(R.string.settings_pin_helper),
            )
        }
    }
}

@Composable
private fun Confirm(title: Int, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(title),
        message = message,
        confirmLabel = stringResource(R.string.confirm_yes),
        cancelLabel = stringResource(R.string.confirm_cancel),
        onConfirm = {
            onDismiss()
            onConfirm()
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun AppsScreen(
    apps: List<AppEntry>,
    catalog: PackageManagerAppCatalog,
    onToggleExcluded: (AppEntry) -> Unit,
    onEditLimit: (AppEntry) -> Unit,
    onBack: () -> Unit,
) {
    val palette = LocalPalette.current
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 48.dp)) {
        item {
            Text(
                stringResource(R.string.apps_title),
                style = MaterialTheme.typography.displayMedium,
                color = palette.ink,
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.apps_body), style = MaterialTheme.typography.bodyLarge, color = palette.text)
            Spacer(Modifier.height(16.dp))
            PillButton(stringResource(R.string.back), onBack)
        }
        if (apps.isEmpty()) item { Caption(stringResource(R.string.apps_empty)) }
        items(apps, key = { it.packageName }) { app ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app.packageName, app.label, catalog)
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.titleMedium, color = palette.ink)
                    Caption(app.packageName)
                }
                PillButton(stringResource(if (app.excluded) R.string.apps_excluded else R.string.apps_tracked), {
                    onToggleExcluded(app)
                })
                if (!app.excluded) {
                    Spacer(Modifier.width(16.dp))
                    val limit =
                        app.limitMinutes?.let { formatDuration(it * MINUTE_MS) }
                            ?: stringResource(R.string.settings_off)
                    PillButton(stringResource(R.string.apps_limit, limit), { onEditLimit(app) })
                }
            }
        }
    }
}

private const val MINUTE_MS = 60_000L
