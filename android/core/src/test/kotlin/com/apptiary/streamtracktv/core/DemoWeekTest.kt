package com.apptiary.streamtracktv.core

import com.apptiary.streamtracktv.core.insights.detectBinges
import com.apptiary.streamtracktv.core.insights.detectIdleScreen
import com.apptiary.streamtracktv.core.model.bucketKey
import com.apptiary.streamtracktv.core.payload.buildBatch
import com.apptiary.streamtracktv.core.seed.demoWeek
import com.apptiary.streamtracktv.core.wire.WireLimits
import com.apptiary.streamtracktv.core.wire.isValidPackageName
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DemoWeekTest {
    // A Sunday, so the week contains a Saturday.
    private val today = LocalDate.parse("2026-11-08")
    private val week = demoWeek(today, currentHour = 21)

    @Test
    fun `is deterministic for a seed`() {
        assertEquals(week, demoWeek(today, currentHour = 21))
    }

    @Test
    fun `covers seven days and stops before the current hour`() {
        val days =
            week.buckets.screen
                .map { it.slot.day }
                .toSet()
        assertEquals(7, days.size)
        assertTrue(
            week.buckets.screen
                .filter { it.slot.day == today }
                .all { it.slot.hour < 21 },
        )
    }

    @Test
    fun `stays within what the server accepts`() {
        val buckets = week.buckets
        assertTrue(
            buckets.usage.all {
                isValidPackageName(it.packageName) &&
                    it.foregroundMs in 1..WireLimits.MS_PER_HOUR
            },
        )
        assertTrue(buckets.screen.all { it.screenOnMs in 1..WireLimits.MS_PER_HOUR })
        assertTrue(buckets.keys.all { it.count > 0 })
        assertEquals(
            buckets.usage.size,
            buckets.usage
                .map { it.bucketKey() }
                .toSet()
                .size,
        )
        val usageByHour = buckets.usage.groupBy { it.slot }.mapValues { (_, rows) -> rows.sumOf { it.foregroundMs } }
        buckets.screen.forEach { assertEquals(it.screenOnMs, usageByHour[it.slot]) }
        assertTrue(buckets.usage.all { it.packageName in week.labels })
        buildBatch(buckets, week.labels, emptySet(), Instant.EPOCH, BRATISLAVA)
    }

    @Test
    fun `contains a binge and a screen left on`() {
        assertTrue(detectBinges(week.buckets.usage).any { it.packageName == "com.netflix.ninja" && it.hours >= 4 })
        val idle = detectIdleScreen(week.buckets.screen, week.buckets.keys)
        assertTrue(idle.any { it.start.day == today.minusDays(1) })
    }
}
