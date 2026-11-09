package com.jantursky.streamtracktv.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jantursky.streamtracktv.core.pin.PIN_MAX_LENGTH
import com.jantursky.streamtracktv.ui.components.FocusSurface
import com.jantursky.streamtracktv.ui.theme.LocalPalette
import com.jantursky.streamtracktv.ui.theme.PillShape

fun pinKeyTag(label: String) = "pin-key-$label"

const val PIN_DELETE_TAG = "pin-delete"
const val PIN_SUBMIT_TAG = "pin-submit"
const val PIN_DOTS_TAG = "pin-dots"

/** Number keys of a remote or keyboard, as digits. */
private val DIGIT_KEYS: Map<Key, Char> =
    mapOf(
        Key.Zero to '0',
        Key.One to '1',
        Key.Two to '2',
        Key.Three to '3',
        Key.Four to '4',
        Key.Five to '5',
        Key.Six to '6',
        Key.Seven to '7',
        Key.Eight to '8',
        Key.Nine to '9',
        Key.NumPad0 to '0',
        Key.NumPad1 to '1',
        Key.NumPad2 to '2',
        Key.NumPad3 to '3',
        Key.NumPad4 to '4',
        Key.NumPad5 to '5',
        Key.NumPad6 to '6',
        Key.NumPad7 to '7',
        Key.NumPad8 to '8',
        Key.NumPad9 to '9',
    )

/** One dot per digit entered. Only the count is shown, never the digits. */
@Composable
fun PinDots(count: Int, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Row(
        modifier
            .testTag(PIN_DOTS_TAG)
            .semantics { contentDescription = "$count" },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(PIN_MAX_LENGTH) { index ->
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (index < count) palette.accent else palette.border),
            )
        }
    }
}

/**
 * A 3 by 4 keypad the D-pad walks, plus the number keys of a remote. [onSubmit] is the check mark;
 * [enabled] false (during a wait) turns every key off.
 */
@Composable
fun PinPad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = FocusRequester(),
    deleteLabel: String = "⌫",
    submitLabel: String = "✓",
) {
    val rows =
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
        )
    Column(
        modifier.onPreviewKeyEvent { event ->
            val digit = DIGIT_KEYS[event.key]
            when {
                !enabled || event.type != KeyEventType.KeyDown -> false
                digit != null -> true.also { onDigit(digit) }
                event.key == Key.Backspace || event.key == Key.Delete -> true.also { onDelete() }
                else -> false
            }
        },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEachIndexed { index, label ->
                    val first = rowIndex == 0 && index == 0
                    PinKey(
                        label,
                        enabled,
                        { onDigit(label.single()) },
                        if (first) Modifier.focusRequester(focusRequester) else Modifier,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PinKey(deleteLabel, enabled, onDelete, Modifier.testTag(PIN_DELETE_TAG), tag = null)
            PinKey("0", enabled, { onDigit('0') })
            PinKey(submitLabel, enabled, onSubmit, Modifier.testTag(PIN_SUBMIT_TAG), tag = null)
        }
    }
}

@Composable
private fun PinKey(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, tag: String? = label) {
    val palette = LocalPalette.current
    FocusSurface(
        onClick = if (enabled) onClick else null,
        modifier = modifier.width(88.dp).height(64.dp).then(if (tag != null) Modifier.testTag(pinKeyTag(tag)) else Modifier),
        shape = PillShape,
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(Modifier.width(88.dp).height(64.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.headlineSmall,
                color = if (enabled) palette.ink else palette.text,
            )
        }
    }
}
