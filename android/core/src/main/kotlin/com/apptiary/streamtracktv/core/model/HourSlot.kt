package com.apptiary.streamtracktv.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One local hour on the TV: the unit every bucket is keyed by. */
public data class HourSlot(val day: LocalDate, val hour: Int) : Comparable<HourSlot> {
    init {
        require(hour in 0..LAST_HOUR) { "Hour must be 0-23, was $hour" }
    }

    /** `YYYY-MM-DD`, as sent on the wire. */
    val dayString: String get() = day.toString()

    /** Prefix shared by all bucket keys of this slot, e.g. `2026-11-08T07`. */
    val keyPrefix: String get() = "${dayString}T${hour.toString().padStart(2, '0')}"

    public fun next(): HourSlot = if (hour == LAST_HOUR) HourSlot(day.plusDays(1), 0) else HourSlot(day, hour + 1)

    override fun compareTo(other: HourSlot): Int = compareValuesBy(this, other, { it.day }, { it.hour })

    public companion object {
        public const val LAST_HOUR: Int = 23

        public fun of(epochMs: Long, zone: ZoneId): HourSlot {
            val local = Instant.ofEpochMilli(epochMs).atZone(zone)
            return HourSlot(local.toLocalDate(), local.hour)
        }

        public fun parse(day: String, hour: Int): HourSlot = HourSlot(LocalDate.parse(day), hour)
    }
}
