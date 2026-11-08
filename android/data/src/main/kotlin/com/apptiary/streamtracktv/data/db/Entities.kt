package com.apptiary.streamtracktv.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** `sent` is cleared whenever the value changes, so the next sync uploads the new absolute value. */
@Entity(tableName = "usage_buckets", primaryKeys = ["day", "hour", "packageName"])
data class UsageBucketEntity(
    val day: String,
    val hour: Int,
    val packageName: String,
    val foregroundMs: Long,
    val sent: Boolean = false,
)

@Entity(tableName = "key_buckets", primaryKeys = ["day", "hour", "keyClass"])
data class KeyBucketEntity(
    val day: String,
    val hour: Int,
    val keyClass: String,
    val count: Long,
    val sent: Boolean = false,
)

@Entity(tableName = "screen_buckets", primaryKeys = ["day", "hour"])
data class ScreenBucketEntity(
    val day: String,
    val hour: Int,
    val screenOnMs: Long,
    val sent: Boolean = false,
)

/** Last known display name of an app, kept so labels survive an uninstall. */
@Entity(tableName = "app_labels")
data class AppLabelEntity(
    @PrimaryKey val packageName: String,
    val label: String,
)
