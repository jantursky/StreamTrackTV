package com.jantursky.streamtracktv.sync

import com.jantursky.streamtracktv.core.model.SecurityEventType
import com.jantursky.streamtracktv.core.pairing.ServerUrl
import com.jantursky.streamtracktv.core.security.SecurityRecorder
import com.jantursky.streamtracktv.core.wire.PinResetPollResponseDto
import com.jantursky.streamtracktv.core.wire.WireLimits
import com.jantursky.streamtracktv.data.credentials.CredentialStore
import com.jantursky.streamtracktv.data.pin.PinStore
import com.jantursky.streamtracktv.data.settings.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed interface PinResetState {
    data object Requesting : PinResetState

    /** Shown on the TV until the owner confirms [code] on the web, or it expires. */
    data class ShowingCode(val code: String, val link: String, val expiresAtMs: Long) : PinResetState

    /** The owner confirmed; the PIN is already cleared on this TV. */
    data object Cleared : PinResetState

    data class Failed(val reason: PinResetFailure) : PinResetState
}

enum class PinResetFailure {
    /** No server address or device token, or the server no longer knows this TV. */
    NOT_PAIRED,
    NETWORK,
    RATE_LIMITED,
    SERVER,
    EXPIRED,
}

/**
 * "Forgot PIN" as a flow of states: ask the server for a code, poll until the owner confirms it on the web,
 * then clear the PIN. Any other outcome leaves the PIN as it was. The code's lifetime is measured on the TV's
 * own clock from the moment it arrived.
 */
class PinResetSession(
    private val api: StreamTrackApi,
    private val settings: SettingsRepository,
    private val credentials: CredentialStore,
    private val pin: PinStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
    private val recorder: SecurityRecorder = SecurityRecorder.None,
) {
    fun reset(): Flow<PinResetState> =
        flow {
            val server = settings.current().serverUrl?.let { ServerUrl.parse(it, allowHttp = true) }
            val token = credentials.token()
            if (server == null || token == null) {
                emit(PinResetState.Failed(PinResetFailure.NOT_PAIRED))
                return@flow
            }
            emit(PinResetState.Requesting)
            val issued =
                when (val result = api.startPinReset(server, token)) {
                    is ApiResult.Ok -> result.value
                    is ApiResult.Failed -> return@flow emit(PinResetState.Failed(result.failure.toFailure()))
                }
            val expiresAtMs = now() + CODE_TTL_MS
            emit(PinResetState.ShowingCode(issued.code, server.pinResetLink(issued.code), expiresAtMs))
            emit(poll(server, token, issued.pollToken, expiresAtMs))
        }

    private suspend fun poll(server: ServerUrl, token: String, pollToken: String, expiresAtMs: Long): PinResetState {
        var errors = 0
        var waitMs = pollIntervalMs
        while (now() < expiresAtMs) {
            delay(waitMs)
            val result = api.pollPinReset(server, token, pollToken)
            val failure = (result as? ApiResult.Failed)?.failure
            val outcome =
                when {
                    result is ApiResult.Ok -> {
                        errors = 0
                        waitMs = pollIntervalMs
                        settle(result.value)
                    }

                    failure != null && !failure.isTransient() -> {
                        PinResetState.Failed(failure.toFailure())
                    }

                    else -> {
                        errors++
                        waitMs = failure?.retryDelayMs(pollIntervalMs) ?: pollIntervalMs
                        failure?.takeIf { errors >= MAX_TRANSIENT_ERRORS }?.let { PinResetState.Failed(it.toFailure()) }
                    }
                }
            if (outcome != null) return outcome
        }
        return PinResetState.Failed(PinResetFailure.EXPIRED)
    }

    /** The end state for a poll answer, or null to keep polling. */
    private suspend fun settle(answer: PinResetPollResponseDto): PinResetState? =
        when (answer) {
            PinResetPollResponseDto.Pending -> {
                null
            }

            PinResetPollResponseDto.Expired -> {
                PinResetState.Failed(PinResetFailure.EXPIRED)
            }

            PinResetPollResponseDto.Confirmed -> {
                pin.clear()
                recorder.record(SecurityEventType.PIN_RESET_CONFIRMED)
                PinResetState.Cleared
            }
        }

    companion object {
        /** The server allows about twice this rate for a code's 600 s lifetime. */
        const val DEFAULT_POLL_INTERVAL_MS = 5_000L
        private const val MAX_TRANSIENT_ERRORS = 5
        private const val MS_PER_SECOND = 1_000L
        private const val NOT_FOUND = 404
        private const val CODE_TTL_MS = WireLimits.PIN_RESET_TTL_SECONDS * MS_PER_SECOND

        private fun ApiFailure.isTransient(): Boolean =
            this is ApiFailure.Network ||
                this is ApiFailure.ServerError ||
                this is ApiFailure.RateLimited ||
                this is ApiFailure.BadResponse

        private fun ApiFailure.retryDelayMs(default: Long): Long =
            ((this as? ApiFailure.RateLimited)?.retryAfterSeconds?.times(MS_PER_SECOND) ?: default).coerceAtLeast(default)

        private fun ApiFailure.toFailure(): PinResetFailure =
            when (this) {
                is ApiFailure.Network -> PinResetFailure.NETWORK

                is ApiFailure.RateLimited -> PinResetFailure.RATE_LIMITED

                is ApiFailure.ServerError, is ApiFailure.BadResponse -> PinResetFailure.SERVER

                ApiFailure.Unauthorized -> PinResetFailure.NOT_PAIRED

                // The server forgets poll tokens once they expire.
                is ApiFailure.Rejected -> if (status == NOT_FOUND) PinResetFailure.EXPIRED else PinResetFailure.SERVER
            }
    }
}
