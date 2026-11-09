package com.jantursky.streamtracktv.ui.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jantursky.streamtracktv.sync.PairingFailure
import com.jantursky.streamtracktv.sync.PairingState
import com.jantursky.streamtracktv.ui.R
import com.jantursky.streamtracktv.ui.components.Caption
import com.jantursky.streamtracktv.ui.components.PillButton
import com.jantursky.streamtracktv.ui.components.QrCodeImage
import com.jantursky.streamtracktv.ui.theme.LocalPalette
import com.jantursky.streamtracktv.ui.theme.PillShape
import com.jantursky.streamtracktv.ui.theme.SafeArea
import kotlinx.coroutines.delay

const val SERVER_FIELD_TAG = "server-field"

@Composable
fun PairingRoute(viewModel: PairingViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val current = state) {
        is PairingUiState.EnterServer -> ServerScreen(current, viewModel::onInputChange, viewModel::submit)
        is PairingUiState.Connecting -> CodeScreen(current, viewModel::retry, viewModel::changeServer)
    }
}

@Composable
fun ServerScreen(state: PairingUiState.EnterServer, onInputChange: (String) -> Unit, onSubmit: () -> Unit) {
    val palette = LocalPalette.current
    val fieldFocus = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        Modifier
            .fillMaxSize()
            .padding(SafeArea)
            .widthIn(max = 1100.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.pair_server_title),
            style = MaterialTheme.typography.displayMedium,
            color = palette.ink,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.pair_server_body),
            style = MaterialTheme.typography.bodyLarge,
            color = palette.text,
        )
        Spacer(Modifier.height(40.dp))
        Text(
            stringResource(R.string.pair_server_label),
            style = MaterialTheme.typography.labelLarge,
            color = palette.ink,
        )
        Spacer(Modifier.height(12.dp))
        BasicTextField(
            value = state.input,
            onValueChange = onInputChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.ink),
            cursorBrush = SolidColor(palette.accent),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                    autoCorrectEnabled = false,
                ),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            interactionSource = interaction,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(fieldFocus)
                    .testTag(SERVER_FIELD_TAG),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .border(
                            if (focused) 3.dp else 1.dp,
                            if (focused) palette.accent else palette.borderStrong,
                            PillShape,
                        ).background(palette.surface, PillShape)
                        .padding(horizontal = 28.dp, vertical = 18.dp),
                ) { inner() }
            },
        )
        state.error?.let { error ->
            Spacer(Modifier.height(12.dp))
            val message =
                when (error) {
                    ServerInputError.INVALID -> R.string.pair_server_invalid
                    ServerInputError.HTTPS_REQUIRED -> R.string.pair_server_invalid_https
                }
            Text(stringResource(message), style = MaterialTheme.typography.bodyMedium, color = palette.ink)
        }
        Spacer(Modifier.height(32.dp))
        PillButton(stringResource(R.string.pair_continue), onSubmit, primary = true)
    }
    LaunchedEffect(Unit) { fieldFocus.requestFocus() }
}

@Composable
fun CodeScreen(state: PairingUiState.Connecting, onRetry: () -> Unit, onChangeServer: () -> Unit) {
    val palette = LocalPalette.current
    val actionFocus = remember { FocusRequester() }
    Column(
        Modifier
            .fillMaxSize()
            .padding(SafeArea),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.pair_code_title),
            style = MaterialTheme.typography.displayMedium,
            color = palette.ink,
        )
        Spacer(Modifier.height(24.dp))
        when (val pairing = state.pairing) {
            PairingState.Requesting, PairingState.Paired -> {
                Text(
                    stringResource(R.string.pair_requesting),
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.text,
                )
                Spacer(Modifier.height(32.dp))
                PillButton(
                    stringResource(R.string.pair_change_server),
                    onChangeServer,
                    Modifier.focusRequester(actionFocus),
                )
            }

            is PairingState.ShowingCode -> {
                CodeContent(pairing, state.server.pairPage) {
                    PillButton(
                        stringResource(R.string.pair_change_server),
                        onChangeServer,
                        Modifier.focusRequester(actionFocus),
                    )
                }
            }

            is PairingState.Failed -> {
                Text(
                    stringResource(pairing.reason.message()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.ink,
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PillButton(
                        stringResource(R.string.pair_retry),
                        onRetry,
                        Modifier.focusRequester(actionFocus),
                        primary = true,
                    )
                    PillButton(stringResource(R.string.pair_change_server), onChangeServer)
                }
            }
        }
    }
    LaunchedEffect(state.pairing::class) { actionFocus.requestFocus() }
}

@Composable
private fun CodeContent(code: PairingState.ShowingCode, pairPage: String, action: @Composable () -> Unit) {
    val palette = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        QrCodeImage(code.link, 240.dp, stringResource(R.string.pair_qr_description, code.code))
        Spacer(Modifier.width(56.dp))
        Column {
            Text(
                stringResource(R.string.pair_code_body, pairPage),
                style = MaterialTheme.typography.bodyLarge,
                color = palette.text,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                code.code.toCharArray().joinToString(" "),
                style = MaterialTheme.typography.displayLarge,
                color = palette.ink,
                modifier = Modifier.testTag(CODE_TAG),
            )
            Spacer(Modifier.height(16.dp))
            val remaining by produceState(code.expiresAtMs - System.currentTimeMillis(), code) {
                while (true) {
                    value = code.expiresAtMs - System.currentTimeMillis()
                    delay(TICK_MS)
                }
            }
            Text(
                stringResource(R.string.pair_code_waiting),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.ink,
            )
            Caption(stringResource(R.string.pair_code_expires, formatCountdown(remaining)))
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

const val CODE_TAG = "pair-code"
private const val TICK_MS = 1_000L

/** `9:05` style minutes and seconds, never negative. */
fun formatCountdown(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0) + SECOND_MS - 1) / SECOND_MS
    return "${seconds / SECONDS_PER_MINUTE}:${(seconds % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"
}

private const val SECOND_MS = 1_000L
private const val SECONDS_PER_MINUTE = 60L

private fun PairingFailure.message(): Int =
    when (this) {
        PairingFailure.NETWORK -> R.string.pair_failed_network
        PairingFailure.RATE_LIMITED -> R.string.pair_failed_rate_limited
        PairingFailure.SERVER -> R.string.pair_failed_server
        PairingFailure.REJECTED -> R.string.pair_failed_rejected
        PairingFailure.EXPIRED -> R.string.pair_failed_expired
    }
