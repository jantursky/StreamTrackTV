package com.jantursky.streamtracktv.ui.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jantursky.streamtracktv.core.model.SecurityEventType
import com.jantursky.streamtracktv.sync.PinResetFailure
import com.jantursky.streamtracktv.sync.PinResetState
import com.jantursky.streamtracktv.ui.R
import com.jantursky.streamtracktv.ui.UiGraph
import com.jantursky.streamtracktv.ui.components.Caption
import com.jantursky.streamtracktv.ui.components.PillButton
import com.jantursky.streamtracktv.ui.components.QrCodeImage
import com.jantursky.streamtracktv.ui.theme.LocalPalette
import com.jantursky.streamtracktv.ui.theme.SafeArea

const val FORGOT_PIN_TAG = "forgot-pin"
const val RESET_CANCEL_TAG = "pin-reset-cancel"
const val RESET_RETRY_TAG = "pin-reset-retry"
const val RESET_CODE_TAG = "pin-reset-code"
const val PIN_CANCEL_TAG = "pin-cancel"

/** The screen in front of the whole app while a PIN is set and the app is locked. */
@Composable
fun LockRoute(graph: UiGraph) {
    val viewModel = viewModel { LockViewModel(graph.pinLock.store, graph.pinLock.session, graph.pinLock.recorder) }
    LaunchedEffect(Unit) { graph.pinLock.recorder.record(SecurityEventType.LOCK_SHOWN) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var forgot by rememberSaveable { mutableStateOf(false) }
    if (forgot) {
        BackHandler { forgot = false }
        ForgotPinRoute(graph, onCancel = { forgot = false })
    } else {
        LockScreen(state, viewModel::onDigit, viewModel::onDelete, viewModel::onSubmit) { forgot = true }
    }
}

@Composable
fun LockScreen(
    state: LockUiState,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit,
    onForgot: () -> Unit,
) {
    val message =
        when {
            state.waitMs > 0 -> stringResource(R.string.pin_wait, formatWait(state.waitMs))
            state.wrong -> stringResource(R.string.pin_wrong)
            else -> null
        }
    PinEntryScreen(
        title = stringResource(R.string.pin_lock_title),
        hint = stringResource(R.string.pin_lock_hint),
        entered = state.entered,
        message = message,
        enabled = state.waitMs <= 0,
        onDigit = onDigit,
        onDelete = onDelete,
        onSubmit = onSubmit,
    ) {
        PillButton(stringResource(R.string.pin_forgot), onForgot, modifier = Modifier.testTag(FORGOT_PIN_TAG))
    }
}

/** Turn the PIN on, change it or turn it off, from Settings. Calls [onClose] when it ends or is cancelled. */
@Composable
fun PinFlowRoute(mode: PinFlowMode, graph: UiGraph, onClose: () -> Unit) {
    val viewModel =
        viewModel(key = mode.name) { PinFlowViewModel(mode, graph.pinLock.store, graph.pinLock.session, graph.pinLock.recorder) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)
    LaunchedEffect(state.done) { if (state.done) onClose() }
    PinFlowScreen(state, viewModel::onDigit, viewModel::onDelete, viewModel::onSubmit, onClose)
}

@Composable
fun PinFlowScreen(
    state: PinFlowState,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    val title =
        when (state.step) {
            PinStep.CURRENT -> R.string.pin_flow_current
            PinStep.NEW -> R.string.pin_flow_new
            PinStep.REPEAT -> R.string.pin_flow_repeat
        }
    val message =
        when (state.error) {
            PinFlowError.WRONG -> stringResource(R.string.pin_wrong)
            PinFlowError.TOO_SHORT -> stringResource(R.string.pin_too_short)
            PinFlowError.MISMATCH -> stringResource(R.string.pin_mismatch)
            PinFlowError.WAIT -> stringResource(R.string.pin_wait, formatWait(state.waitMs))
            null -> null
        }
    PinEntryScreen(
        title = stringResource(title),
        hint = if (state.step == PinStep.NEW) stringResource(R.string.pin_flow_hint) else null,
        entered = state.entered,
        message = message,
        enabled = true,
        onDigit = onDigit,
        onDelete = onDelete,
        onSubmit = onSubmit,
    ) {
        PillButton(stringResource(R.string.confirm_cancel), onCancel, modifier = Modifier.testTag(PIN_CANCEL_TAG))
    }
}

@Composable
fun ForgotPinRoute(graph: UiGraph, onCancel: () -> Unit) {
    val viewModel = viewModel { ForgotPinViewModel(graph) }
    LaunchedEffect(Unit) { graph.pinLock.recorder.record(SecurityEventType.FORGOT_PIN_OPENED) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ForgotPinScreen(state, viewModel::retry, onCancel)
}

@Composable
fun ForgotPinScreen(state: PinResetState, onRetry: () -> Unit, onCancel: () -> Unit) {
    val palette = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(SafeArea),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.pin_reset_title), style = MaterialTheme.typography.displayMedium, color = palette.ink)
        Spacer(Modifier.height(16.dp))
        when (state) {
            PinResetState.Requesting, PinResetState.Cleared -> {
                Text(stringResource(R.string.pin_reset_requesting), style = MaterialTheme.typography.bodyLarge, color = palette.text)
            }

            is PinResetState.ShowingCode -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QrCodeImage(state.link, 240.dp, stringResource(R.string.pin_reset_qr_description, state.code))
                    Spacer(Modifier.width(56.dp))
                    Column {
                        Text(
                            stringResource(R.string.pin_reset_body),
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.text,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            state.code,
                            style = MaterialTheme.typography.displayMedium,
                            color = palette.ink,
                            modifier = Modifier.testTag(RESET_CODE_TAG),
                        )
                        Spacer(Modifier.height(8.dp))
                        Caption(stringResource(R.string.pin_reset_waiting))
                    }
                }
            }

            is PinResetState.Failed -> {
                Text(
                    stringResource(state.reason.message()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.text,
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state is PinResetState.Failed && state.reason != PinResetFailure.NOT_PAIRED) {
                PillButton(stringResource(R.string.pair_retry), onRetry, modifier = Modifier.testTag(RESET_RETRY_TAG), primary = true)
            }
            PillButton(stringResource(R.string.confirm_cancel), onCancel, modifier = Modifier.testTag(RESET_CANCEL_TAG))
        }
    }
}

private fun PinResetFailure.message(): Int =
    when (this) {
        PinResetFailure.NOT_PAIRED -> R.string.pin_reset_failed_unpaired
        PinResetFailure.NETWORK -> R.string.pin_reset_failed_network
        PinResetFailure.RATE_LIMITED -> R.string.pin_reset_failed_rate_limited
        PinResetFailure.SERVER -> R.string.pin_reset_failed_server
        PinResetFailure.EXPIRED -> R.string.pin_reset_failed_expired
    }
