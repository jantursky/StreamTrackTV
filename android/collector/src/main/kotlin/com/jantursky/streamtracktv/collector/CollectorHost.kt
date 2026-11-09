package com.jantursky.streamtracktv.collector

import com.jantursky.streamtracktv.data.UsageRepository

/** Implemented by the application so the service, receiver and worker reach the shared instances. */
interface CollectorHost {
    val usageRepository: UsageRepository
    val usageCollector: UsageCollector

    /** Called after boot and after an app update to put the background work back in place. */
    fun onBootCompleted()
}
