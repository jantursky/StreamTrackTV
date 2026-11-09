package com.jantursky.streamtracktv.core.model

/** Half-open interval `[startMs, endMs)` in epoch milliseconds. */
public data class TimeInterval(val startMs: Long, val endMs: Long) {
    init {
        require(endMs >= startMs) { "Interval ends before it starts: $startMs..$endMs" }
    }

    val durationMs: Long get() = endMs - startMs

    /** The part of this interval inside `[fromMs, toMs)`, or null when they do not overlap. */
    public fun clip(fromMs: Long, toMs: Long): TimeInterval? {
        val start = maxOf(startMs, fromMs)
        val end = minOf(endMs, toMs)
        return if (end > start) TimeInterval(start, end) else null
    }
}

/** Merges overlapping or touching intervals into a sorted, disjoint list. */
public fun List<TimeInterval>.union(): List<TimeInterval> {
    val merged = mutableListOf<TimeInterval>()
    for (interval in sortedBy { it.startMs }) {
        val last = merged.lastOrNull()
        if (last != null && interval.startMs <= last.endMs) {
            merged[merged.lastIndex] = TimeInterval(last.startMs, maxOf(last.endMs, interval.endMs))
        } else {
            merged += interval
        }
    }
    return merged
}
