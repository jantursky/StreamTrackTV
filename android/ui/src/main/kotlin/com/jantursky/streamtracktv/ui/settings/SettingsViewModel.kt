package com.jantursky.streamtracktv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jantursky.streamtracktv.core.model.SyncInterval
import com.jantursky.streamtracktv.data.settings.Settings
import com.jantursky.streamtracktv.ui.UiGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One app in the exclusion and limit list. */
data class AppEntry(val packageName: String, val label: String, val excluded: Boolean, val limitMinutes: Int?)

data class SettingsModel(val settings: Settings, val apps: List<AppEntry>, val pinEnabled: Boolean = false)

class SettingsViewModel(private val graph: UiGraph, ownPackage: String) : ViewModel() {
    private val launchable = flow { emit(graph.catalog.launchablePackages() - ownPackage) }.flowOn(Dispatchers.IO)

    val model: StateFlow<SettingsModel?> =
        combine(
            graph.settings.settings,
            graph.usage.observePackages(),
            graph.usage.observeLabels(),
            launchable,
            graph.pinLock.store.isEnabled,
        ) { settings, recorded, labels, installed, pinEnabled ->
            val packages = (recorded + installed + settings.excludedPackages).toSortedSet()
            val apps =
                packages
                    .map { pkg ->
                        AppEntry(
                            pkg,
                            labels[pkg] ?: graph.catalog.label(pkg) ?: pkg,
                            pkg in settings.excludedPackages,
                            settings.appLimitsMinutes[pkg],
                        )
                    }.sortedBy { it.label.lowercase() }
            SettingsModel(settings, apps, pinEnabled)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun cycleInterval(current: Int) = launch { graph.settings.setSyncInterval(SyncInterval.next(current)) }

    /** Limits are typed exactly on the entry screen; null removes the limit. */
    fun setScreenLimit(minutes: Int?) = launch { graph.settings.setScreenLimit(minutes) }

    fun setScreenLimitWeekend(minutes: Int?) = launch { graph.settings.setScreenLimitWeekend(minutes) }

    fun setAppLimit(packageName: String, minutes: Int?) = launch { graph.settings.setAppLimit(packageName, minutes) }

    fun setAppLimitWeekend(packageName: String, minutes: Int?) =
        launch { graph.settings.setAppLimitWeekend(packageName, minutes) }

    /** Both bounds together; null for either clears the window. */
    fun setBedtime(startMinute: Int?, endMinute: Int?) = launch { graph.settings.setBedtime(startMinute, endMinute) }

    fun setRemindersEnabled(enabled: Boolean) = launch { graph.settings.setRemindersEnabled(enabled) }

    fun toggleTheme(light: Boolean) = launch { graph.settings.setLightTheme(!light) }

    fun toggleExcluded(app: AppEntry) =
        launch {
            val exclude = !app.excluded
            graph.settings.setExcluded(app.packageName, exclude)
            if (exclude) {
                graph.settings.setAppLimit(app.packageName, null)
                graph.usage.deletePackage(app.packageName)
            }
        }

    /** Forgets the device token; the root then shows pairing with the server address filled in. */
    fun unpair() = launch { graph.credentials.clear() }

    fun deleteLocalData() = launch { graph.usage.deleteAll() }

    fun loadDemoWeek() = launch { graph.loadDemoWeek?.invoke() }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
