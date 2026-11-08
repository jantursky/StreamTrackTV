package com.apptiary.streamtracktv.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [UsageBucketEntity::class, KeyBucketEntity::class, ScreenBucketEntity::class, AppLabelEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class StreamTrackDatabase : RoomDatabase() {
    abstract fun buckets(): BucketDao

    companion object {
        fun create(context: Context): StreamTrackDatabase =
            Room.databaseBuilder(context, StreamTrackDatabase::class.java, "streamtrack.db").build()
    }
}
