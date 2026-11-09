package com.jantursky.streamtracktv.collector

import com.jantursky.streamtracktv.core.aggregation.bucketsFromEvents
import com.jantursky.streamtracktv.core.aggregation.collectionWindow
import com.jantursky.streamtracktv.core.model.BucketSet
import com.jantursky.streamtracktv.data.UsageRepository
import com.jantursky.streamtracktv.data.settings.SettingsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId

/**
 * Recomputes the foreground and screen-on buckets of yesterday and today from the usage events and
 * stores them as absolute values. Running it often is cheap and safe: unchanged rows stay as they are.
 */
class UsageCollector(
    private val source: UsageEventSource,
    private val repository: UsageRepository,
    private val settings: SettingsRepository,
    private val catalog: AppCatalog,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val mutex = Mutex()

    suspend fun collect(): BucketSet =
        mutex.withLock {
            val nowMs = now()
            val zone = zone()
            val window = collectionWindow(nowMs, zone)
            val events = source.events(window.startMs - LOOKBACK_MS, nowMs)
            val buckets = bucketsFromEvents(events, window.startMs, nowMs, zone, settings.current().excludedPackages)
            repository.record(buckets)
            val labels =
                buckets.usage
                    .map { it.packageName }
                    .distinct()
                    .mapNotNull { pkg -> catalog.label(pkg)?.let { pkg to it } }
                    .toMap()
            if (labels.isNotEmpty()) repository.saveLabels(labels)
            buckets
        }

    private companion object {
        /** Events this far before the window tell which app was already open when it started. */
        const val LOOKBACK_MS = 6 * 60 * 60_000L
    }
}
