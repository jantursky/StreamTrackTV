package com.jantursky.streamtracktv.core.aggregation

import com.jantursky.streamtracktv.core.model.TimeInterval
import com.jantursky.streamtracktv.core.model.union

/** One continuous stretch of an app in the foreground. */
public data class AppSession(val packageName: String, val interval: TimeInterval)

/**
 * Turns usage events into foreground sessions.
 *
 * An app stays in the foreground while at least one of its activities is resumed, so moving between
 * two activities of the same app does not split the session. Another app resuming or the screen going
 * off ends it. A power loss leaves no shutdown event; a boot, or a screen-on while the screen is already
 * on, then ends the session at the last event seen before it. A session still open at the end is closed
 * at [endMs].
 */
public fun foregroundSessions(events: List<DeviceEvent>, endMs: Long): List<AppSession> {
    val tracker = ForegroundTracker()
    var screenOn = false
    var lastMs: Long? = null
    for (event in events.sortedBy { it.timeMs }) {
        val before = lastMs ?: event.timeMs
        when (event) {
            is DeviceEvent.ActivityResumed -> tracker.resume(event)
            is DeviceEvent.ActivityPaused -> tracker.pause(event)
            is DeviceEvent.ScreenOff -> tracker.close(event.timeMs)
            is DeviceEvent.DeviceStartup -> tracker.close(before)
            is DeviceEvent.ScreenOn -> if (screenOn) tracker.close(before)
        }
        screenOn =
            when (event) {
                is DeviceEvent.ScreenOn -> true
                is DeviceEvent.ScreenOff, is DeviceEvent.DeviceStartup -> false
                is DeviceEvent.ActivityResumed, is DeviceEvent.ActivityPaused -> screenOn
            }
        lastMs = event.timeMs
    }
    tracker.close(endMs)
    return tracker.sessions
}

/** The app in the foreground and its resumed activities, while walking the events in order. */
private class ForegroundTracker {
    val sessions = mutableListOf<AppSession>()
    private var current: String? = null
    private var startMs = 0L
    private val resumed = mutableSetOf<String>()

    fun resume(event: DeviceEvent.ActivityResumed) {
        if (current != event.packageName) {
            close(event.timeMs)
            current = event.packageName
            startMs = event.timeMs
        }
        resumed += event.activity
    }

    fun pause(event: DeviceEvent.ActivityPaused) {
        if (event.packageName != current) return
        resumed -= event.activity
        if (resumed.isEmpty()) close(event.timeMs)
    }

    fun close(atMs: Long) {
        val pkg = current ?: return
        if (atMs > startMs) sessions += AppSession(pkg, TimeInterval(startMs, atMs))
        current = null
        resumed.clear()
    }
}

/**
 * Screen-on intervals from interactive / non-interactive events.
 *
 * The screen counts as on from [startMs] when the first screen event is a screen-off. A boot, or a
 * screen-on while the screen is already on, means a power loss went unrecorded: the open interval ends
 * at the last event of any kind seen before it. Devices below Android 9 report no screen events at all;
 * there the union of [fallback] sessions stands in, since an app in the foreground implies a lit screen.
 */
public fun screenOnIntervals(
    events: List<DeviceEvent>,
    startMs: Long,
    endMs: Long,
    fallback: List<AppSession>,
): List<TimeInterval> {
    val sorted = events.sortedBy { it.timeMs }
    if (sorted.none { it is DeviceEvent.ScreenOn || it is DeviceEvent.ScreenOff }) return fallback.map { it.interval }.union()

    val intervals = mutableListOf<TimeInterval>()
    val firstScreenEvent = sorted.first { it is DeviceEvent.ScreenOn || it is DeviceEvent.ScreenOff || it is DeviceEvent.DeviceStartup }
    var onSinceMs: Long? = if (firstScreenEvent is DeviceEvent.ScreenOff) startMs else null
    var lastMs = startMs

    fun closeAt(atMs: Long) {
        onSinceMs?.let { if (atMs > it) intervals += TimeInterval(it, atMs) }
        onSinceMs = null
    }

    for (event in sorted) {
        if (event is DeviceEvent.ScreenOff) {
            closeAt(event.timeMs)
        } else if (event is DeviceEvent.ScreenOn || event is DeviceEvent.DeviceStartup) {
            closeAt(lastMs)
            if (event is DeviceEvent.ScreenOn) onSinceMs = event.timeMs
        }
        lastMs = maxOf(lastMs, event.timeMs)
    }
    closeAt(endMs)
    return intervals
}
