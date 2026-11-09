package com.jantursky.streamtracktv.core

import com.jantursky.streamtracktv.core.aggregation.DeviceEvent.ActivityPaused
import com.jantursky.streamtracktv.core.aggregation.DeviceEvent.ActivityResumed
import com.jantursky.streamtracktv.core.aggregation.DeviceEvent.DeviceStartup
import com.jantursky.streamtracktv.core.aggregation.DeviceEvent.ScreenOff
import com.jantursky.streamtracktv.core.aggregation.DeviceEvent.ScreenOn
import com.jantursky.streamtracktv.core.aggregation.KeyCounter
import com.jantursky.streamtracktv.core.aggregation.bucketsFromEvents
import com.jantursky.streamtracktv.core.aggregation.collectionWindow
import com.jantursky.streamtracktv.core.model.HourSlot
import com.jantursky.streamtracktv.core.model.KeyBucket
import com.jantursky.streamtracktv.core.model.KeyClass
import com.jantursky.streamtracktv.core.model.ScreenBucket
import com.jantursky.streamtracktv.core.model.TimeInterval
import com.jantursky.streamtracktv.core.model.UsageBucket
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BucketingTest {
    private val day = LocalDate.parse("2026-11-08")
    private val netflix = "com.netflix.ninja"

    @Test
    fun `counts only the part of a session inside the window`() {
        val events =
            listOf(
                ScreenOn(at("2026-11-07T23:00")),
                ActivityResumed(at("2026-11-07T23:30"), netflix, "Main"),
                ActivityPaused(at("2026-11-08T00:45"), netflix, "Main"),
                ScreenOff(at("2026-11-08T01:00")),
            )
        val buckets = bucketsFromEvents(events, at("2026-11-08T00:00"), at("2026-11-08T02:00"), BRATISLAVA, emptySet())
        assertEquals(listOf(UsageBucket(HourSlot(day, 0), netflix, 45 * MINUTE)), buckets.usage)
        assertEquals(listOf(ScreenBucket(HourSlot(day, 0), HOUR)), buckets.screen)
        assertTrue(buckets.keys.isEmpty())
    }

    @Test
    fun `a power loss after midnight is not counted until the next boot`() {
        val events =
            listOf(
                ScreenOn(at("2026-11-07T23:00")),
                ActivityResumed(at("2026-11-07T23:30"), netflix, "Browse"),
                ActivityResumed(at("2026-11-08T00:20"), netflix, "Player"),
                DeviceStartup(at("2026-11-08T19:00")),
                ScreenOn(at("2026-11-08T19:01")),
            )
        val buckets = bucketsFromEvents(events, at("2026-11-08T00:00"), at("2026-11-08T20:00"), BRATISLAVA, emptySet())
        assertEquals(listOf(UsageBucket(HourSlot(day, 0), netflix, 20 * MINUTE)), buckets.usage)
        assertEquals(
            listOf(ScreenBucket(HourSlot(day, 0), 20 * MINUTE), ScreenBucket(HourSlot(day, 19), 59 * MINUTE)),
            buckets.screen,
        )
    }

    @Test
    fun `drops excluded apps and names the server rejects but keeps screen time`() {
        val events =
            listOf(
                ActivityResumed(at("2026-11-08T19:00"), "com.secret.app", "Main"),
                ActivityResumed(at("2026-11-08T19:20"), "android", "Main"),
                ActivityResumed(at("2026-11-08T19:40"), netflix, "Main"),
            )
        val buckets =
            bucketsFromEvents(
                events,
                at("2026-11-08T19:00"),
                at("2026-11-08T20:00"),
                BRATISLAVA,
                excluded = setOf("com.secret.app"),
            )
        assertEquals(listOf(UsageBucket(HourSlot(day, 19), netflix, 20 * MINUTE)), buckets.usage)
        assertEquals(listOf(ScreenBucket(HourSlot(day, 19), HOUR)), buckets.screen)
    }

    @Test
    fun `an empty event list yields no buckets`() {
        assertTrue(bucketsFromEvents(emptyList(), 0, 1000, BRATISLAVA, emptySet()).isEmpty())
    }

    @Test
    fun `the collection window starts at the beginning of yesterday`() {
        val now = at("2026-11-08T19:30")
        assertEquals(TimeInterval(at("2026-11-07T00:00"), now), collectionWindow(now, BRATISLAVA))
    }

    @Test
    fun `key counter groups presses by hour and class and resets on drain`() {
        val counter = KeyCounter { BRATISLAVA }
        counter.record(at("2026-11-08T19:01"), KeyClass.DPAD)
        counter.record(at("2026-11-08T19:59"), KeyClass.DPAD)
        counter.record(at("2026-11-08T19:30"), KeyClass.SELECT)
        counter.record(at("2026-11-08T20:00"), KeyClass.DPAD)
        assertEquals(
            listOf(
                KeyBucket(HourSlot(day, 19), KeyClass.DPAD, 2),
                KeyBucket(HourSlot(day, 19), KeyClass.SELECT, 1),
                KeyBucket(HourSlot(day, 20), KeyClass.DPAD, 1),
            ),
            counter.drain(),
        )
        assertTrue(counter.drain().isEmpty())
    }
}
