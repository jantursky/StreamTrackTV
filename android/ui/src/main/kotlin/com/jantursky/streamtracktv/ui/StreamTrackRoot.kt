package com.jantursky.streamtracktv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jantursky.streamtracktv.ui.about.AboutScreen
import com.jantursky.streamtracktv.ui.components.FocusSurface
import com.jantursky.streamtracktv.ui.dashboard.DashboardScreen
import com.jantursky.streamtracktv.ui.dashboard.DashboardViewModel
import com.jantursky.streamtracktv.ui.history.HistoryRoute
import com.jantursky.streamtracktv.ui.lock.LockRoute
import com.jantursky.streamtracktv.ui.pairing.PairingRoute
import com.jantursky.streamtracktv.ui.pairing.PairingViewModel
import com.jantursky.streamtracktv.ui.privacy.PrivacyScreen
import com.jantursky.streamtracktv.ui.privacy.PrivacyViewModel
import com.jantursky.streamtracktv.ui.settings.SettingsRoute
import com.jantursky.streamtracktv.ui.settings.SettingsViewModel
import com.jantursky.streamtracktv.ui.setup.SetupRoute
import com.jantursky.streamtracktv.ui.theme.LocalPalette
import com.jantursky.streamtracktv.ui.theme.PillShape
import com.jantursky.streamtracktv.ui.theme.SafeArea
import com.jantursky.streamtracktv.ui.theme.StreamTrackTheme

enum class Destination(val label: Int) {
    TODAY(R.string.nav_today),
    HISTORY(R.string.nav_history),
    SETUP(R.string.nav_setup),
    PRIVACY(R.string.nav_privacy),
    SETTINGS(R.string.nav_settings),
    ABOUT(R.string.nav_about),
}

fun navTag(destination: Destination) = "nav-${destination.name.lowercase()}"

/** Pairing until a device token exists, then the main shell. */
@Composable
fun StreamTrackRoot(graph: UiGraph) {
    val settings by graph.settings.settings.collectAsStateWithLifecycle(null)
    val paired by graph.credentials.isPaired.collectAsStateWithLifecycle(null)
    val locked by graph.pinLock.session.locked
        .collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { graph.pinLock.session.appOpened() }
    // Leaving the screen, for whatever reason, puts the lock back.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { graph.pinLock.session.lock() }
    StreamTrackTheme(dark = settings?.lightTheme != true) {
        Box(
            Modifier
                .fillMaxSize()
                .background(LocalPalette.current.canvas),
        ) {
            when {
                locked == null -> Unit
                locked == true -> LockRoute(graph)
                paired == true -> MainShell(graph)
                paired == false -> PairingRoute(viewModel { PairingViewModel(graph) })
            }
        }
    }
}

@Composable
private fun MainShell(graph: UiGraph) {
    var destination by rememberSaveable { mutableStateOf(Destination.TODAY) }
    if (destination != Destination.TODAY) BackHandler { destination = Destination.TODAY }
    NavigationLayout(destination, onNavigate = { destination = it }) {
        when (destination) {
            Destination.TODAY -> {
                TodayRoute(graph) { destination = Destination.SETUP }
            }

            Destination.HISTORY -> {
                HistoryRoute(graph)
            }

            Destination.SETUP -> {
                SetupRoute(graph.permissions)
            }

            Destination.PRIVACY -> {
                val preview by viewModel { PrivacyViewModel(graph) }.preview.collectAsStateWithLifecycle()
                PrivacyScreen(preview)
            }

            Destination.SETTINGS -> {
                val ownPackage = LocalContext.current.packageName
                SettingsRoute(
                    viewModel { SettingsViewModel(graph, ownPackage) },
                    graph,
                    graph.loadDemoWeek != null,
                )
            }

            Destination.ABOUT -> {
                AboutScreen(graph.appInfo)
            }
        }
    }
}

@Composable
private fun TodayRoute(graph: UiGraph, onOpenSetup: () -> Unit) {
    val viewModel = viewModel { DashboardViewModel(graph) }
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val model by viewModel.model.collectAsStateWithLifecycle()
    model?.let { DashboardScreen(it, graph.catalog, onOpenSetup, viewModel::syncNow) }
}

/** Side rail of pill tabs and the content area. Focus starts on the current tab and returns to it. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NavigationLayout(current: Destination, onNavigate: (Destination) -> Unit, content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    val currentTab = remember { FocusRequester() }
    Row(
        Modifier
            .fillMaxSize()
            .padding(SafeArea),
    ) {
        Column(
            Modifier
                .width(220.dp)
                .focusRestorer(currentTab)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = palette.ink)
            Spacer(Modifier.height(24.dp))
            Destination.entries.forEach { destination ->
                val selected = destination == current
                FocusSurface(
                    onClick = { onNavigate(destination) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(navTag(destination))
                            .then(if (selected) Modifier.focusRequester(currentTab) else Modifier),
                    shape = PillShape,
                    color = if (selected) palette.soot else palette.canvas,
                    borderColor = if (selected) palette.soot else palette.border,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text(
                        stringResource(destination.label),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) palette.canvas else palette.ink,
                    )
                }
            }
        }
        Spacer(Modifier.width(32.dp))
        Box(
            Modifier
                .weight(1f)
                .focusGroup(),
        ) { content() }
    }
    LaunchedEffect(Unit) { currentTab.requestFocus() }
}
