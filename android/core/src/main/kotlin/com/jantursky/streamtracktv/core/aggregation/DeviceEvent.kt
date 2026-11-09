package com.jantursky.streamtracktv.core.aggregation

/** Framework usage events reduced to what the aggregation needs. */
public sealed interface DeviceEvent {
    public val timeMs: Long

    /** An activity of [packageName] came to the foreground. */
    public data class ActivityResumed(
        override val timeMs: Long,
        val packageName: String,
        val activity: String,
    ) : DeviceEvent

    /** An activity of [packageName] left the foreground. */
    public data class ActivityPaused(
        override val timeMs: Long,
        val packageName: String,
        val activity: String,
    ) : DeviceEvent

    /** The screen became interactive. Seen while the screen is already on, it marks a missed screen-off. */
    public data class ScreenOn(override val timeMs: Long) : DeviceEvent

    /** Screen turned off or the device shut down. Ends every foreground session. */
    public data class ScreenOff(override val timeMs: Long) : DeviceEvent

    /**
     * The device booted. Whatever was open before it ended with a power loss at an unknown time, so it
     * is closed at the last event seen before the boot.
     */
    public data class DeviceStartup(override val timeMs: Long) : DeviceEvent
}
