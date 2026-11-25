package com.jantursky.streamtracktv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jantursky.streamtracktv.ui.R
import com.jantursky.streamtracktv.ui.components.Caption
import com.jantursky.streamtracktv.ui.components.PillButton
import com.jantursky.streamtracktv.ui.lock.DigitPad
import com.jantursky.streamtracktv.ui.theme.LocalPalette

const val ENTRY_TITLE_TAG = "entry-title"
const val ENTRY_VALUE_TAG = "entry-value"
const val ENTRY_ERROR_TAG = "entry-error"
const val ENTRY_REMOVE_TAG = "entry-remove"
const val ENTRY_WEEKEND_TAG = "entry-weekend"

/** Largest daily limit in minutes, one full day. */
const val MAX_LIMIT_MINUTES = 1440

/** Both kinds take at most four digits: 1440 minutes and HHMM. */
private const val MAX_DIGITS = 4

/** What a typed value means: a number of minutes, or a clock time typed as HHMM. */
enum class EntryKind { MINUTES, CLOCK }

/**
 * The settings entry screens, one at a time: the daily screen limit, an app limit, and the two bedtime edges.
 * [weekend] is true for the Saturday and Sunday value of a limit.
 */
sealed interface SettingsEdit {
    data class ScreenLimit(val weekend: Boolean) : SettingsEdit

    data class AppLimit(val app: AppEntry, val weekend: Boolean) : SettingsEdit

    /** Bedtime is entered as two times: the start, then the end, which is saved with the start. */
    data class Bedtime(val editingEnd: Boolean, val start: Int?) : SettingsEdit
}

/** Minutes since midnight as HH:MM. */
fun formatClock(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

/**
 * Types a value on the D-pad keypad and saves it with the check mark; the Remove key clears it. Values out of
 * range are refused and the old value stays.
 */
@Composable
fun MinutesEntryScreen(
    title: String,
    kind: EntryKind,
    current: Int?,
    onSave: (Int?) -> Unit,
    onBack: () -> Unit,
    weekendRow: (() -> Unit)? = null,
) {
    val palette = LocalPalette.current
    var digits by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val shown =
        when {
            digits.isEmpty() -> current?.let { if (kind == EntryKind.CLOCK) formatClock(it) else "$it" } ?: "-"
            kind == EntryKind.CLOCK && digits.length == 4 -> "${digits.take(2)}:${digits.drop(2)}"
            else -> digits
        }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, style = MaterialTheme.typography.displayMedium, color = palette.ink, modifier = Modifier.testTag(ENTRY_TITLE_TAG))
        Text(shown, style = MaterialTheme.typography.headlineMedium, color = palette.ink, modifier = Modifier.testTag(ENTRY_VALUE_TAG))
        Caption(
            stringResource(if (kind == EntryKind.CLOCK) R.string.entry_clock_helper else R.string.entry_minutes_helper),
        )
        error?.let {
            Text(
                stringResource(it),
                style = MaterialTheme.typography.bodyLarge,
                color = palette.ink,
                modifier = Modifier.testTag(ENTRY_ERROR_TAG),
            )
        }
        DigitPad(
            enabled = true,
            onDigit = { digit ->
                error = null
                if (digits.length < MAX_DIGITS) digits += digit
            },
            onDelete = {
                error = null
                digits = digits.dropLast(1)
            },
            onSubmit = {
                val value = if (kind == EntryKind.CLOCK) parseClock(digits) else parseMinutes(digits)
                if (value != null) {
                    onSave(value)
                } else {
                    error = if (kind == EntryKind.CLOCK) R.string.entry_error_clock else R.string.entry_error_minutes
                }
            },
            focusRequester = focus,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PillButton(stringResource(R.string.entry_remove), { onSave(null) }, Modifier.testTag(ENTRY_REMOVE_TAG))
            weekendRow?.let {
                PillButton(stringResource(R.string.settings_weekend_value), it, Modifier.testTag(ENTRY_WEEKEND_TAG))
            }
            Spacer(Modifier.width(8.dp))
            PillButton(stringResource(R.string.back), onBack)
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** 1 to [MAX_LIMIT_MINUTES], or null. */
internal fun parseMinutes(digits: String): Int? = digits.toIntOrNull()?.takeIf { it in 1..MAX_LIMIT_MINUTES }

/** Four digits HHMM with HH below 24 and MM below 60, as minutes since midnight, or null. */
internal fun parseClock(digits: String): Int? {
    if (digits.length != 4) return null
    val hour = digits.take(2).toInt()
    val minute = digits.drop(2).toInt()
    return if (hour < 24 && minute < 60) hour * 60 + minute else null
}
