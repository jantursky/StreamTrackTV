package com.apptiary.streamtracktv.data.db

import com.apptiary.streamtracktv.core.model.HourSlot
import com.apptiary.streamtracktv.core.model.KeyBucket
import com.apptiary.streamtracktv.core.model.KeyClass
import com.apptiary.streamtracktv.core.model.ScreenBucket
import com.apptiary.streamtracktv.core.model.UsageBucket

internal fun UsageBucket.toEntity() = UsageBucketEntity(slot.dayString, slot.hour, packageName, foregroundMs)

internal fun KeyBucket.toEntity() = KeyBucketEntity(slot.dayString, slot.hour, keyClass.wireName, count)

internal fun ScreenBucket.toEntity() = ScreenBucketEntity(slot.dayString, slot.hour, screenOnMs)

internal fun UsageBucketEntity.toModel() = UsageBucket(HourSlot.parse(day, hour), packageName, foregroundMs)

internal fun ScreenBucketEntity.toModel() = ScreenBucket(HourSlot.parse(day, hour), screenOnMs)

/** Null for a key class this version does not know, which only a downgrade could leave behind. */
internal fun KeyBucketEntity.toModel(): KeyBucket? =
    KeyClass.fromWireName(keyClass)?.let { KeyBucket(HourSlot.parse(day, hour), it, count) }
