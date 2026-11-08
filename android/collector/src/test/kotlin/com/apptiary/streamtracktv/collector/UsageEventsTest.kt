package com.apptiary.streamtracktv.collector

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.apptiary.streamtracktv.core.aggregation.DeviceEvent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowUsageStatsManager
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class UsageEventsTest {
    @Test
    fun `keeps foreground and screen events only`() {
        assertEquals(
            DeviceEvent.ActivityResumed(1, "com.a.b", "com.a.b.Main"),
            toDeviceEvent(1, 1, "com.a.b", "com.a.b.Main"),
        )
        assertEquals(DeviceEvent.ActivityPaused(2, "com.a.b", "com.a.b"), toDeviceEvent(2, 2, "com.a.b", null))
        assertEquals(DeviceEvent.ActivityPaused(3, "com.a.b", "X"), toDeviceEvent(23, 3, "com.a.b", "X"))
        assertEquals(DeviceEvent.ScreenOn(4), toDeviceEvent(15, 4, null, null))
        assertEquals(DeviceEvent.ScreenOff(5), toDeviceEvent(16, 5, null, null))
        assertEquals(DeviceEvent.ScreenOff(6), toDeviceEvent(26, 6, null, null))
        assertEquals(DeviceEvent.DeviceStartup(7), toDeviceEvent(27, 7, null, null))
        assertNull(toDeviceEvent(1, 7, null, null))
        assertNull(toDeviceEvent(5, 8, "com.a.b", null))
    }

    @Test
    fun `reads events from the usage stats manager`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shadow = shadowOf(context.getSystemService(UsageStatsManager::class.java))
        listOf(
            1_000L to UsageEvents.Event.ACTIVITY_RESUMED,
            5_000L to UsageEvents.Event.ACTIVITY_PAUSED,
            6_000L to UsageEvents.Event.USER_INTERACTION,
        ).forEach { (time, type) ->
            shadow.addEvent(
                ShadowUsageStatsManager.EventBuilder
                    .buildEvent()
                    .setPackage("com.netflix.ninja")
                    .setClass("com.netflix.ninja.Main")
                    .setTimeStamp(time)
                    .setEventType(type)
                    .build(),
            )
        }
        val events = AndroidUsageEventSource(context).events(0, 10_000)
        assertEquals(listOf(1_000L, 5_000L), events.map { it.timeMs })
        assertEquals(
            listOf("com.netflix.ninja"),
            events.filterIsInstance<DeviceEvent.ActivityResumed>().map { it.packageName },
        )
    }
}
