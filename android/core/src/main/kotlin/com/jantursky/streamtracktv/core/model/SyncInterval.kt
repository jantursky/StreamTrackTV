package com.jantursky.streamtracktv.core.model

/** Sync intervals the settings offer. WorkManager cannot run periodic work more often than 15 minutes. */
public object SyncInterval {
    public const val MIN_MINUTES: Int = 15
    public const val MAX_MINUTES: Int = 24 * 60
    public const val DEFAULT_MINUTES: Int = 60

    public val choices: List<Int> = listOf(15, 30, 60, 180, 360, 720, MAX_MINUTES)

    public fun clamp(minutes: Int): Int = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)

    /** The choice after [minutes], wrapping around; used by the D-Pad option cycler. */
    public fun next(minutes: Int): Int = choices.firstOrNull { it > minutes } ?: choices.first()
}
