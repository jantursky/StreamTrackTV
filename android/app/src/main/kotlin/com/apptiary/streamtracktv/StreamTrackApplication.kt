package com.apptiary.streamtracktv

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.apptiary.streamtracktv.collector.CollectionWorker
import com.apptiary.streamtracktv.collector.CollectorHost
import com.apptiary.streamtracktv.collector.UsageCollector
import com.apptiary.streamtracktv.data.UsageRepository
import com.apptiary.streamtracktv.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class StreamTrackApplication :
    Application(),
    CollectorHost,
    Configuration.Provider {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val graph: AppGraph by lazy { AppGraph(this, scope) }

    override val usageRepository: UsageRepository get() = graph.usage
    override val usageCollector: UsageCollector get() = graph.collector

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(Workers(graph)).build()

    override fun onCreate() {
        super.onCreate()
        graph.collectorScheduler.ensureScheduled()
        keepSyncScheduled()
    }

    override fun onBootCompleted() {
        graph.collectorScheduler.ensureScheduled()
        graph.collectorScheduler.collectNow()
    }

    /** Sync runs while paired, on the chosen interval; unpairing (or a 401) stops it. */
    private fun keepSyncScheduled() {
        scope.launch {
            combine(graph.credentials.isPaired, graph.settings.settings) { paired, settings ->
                paired to
                    settings.syncIntervalMinutes
            }.distinctUntilChanged()
                .collect { (paired, interval) ->
                    if (paired) graph.syncScheduler.schedule(interval) else graph.syncScheduler.cancel()
                }
        }
    }

    private class Workers(private val graph: AppGraph) : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker? =
            when (workerClassName) {
                CollectionWorker::class.java.name -> {
                    CollectionWorker(appContext, workerParameters, graph.collector)
                }

                SyncWorker::class.java.name -> {
                    SyncWorker(
                        appContext,
                        workerParameters,
                        { graph.collector.collect() },
                        graph.syncEngine,
                        graph.syncScheduler::retryAfter,
                    )
                }

                else -> {
                    null
                }
            }
    }
}
