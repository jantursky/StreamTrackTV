package com.jantursky.streamtracktv.sync

import com.jantursky.streamtracktv.core.model.BucketSet
import com.jantursky.streamtracktv.core.pairing.ServerUrl
import com.jantursky.streamtracktv.core.payload.MAX_ROWS_PER_BATCH
import com.jantursky.streamtracktv.core.payload.OutgoingBatch
import com.jantursky.streamtracktv.core.payload.buildBatch
import com.jantursky.streamtracktv.core.wire.EventsRequestDto
import com.jantursky.streamtracktv.core.wire.WireLimits
import com.jantursky.streamtracktv.core.wire.toDto
import com.jantursky.streamtracktv.data.SecurityLog
import com.jantursky.streamtracktv.data.UsageRepository
import com.jantursky.streamtracktv.data.credentials.CredentialStore
import com.jantursky.streamtracktv.data.settings.SettingsRepository
import com.jantursky.streamtracktv.data.settings.SyncRecord
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

sealed interface SyncOutcome {
    data class Synced(val rows: Int) : SyncOutcome

    data object NotPaired : SyncOutcome

    /** The server no longer knows this TV. The token is gone and the TV must pair again. */
    data object Unauthorized : SyncOutcome

    /**
     * Worth retrying: offline, rate limited or a server error. [retryAfterSeconds] is the server's
     * `Retry-After`, when it named a time; otherwise the usual backoff applies.
     */
    data class RetryLater(val reason: String, val retryAfterSeconds: Long? = null) : SyncOutcome

    /** The server rejected the data; retrying the same rows will not help. */
    data class Failed(val reason: String) : SyncOutcome
}

/**
 * Uploads unsent buckets. Rows are absolute values, so a batch that reached the server but whose
 * response got lost is simply sent again and the server's upsert ends in the same state.
 *
 * A batch the server refuses as too large or invalid (400, 413) is halved and sent again, so one bad
 * row cannot hold back the rest. A single row that is still refused is set aside: it is marked as
 * settled, counted in [SettingsRepository], and only goes out again if its value changes.
 *
 * Security events follow the usage rows in the same run. They never change the outcome: a failed upload keeps
 * them for the next run, and a batch the server refuses is halved the same way.
 */
class SyncEngine(
    private val api: StreamTrackApi,
    private val repository: UsageRepository,
    private val settings: SettingsRepository,
    private val credentials: CredentialStore,
    private val securityLog: SecurityLog,
    private val now: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val mutex = Mutex()

    suspend fun sync(): SyncOutcome =
        mutex.withLock {
            val outcome = upload()
            if (outcome !is SyncOutcome.NotPaired && outcome !is SyncOutcome.Unauthorized) uploadEvents()
            record(outcome)
            outcome
        }

    /** The events the next sync would send first, for the privacy screen. */
    suspend fun nextEvents(): EventsRequestDto = EventsRequestDto(events = securityLog.pending().map { it.toDto() })

    /** The batch the next sync would send first, for the privacy screen. */
    suspend fun nextBatch(): OutgoingBatch = batchOf(repository.pending(), MAX_ROWS_PER_BATCH)

    private suspend fun batchOf(pending: BucketSet, maxRows: Int): OutgoingBatch =
        buildBatch(pending, repository.labels(), settings.current().excludedPackages, now(), zone(), maxRows)

    private suspend fun upload(): SyncOutcome {
        val server = settings.current().serverUrl?.let { ServerUrl.parse(it, allowHttp = true) }
        val token = credentials.token()
        if (server == null || token == null) return SyncOutcome.NotPaired
        var sent = 0
        var maxRows = MAX_ROWS_PER_BATCH
        var requests = 0
        var pending = repository.pending()
        while (!pending.isEmpty() && requests++ < MAX_REQUESTS_PER_RUN) {
            val batch = batchOf(pending, maxRows)
            val result = api.sync(server, token, batch.payload)
            if (result is ApiResult.Ok) {
                repository.markSent(batch.rows)
                sent += batch.rows.size
            } else {
                val failure = (result as ApiResult.Failed).failure
                when {
                    !failure.refusesPayload() -> return failure.toOutcome()
                    batch.rows.size > 1 -> maxRows = batch.rows.size / 2
                    else -> setAside(batch.rows)
                }
            }
            pending = repository.pending()
        }
        return SyncOutcome.Synced(sent)
    }

    /** Sends unsent security events, oldest first, and marks them sent only once the server has them. */
    private suspend fun uploadEvents() {
        val server = settings.current().serverUrl?.let { ServerUrl.parse(it, allowHttp = true) }
        val token = credentials.token()
        if (server == null || token == null) return
        var size = WireLimits.MAX_EVENTS_PER_BATCH
        var requests = 0
        while (requests++ < MAX_EVENT_REQUESTS) {
            val batch = securityLog.pending(size)
            if (batch.isEmpty()) return
            val result = api.postEvents(server, token, EventsRequestDto(events = batch.map { it.toDto() }))
            when {
                result is ApiResult.Ok -> securityLog.markSent(batch)

                result.refusesPayload() && batch.size > 1 -> size = batch.size / 2

                // One event the server will not take: settle it, or it would hold back every later one.
                result.refusesPayload() -> securityLog.markSent(batch)

                else -> return
            }
        }
    }

    private fun ApiResult<*>.refusesPayload(): Boolean = (this as? ApiResult.Failed)?.failure?.refusesPayload() == true

    /** A row the server refuses on its own: settle it so it is not sent forever, and count it. */
    private suspend fun setAside(rows: BucketSet) {
        repository.markSent(rows)
        settings.addRejectedRows(rows.size)
    }

    private suspend fun ApiFailure.toOutcome(): SyncOutcome =
        when (this) {
            ApiFailure.Unauthorized -> {
                credentials.clear()
                SyncOutcome.Unauthorized
            }

            is ApiFailure.Rejected, is ApiFailure.BadResponse -> {
                SyncOutcome.Failed(describe())
            }

            is ApiFailure.RateLimited -> {
                SyncOutcome.RetryLater(describe(), retryAfterSeconds)
            }

            is ApiFailure.ServerError, is ApiFailure.Network -> {
                SyncOutcome.RetryLater(describe())
            }
        }

    private suspend fun record(outcome: SyncOutcome) {
        val at = now().toEpochMilli()
        val record =
            when (outcome) {
                is SyncOutcome.Synced -> SyncRecord(at, ok = true, message = null)
                SyncOutcome.NotPaired -> return
                SyncOutcome.Unauthorized -> SyncRecord(at, ok = false, message = "HTTP 401")
                is SyncOutcome.RetryLater -> SyncRecord(at, ok = false, message = outcome.reason)
                is SyncOutcome.Failed -> SyncRecord(at, ok = false, message = outcome.reason)
            }
        settings.recordSync(record)
    }

    private companion object {
        /**
         * Bounds one run; anything left waits for the next period. Fifteen requests cover the halving
         * from a full batch down to one row, and four runs an hour stay within the server's 60 syncs.
         */
        const val MAX_REQUESTS_PER_RUN = 15

        /** Five batches of 100 catch up 500 events, the most the TV keeps. */
        const val MAX_EVENT_REQUESTS = 5

        private val PAYLOAD_STATUSES = setOf(400, 413)

        /** The server will not take this payload as it is; a smaller one may pass. */
        fun ApiFailure.refusesPayload(): Boolean = this is ApiFailure.Rejected && status in PAYLOAD_STATUSES
    }
}
