package com.jantursky.streamtracktv.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities =
        [
            UsageBucketEntity::class,
            KeyBucketEntity::class,
            ScreenBucketEntity::class,
            AppLabelEntity::class,
            SecurityEventEntity::class,
        ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class StreamTrackDatabase : RoomDatabase() {
    abstract fun buckets(): BucketDao

    abstract fun securityEvents(): SecurityEventDao

    companion object {
        fun create(context: Context): StreamTrackDatabase =
            Room.databaseBuilder(context, StreamTrackDatabase::class.java, "streamtrack.db").build()
    }
}
