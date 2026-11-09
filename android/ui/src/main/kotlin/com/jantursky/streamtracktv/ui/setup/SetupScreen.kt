package com.jantursky.streamtracktv.ui.setup

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jantursky.streamtracktv.collector.AndroidPermissions
import com.jantursky.streamtracktv.collector.Requirement
import com.jantursky.streamtracktv.collector.RequirementStatus
import com.jantursky.streamtracktv.ui.R
import com.jantursky.streamtracktv.ui.components.Caption
import com.jantursky.streamtracktv.ui.components.FocusSurface
import com.jantursky.streamtracktv.ui.components.PillButton
import com.jantursky.streamtracktv.ui.components.QrCodeImage
import com.jantursky.streamtracktv.ui.components.StatusMark
import com.jantursky.streamtracktv.ui.theme.LocalPalette
import com.jantursky.streamtracktv.ui.theme.RoundedIcon

/** Checklist of the three permissions, re-read every time the user comes back from the settings. */
@Composable
fun SetupRoute(permissions: AndroidPermissions) {
    var statuses by remember { mutableStateOf(permissions.statuses()) }
    LifecycleResumeEffect(permissions) {
        statuses = permissions.statuses()
        onPauseOrDispose { }
    }
    val context = LocalContext.current
    SetupScreen(statuses) { status -> openSettings(context, status) }
}

private fun openSettings(context: Context, status: RequirementStatus): Boolean {
    val intent = status.settingsIntent ?: return false
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/** [onOpen] returns false when the settings screen could not be opened; the adb fallback is shown then. */
@Composable
fun SetupScreen(statuses: List<RequirementStatus>, onOpen: (RequirementStatus) -> Boolean) {
    val palette = LocalPalette.current
    LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp), contentPadding = PaddingValues(bottom = 48.dp)) {
        item {
            Text(
                stringResource(R.string.setup_title),
                style = MaterialTheme.typography.displayMedium,
                color = palette.ink,
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.setup_body), style = MaterialTheme.typography.bodyLarge, color = palette.text)
        }
        items(statuses, key = { it.requirement }) { status -> RequirementCard(status, onOpen) }
    }
}

@Composable
private fun RequirementCard(status: RequirementStatus, onOpen: (RequirementStatus) -> Boolean) {
    val palette = LocalPalette.current
    var manual by remember(status.requirement) { mutableStateOf(status.settingsIntent == null) }
    Column(Modifier.fillMaxWidth()) {
        FocusSurface(onClick = null, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(status.requirement.title()),
                        style = MaterialTheme.typography.headlineSmall,
                        color = palette.ink,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(status.requirement.body()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.text,
                    )
                }
                Spacer(Modifier.width(24.dp))
                StatusMark(status.granted, stringResource(R.string.setup_done), stringResource(R.string.setup_needed))
            }
        }
        if (!status.granted && manual) {
            // Outside the focusable card on purpose: a focus target taller than the screen makes the list
            // jump up and down as focus moves, and the QR code could never be read.
            Spacer(Modifier.height(12.dp))
            ManualSteps(status.adbCommand)
        }
        if (!status.granted && status.settingsIntent != null) {
            Spacer(Modifier.height(12.dp))
            PillButton(stringResource(R.string.setup_open), onClick = { manual = !onOpen(status) }, primary = true)
        }
    }
}

/** The adb command beside its QR code, compact enough to sit on screen with its card. */
@Composable
private fun ManualSteps(command: String) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(palette.canvas, RoundedIcon)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Caption(stringResource(R.string.setup_manual))
            Spacer(Modifier.height(8.dp))
            Text(
                command,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = palette.ink,
            )
            Spacer(Modifier.height(8.dp))
            Caption(stringResource(R.string.setup_qr_hint))
        }
        Spacer(Modifier.width(24.dp))
        QrCodeImage(command, QR_SIZE, stringResource(R.string.setup_qr_description))
    }
}

private val QR_SIZE = 160.dp

private fun Requirement.title(): Int =
    when (this) {
        Requirement.USAGE_ACCESS -> R.string.setup_usage_title
        Requirement.ACCESSIBILITY -> R.string.setup_accessibility_title
        Requirement.BATTERY_OPTIMIZATION -> R.string.setup_battery_title
    }

private fun Requirement.body(): Int =
    when (this) {
        Requirement.USAGE_ACCESS -> R.string.setup_usage_body
        Requirement.ACCESSIBILITY -> R.string.setup_accessibility_body
        Requirement.BATTERY_OPTIMIZATION -> R.string.setup_battery_body
    }
