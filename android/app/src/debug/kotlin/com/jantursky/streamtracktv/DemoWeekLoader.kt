package com.jantursky.streamtracktv

import com.jantursky.streamtracktv.core.seed.demoWeek
import com.jantursky.streamtracktv.data.UsageRepository
import java.time.LocalDateTime

/** Debug builds: replaces local data with a generated week for previews and screenshots. */
fun demoWeekLoader(usage: UsageRepository): (suspend () -> Unit)? = {
    val now = LocalDateTime.now()
    val week = demoWeek(now.toLocalDate(), now.hour)
    usage.deleteAll()
    usage.record(week.buckets)
    usage.addKeyPresses(week.buckets.keys)
    usage.saveLabels(week.labels)
}
