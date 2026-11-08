package com.apptiary.streamtracktv.core.model

/** Foreground time of one app during one local hour. */
public data class UsageBucket(val slot: HourSlot, val packageName: String, val foregroundMs: Long)

/** Remote key presses of one class during one local hour. */
public data class KeyBucket(val slot: HourSlot, val keyClass: KeyClass, val count: Long)

/** Screen-on time during one local hour. */
public data class ScreenBucket(val slot: HourSlot, val screenOnMs: Long)

/** All three bucket kinds for some range of hours. */
public data class BucketSet(
    val usage: List<UsageBucket> = emptyList(),
    val keys: List<KeyBucket> = emptyList(),
    val screen: List<ScreenBucket> = emptyList(),
) {
    val size: Int get() = usage.size + keys.size + screen.size

    public fun isEmpty(): Boolean = size == 0
}

/** Stable identity of a bucket, identical to `bucketKey()` in the shared schema package. */
public fun UsageBucket.bucketKey(): String = "${slot.keyPrefix}|app|$packageName"

public fun KeyBucket.bucketKey(): String = "${slot.keyPrefix}|key|${keyClass.wireName}"

public fun ScreenBucket.bucketKey(): String = "${slot.keyPrefix}|screen"
