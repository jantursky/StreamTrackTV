package com.apptiary.streamtracktv.ui.settings

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
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.apptiary.streamtracktv.collector.PackageManagerAppCatalog
import com.apptiary.streamtracktv.ui.R
import com.apptiary.streamtracktv.ui.components.AppIcon
import com.apptiary.streamtracktv.ui.components.Caption
import com.apptiary.streamtracktv.ui.components.ConfirmDialog
import com.apptiary.streamtracktv.ui.components.OptionRow
import com.apptiary.streamtracktv.ui.components.PillButton
import com.apptiary.streamtracktv.ui.formatDuration
import com.apptiary.streamtracktv.ui.theme.LocalPalette

private enum class Pending { UNPAIR, CHANGE_SERVER, DELETE }

/** What the settings list can ask for; the route maps these to the view model. */
class SettingsActions(
    val cycleInterval: (Int) -> Unit,
    val cycleScreenLimit: (Int?) -> Unit,
    val toggleTheme: (Boolean) -> Unit,
    val openApps: () -> Unit,
    val unpair: () -> Unit,
    val deleteLocalData: () -> Unit,
    val loadDemoWeek: (() -> Unit)?,
)

@Composable
fun SettingsRoute(viewModel: SettingsViewModel, catalog: PackageManagerAppCatalog, demoAvailable: Boolean) {
    val model by viewModel.model.collectAsStateWithLifecycle()
    val current = model ?: return
    var showApps by rememberSaveable { mutableStateOf(false) }
    if (showApps) {
        BackHandler { showApps = false }
        AppsScreen(current.apps, catalog, viewModel::toggleExcluded, viewModel::cycleAppLimit) { showApps = false }
    } else {
        SettingsScreen(
            current,
            SettingsActions(
                cycleInterval = viewModel::cycleInterval,
                cycleScreenLimit = viewModel::cycleScreenLimit,
                toggleTheme = viewModel::toggleTheme,
                openApps = { showApps = true },
                unpair = viewModel::unpair,
                deleteLocalData = viewModel::deleteLocalData,
                loadDemoWeek = if (demoAvailable) viewModel::loadDemoWeek else null,
            ),
        )
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
                { actions.cycleScreenLimit(settings.screenLimitMinutes) },
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
    onCycleLimit: (AppEntry) -> Unit,
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
                    PillButton(stringResource(R.string.apps_limit, limit), { onCycleLimit(app) })
                }
            }
        }
    }
}

private const val MINUTE_MS = 60_000L
