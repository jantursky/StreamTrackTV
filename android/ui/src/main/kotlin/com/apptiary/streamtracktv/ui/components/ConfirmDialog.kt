package com.apptiary.streamtracktv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.apptiary.streamtracktv.ui.theme.CardShape
import com.apptiary.streamtracktv.ui.theme.LocalPalette

/** Confirmation for destructive actions. Focus starts on the safe choice. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    val cancelFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .border(1.dp, palette.border, CardShape)
                .background(palette.surface, CardShape)
                .padding(40.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = palette.ink)
            Spacer(Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge, color = palette.text)
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PillButton(cancelLabel, onDismiss, Modifier.focusRequester(cancelFocus))
                PillButton(confirmLabel, onConfirm, primary = true)
            }
        }
    }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
}
