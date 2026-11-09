package com.jantursky.streamtracktv.core

import java.time.LocalDateTime
import java.time.ZoneId

val BRATISLAVA: ZoneId = ZoneId.of("Europe/Bratislava")

const val MINUTE: Long = 60_000L
const val HOUR: Long = 60 * MINUTE

/** Epoch milliseconds of a local date-time string such as `2026-11-08T19:30` in [zone]. */
fun at(local: String, zone: ZoneId = BRATISLAVA): Long =
    LocalDateTime
        .parse(local)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
