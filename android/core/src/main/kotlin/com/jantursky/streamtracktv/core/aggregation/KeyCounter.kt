package com.jantursky.streamtracktv.core.aggregation

import com.jantursky.streamtracktv.core.model.HourSlot
import com.jantursky.streamtracktv.core.model.KeyBucket
import com.jantursky.streamtracktv.core.model.KeyClass
import java.time.ZoneId

/**
 * Counts key presses in memory so the accessibility service does not hit the database on every press.
 * [drain] hands back the increments since the previous drain.
 */
public class KeyCounter(private val zone: () -> ZoneId) {
    private val counts = mutableMapOf<Pair<HourSlot, KeyClass>, Long>()

    @Synchronized
    public fun record(timeMs: Long, keyClass: KeyClass) {
        val key = HourSlot.of(timeMs, zone()) to keyClass
        counts[key] = (counts[key] ?: 0L) + 1
    }

    /** Increments since the last call, one [KeyBucket] per hour and class. */
    @Synchronized
    public fun drain(): List<KeyBucket> {
        val drained = counts.map { (key, count) -> KeyBucket(key.first, key.second, count) }
        counts.clear()
        return drained.sortedWith(compareBy({ it.slot }, { it.keyClass }))
    }
}
