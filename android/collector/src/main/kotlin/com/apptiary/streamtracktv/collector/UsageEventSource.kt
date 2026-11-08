package com.apptiary.streamtracktv.collector

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.apptiary.streamtracktv.core.aggregation.DeviceEvent

/** Usage events between two instants, oldest first. */
fun interface UsageEventSource {
    fun events(fromMs: Long, toMs: Long): List<DeviceEvent>
}

/** Reads [UsageStatsManager]. Returns nothing until the user grants usage access. */
class AndroidUsageEventSource(context: Context) : UsageEventSource {
    private val manager = context.getSystemService(UsageStatsManager::class.java)

    override fun events(fromMs: Long, toMs: Long): List<DeviceEvent> {
        val events = manager?.queryEvents(fromMs, toMs) ?: return emptyList()
        val result = mutableListOf<DeviceEvent>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            toDeviceEvent(event.eventType, event.timeStamp, event.packageName, event.className)?.let(result::add)
        }
        return result
    }
}

// Event type values from UsageEvents.Event. Some names only exist on newer SDKs, the values are stable.
private const val ACTIVITY_RESUMED = 1
private const val ACTIVITY_PAUSED = 2
private const val SCREEN_INTERACTIVE = 15
private const val SCREEN_NON_INTERACTIVE = 16
private const val ACTIVITY_STOPPED = 23
private const val DEVICE_SHUTDOWN = 26
private const val DEVICE_STARTUP = 27

/** Keeps the event types the aggregation needs and drops the rest. */
internal fun toDeviceEvent(type: Int, timeMs: Long, packageName: String?, className: String?): DeviceEvent? =
    when (type) {
        ACTIVITY_RESUMED -> packageName?.let { DeviceEvent.ActivityResumed(timeMs, it, className ?: it) }
        ACTIVITY_PAUSED, ACTIVITY_STOPPED -> packageName?.let { DeviceEvent.ActivityPaused(timeMs, it, className ?: it) }
        SCREEN_INTERACTIVE -> DeviceEvent.ScreenOn(timeMs)
        SCREEN_NON_INTERACTIVE, DEVICE_SHUTDOWN -> DeviceEvent.ScreenOff(timeMs)
        DEVICE_STARTUP -> DeviceEvent.DeviceStartup(timeMs)
        else -> null
    }
