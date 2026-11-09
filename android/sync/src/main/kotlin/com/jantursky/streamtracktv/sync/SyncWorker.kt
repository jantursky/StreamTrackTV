package com.jantursky.streamtracktv.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jantursky.streamtracktv.core.model.SyncInterval
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/**
 * Refreshes the usage buckets, then uploads what is pending. Created by the app's worker factory.
 * Collection failing (no usage access yet) does not stop the key counts from syncing. When the server
 * names a time to come back, [retryAfter] schedules that instead of the usual backoff.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val collect: suspend () -> Unit,
    private val engine: SyncEngine,
    private val retryAfter: (seconds: Long) -> Unit,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            collect()
        } catch (_: SecurityException) {
            // Usage access was revoked; the keys and screen rows already stored still go out.
        }
        return when (val outcome = engine.sync()) {
            is SyncOutcome.Synced, SyncOutcome.NotPaired, SyncOutcome.Unauthorized -> {
                Result.success()
            }

            is SyncOutcome.RetryLater -> {
                val seconds = outcome.retryAfterSeconds ?: return Result.retry()
                retryAfter(seconds)
                Result.success()
            }

            is SyncOutcome.Failed -> {
                Result.failure()
            }
        }
    }
}

/** Periodic sync on the user's interval with exponential backoff, plus "sync now". */
class SyncScheduler(private val workManager: WorkManager) {
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(intervalMinutes: Int) {
        val request =
            PeriodicWorkRequestBuilder<SyncWorker>(SyncInterval.clamp(intervalMinutes).toLong(), TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun syncNow() {
        val request =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
        workManager.enqueueUniqueWork(ONE_OFF_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** One sync after the server's `Retry-After`, kept within 30 seconds and a day. */
    fun retryAfter(seconds: Long) {
        val request =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setInitialDelay(seconds.coerceIn(BACKOFF_SECONDS, MAX_RETRY_AFTER_SECONDS), TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
        // Appending lets a retry scheduled from inside a running retry wait for it instead of cancelling it.
        workManager.enqueueUniqueWork(RETRY_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun cancel() {
        workManager.cancelUniqueWork(PERIODIC_NAME)
        workManager.cancelUniqueWork(ONE_OFF_NAME)
        workManager.cancelUniqueWork(RETRY_NAME)
    }

    /** Epoch milliseconds of the next periodic run, or null when none is scheduled. */
    fun nextRunAt(): Flow<Long?> =
        workManager.getWorkInfosForUniqueWorkFlow(PERIODIC_NAME).map { infos ->
            infos.firstOrNull { !it.state.isFinished }?.nextScheduleTimeMillis?.takeIf { it != Long.MAX_VALUE }
        }

    companion object {
        private const val PERIODIC_NAME = "sync"
        private const val ONE_OFF_NAME = "sync-now"
        const val RETRY_NAME = "sync-retry"
        private const val BACKOFF_SECONDS = 30L
        private const val MAX_RETRY_AFTER_SECONDS = 24 * 60 * 60L
    }
}
