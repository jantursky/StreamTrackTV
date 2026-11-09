package com.jantursky.streamtracktv.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jantursky.streamtracktv.ui.theme.LocalPalette
import com.jantursky.streamtracktv.ui.theme.SafeArea

const val PIN_TITLE_TAG = "pin-title"
const val PIN_MESSAGE_TAG = "pin-message"

/** `0:30` for 30 seconds, `2:05` for two minutes and five seconds. Rounded up to whole seconds. */
fun formatWait(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0) + MS_PER_SECOND - 1) / MS_PER_SECOND
    return "${seconds / SECONDS_PER_MINUTE}:${(seconds % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"
}

private const val MS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L

/**
 * The one screen for entering a PIN: lock, set, change and turn off all use it. [message] is an error or a
 * wait; [enabled] is false while a wait runs. [actions] sit under the keypad.
 */
@Composable
fun PinEntryScreen(
    title: String,
    hint: String?,
    entered: String,
    message: String?,
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    val palette = LocalPalette.current
    val firstKey = remember { FocusRequester() }
    // A TV is wide and short (960 by 540 dp at 1080p): text on the left, the keypad beside it.
    Row(
        modifier
            .fillMaxSize()
            .padding(SafeArea),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(64.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.displaySmall,
                color = palette.ink,
                modifier = Modifier.testTag(PIN_TITLE_TAG),
            )
            hint?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyLarge, color = palette.text)
            }
            Spacer(Modifier.height(24.dp))
            PinDots(entered.length)
            Spacer(Modifier.height(12.dp))
            Text(
                message.orEmpty().ifEmpty { " " },
                style = MaterialTheme.typography.bodyLarge,
                color = palette.ink,
                modifier = Modifier.testTag(PIN_MESSAGE_TAG),
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { actions() }
        }
        PinPad(enabled, onDigit, onDelete, onSubmit, focusRequester = firstKey)
    }
    LaunchedEffect(Unit) { firstKey.requestFocus() }
}
