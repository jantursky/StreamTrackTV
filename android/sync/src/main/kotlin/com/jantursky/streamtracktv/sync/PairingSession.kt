package com.jantursky.streamtracktv.sync

import com.jantursky.streamtracktv.core.pairing.ServerUrl
import com.jantursky.streamtracktv.core.wire.PairPollResponseDto
import com.jantursky.streamtracktv.core.wire.PairStartRequestDto
import com.jantursky.streamtracktv.core.wire.WireLimits
import com.jantursky.streamtracktv.data.credentials.CredentialStore
import com.jantursky.streamtracktv.data.settings.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

sealed interface PairingState {
    data object Requesting : PairingState

    /** Shown on the TV until the user confirms [code] on the web, or it expires. */
    data class ShowingCode(val code: String, val link: String, val expiresAtMs: Long) : PairingState

    /** The device token is stored; syncing can start. */
    data object Paired : PairingState

    data class Failed(val reason: PairingFailure) : PairingState
}

enum class PairingFailure { NETWORK, RATE_LIMITED, SERVER, REJECTED, EXPIRED }

/**
 * Pairing as a flow of states: request a code, poll until the user claims it, store the token.
 *
 * An expired code is replaced by a new one without user action, at most [MAX_CODES] times in a row, so
 * a TV left on the pairing screen stays below the server's limit of code requests per address. A code's
 * expiry is measured on the TV's own clock from the moment it arrived, so a TV whose clock is off still
 * shows each code for its full lifetime.
 */
class PairingSession(
    private val api: StreamTrackApi,
    private val settings: SettingsRepository,
    private val credentials: CredentialStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
) {
    fun pair(server: ServerUrl, deviceName: String, model: String?): Flow<PairingState> =
        flow {
            val request =
                PairStartRequestDto(
                    deviceName = deviceName.trim().take(WireLimits.MAX_DEVICE_NAME_LENGTH).ifEmpty { "Android TV" },
                    model = model?.trim()?.take(WireLimits.MAX_MODEL_LENGTH)?.ifEmpty { null },
                )
            for (attempt in 0 until MAX_CODES) {
                if (attempt > 0) delay(MIN_CODE_REQUEST_INTERVAL_MS)
                emit(PairingState.Requesting)
                val issued =
                    when (val result = api.startPairing(server, request)) {
                        is ApiResult.Ok -> {
                            result.value
                        }

                        is ApiResult.Failed -> {
                            emit(PairingState.Failed(result.failure.toPairingFailure()))
                            return@flow
                        }
                    }
                val code = issued.response
                val expiresAtMs = now() + lifetimeMs(issued)
                emit(PairingState.ShowingCode(code.code, server.pairLink(code.code), expiresAtMs))
                when (val polled = pollUntilSettled(server, code.pollToken, expiresAtMs)) {
                    is Poll.Claimed -> {
                        settings.setServerUrl(server.value)
                        credentials.save(polled.deviceToken)
                        emit(PairingState.Paired)
                        return@flow
                    }

                    // Loop around for a fresh code.
                    Poll.Expired -> {
                        continue
                    }

                    is Poll.Failed -> {
                        emit(PairingState.Failed(polled.reason))
                        return@flow
                    }
                }
            }
            emit(PairingState.Failed(PairingFailure.EXPIRED))
        }

    private suspend fun pollUntilSettled(server: ServerUrl, pollToken: String, expiresAtMs: Long): Poll {
        var errors = PollErrors()
        var waitMs = pollIntervalMs
        while (now() < expiresAtMs) {
            delay(waitMs)
            val result = api.pollPairing(server, pollToken)
            val failure = (result as? ApiResult.Failed)?.failure
            if (failure != null && failure.isTransient()) {
                errors = errors.after(failure)
                if (errors.exhausted()) return Poll.Failed(failure.toPairingFailure())
                waitMs = (failure as? ApiFailure.RateLimited)?.retryAfterSeconds?.let { it * MS_PER_SECOND }?.coerceAtLeast(pollIntervalMs)
                    ?: pollIntervalMs
            } else {
                errors = PollErrors()
                waitMs = pollIntervalMs
                settle(result)?.let { return it }
            }
        }
        return Poll.Expired
    }

    /** The outcome of a poll answer, or null to keep polling. */
    private fun settle(result: ApiResult<PairPollResponseDto>): Poll? =
        when (result) {
            is ApiResult.Ok -> {
                when (val response = result.value) {
                    PairPollResponseDto.Pending -> null
                    PairPollResponseDto.Expired -> Poll.Expired
                    is PairPollResponseDto.Claimed -> Poll.Claimed(response.deviceToken)
                }
            }

            // The server forgets poll tokens once they expire.
            is ApiResult.Failed -> {
                val forgotten = (result.failure as? ApiFailure.Rejected)?.status == NOT_FOUND
                if (forgotten) Poll.Expired else Poll.Failed(PairingFailure.REJECTED)
            }
        }

    private sealed interface Poll {
        data class Claimed(val deviceToken: String) : Poll

        data object Expired : Poll

        data class Failed(val reason: PairingFailure) : Poll
    }

    /** Consecutive poll failures that may go away. A request to slow down is waited out, not held against the server. */
    private data class PollErrors(val transient: Int = 0, val rateLimited: Int = 0) {
        fun after(failure: ApiFailure): PollErrors =
            if (failure is ApiFailure.RateLimited) copy(rateLimited = rateLimited + 1) else copy(transient = transient + 1)

        fun exhausted(): Boolean = rateLimited >= MAX_RATE_LIMITED || transient >= MAX_TRANSIENT_ERRORS
    }

    companion object {
        /**
         * Time between polls. The server allows 150 polls per poll token in a code's 600 s lifetime;
         * polling every 5 s makes 120.
         */
        const val DEFAULT_POLL_INTERVAL_MS = 5_000L

        /** Codes requested in a row before the TV stops and waits for the user. Six codes cover an hour. */
        const val MAX_CODES = 6

        /** Least time between two code requests, whatever the polling of the previous code did. */
        const val MIN_CODE_REQUEST_INTERVAL_MS = 5_000L

        private const val MAX_TRANSIENT_ERRORS = 5
        private const val MAX_RATE_LIMITED = 3
        private const val MS_PER_SECOND = 1_000L
        private const val NOT_FOUND = 404
        private const val CODE_TTL_MS = WireLimits.PAIR_CODE_TTL_SECONDS * MS_PER_SECOND

        /**
         * How long a code stays valid, from the moment it arrived. The server's own clock gives it as
         * `expiresAt` minus the answer's `Date`; without a usable `Date` the schema's TTL stands in.
         */
        private fun lifetimeMs(issued: IssuedCode): Long {
            val expiresAtMs = parseInstantMs(issued.response.expiresAt) ?: return CODE_TTL_MS
            val serverNowMs = issued.serverTimeMs ?: return CODE_TTL_MS
            val lifetime = expiresAtMs - serverNowMs
            return if (lifetime in 1..CODE_TTL_MS) lifetime else CODE_TTL_MS
        }

        private fun parseInstantMs(value: String): Long? =
            try {
                OffsetDateTime.parse(value).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                null
            }

        /** Failures that may go away on the next poll. */
        private fun ApiFailure.isTransient(): Boolean =
            this is ApiFailure.Network || this is ApiFailure.ServerError || this is ApiFailure.RateLimited || this is ApiFailure.BadResponse

        private fun ApiFailure.toPairingFailure(): PairingFailure =
            when (this) {
                is ApiFailure.Network -> PairingFailure.NETWORK
                is ApiFailure.RateLimited -> PairingFailure.RATE_LIMITED
                is ApiFailure.ServerError, is ApiFailure.BadResponse -> PairingFailure.SERVER
                ApiFailure.Unauthorized, is ApiFailure.Rejected -> PairingFailure.REJECTED
            }
    }
}
