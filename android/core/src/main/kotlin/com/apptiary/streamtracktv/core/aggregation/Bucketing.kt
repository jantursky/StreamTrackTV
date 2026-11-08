package com.apptiary.streamtracktv.core.aggregation

import com.apptiary.streamtracktv.core.model.BucketSet
import com.apptiary.streamtracktv.core.model.ScreenBucket
import com.apptiary.streamtracktv.core.model.TimeInterval
import com.apptiary.streamtracktv.core.model.UsageBucket
import com.apptiary.streamtracktv.core.wire.isValidPackageName
import java.time.ZoneId

/**
 * Turns the usage events of `[fromMs, toMs)` into absolute hourly buckets.
 *
 * [events] should start a few hours before [fromMs] so sessions that began earlier are known; only
 * the part inside the window is counted. Packages in [excluded] and names the server would reject are
 * dropped. Every bucket is a complete value for its hour, so writing it twice changes nothing.
 */
public fun bucketsFromEvents(
    events: List<DeviceEvent>,
    fromMs: Long,
    toMs: Long,
    zone: ZoneId,
    excluded: Set<String>,
): BucketSet {
    val lookbackStart = minOf(fromMs, events.minOfOrNull { it.timeMs } ?: fromMs)
    val sessions = foregroundSessions(events, toMs)
    val usage =
        sessions
            .filter { it.packageName !in excluded && isValidPackageName(it.packageName) }
            .groupBy({ it.packageName }, { it.interval.clip(fromMs, toMs) })
            .flatMap { (pkg, intervals) ->
                sumBySlot(intervals.filterNotNull(), zone).map { (slot, ms) -> UsageBucket(slot, pkg, ms) }
            }
    val screen =
        screenOnIntervals(events, lookbackStart, toMs, sessions)
            .mapNotNull { it.clip(fromMs, toMs) }
            .let { sumBySlot(it, zone) }
            .map { (slot, ms) -> ScreenBucket(slot, ms) }
    return BucketSet(usage = usage.sortedBy { it.slot }, screen = screen.sortedBy { it.slot })
}

/** Window for one collection run: from the start of yesterday to [nowMs]. */
public fun collectionWindow(nowMs: Long, zone: ZoneId): TimeInterval {
    val startOfYesterday =
        java.time.Instant
            .ofEpochMilli(nowMs)
            .atZone(zone)
            .toLocalDate()
            .minusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    return TimeInterval(startOfYesterday, nowMs)
}
