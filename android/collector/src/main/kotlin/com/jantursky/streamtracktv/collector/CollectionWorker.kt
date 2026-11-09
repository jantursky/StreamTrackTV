package com.jantursky.streamtracktv.collector

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Periodic recomputation of the usage buckets. Created by the app's worker factory. */
class CollectionWorker(
    context: Context,
    params: WorkerParameters,
    private val collector: UsageCollector,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        collector.collect()
        return Result.success()
    }
}

/** Keeps the collection running every 15 minutes, the shortest period WorkManager allows. */
class CollectorScheduler(private val workManager: WorkManager) {
    fun ensureScheduled() {
        val request = PeriodicWorkRequestBuilder<CollectionWorker>(PERIOD_MINUTES, TimeUnit.MINUTES).build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun collectNow() {
        workManager.enqueueUniqueWork(
            ONE_OFF_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CollectionWorker>().build(),
        )
    }

    private companion object {
        const val PERIOD_MINUTES = 15L
        const val PERIODIC_NAME = "collect-usage"
        const val ONE_OFF_NAME = "collect-usage-now"
    }
}
