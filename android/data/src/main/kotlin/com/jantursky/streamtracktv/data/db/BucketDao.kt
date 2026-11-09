package com.jantursky.streamtracktv.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Bucket storage. Upserts are written as read-compare-write in a transaction instead of SQLite's
 * `ON CONFLICT DO UPDATE`, which the framework SQLite of Android 8 and 9 does not support.
 */
@Dao
@Suppress("TooManyFunctions")
abstract class BucketDao {
    @Query("SELECT foregroundMs FROM usage_buckets WHERE day = :day AND hour = :hour AND packageName = :packageName")
    protected abstract suspend fun usageValue(day: String, hour: Int, packageName: String): Long?

    @Query("SELECT screenOnMs FROM screen_buckets WHERE day = :day AND hour = :hour")
    protected abstract suspend fun screenValue(day: String, hour: Int): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putUsage(row: UsageBucketEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putScreen(row: ScreenBucketEntity)

    @Query("INSERT OR IGNORE INTO key_buckets (day, hour, keyClass, count, sent) VALUES (:day, :hour, :keyClass, 0, 0)")
    protected abstract suspend fun ensureKeyRow(day: String, hour: Int, keyClass: String)

    @Query(
        "UPDATE key_buckets SET count = count + :delta, sent = 0 WHERE day = :day AND hour = :hour AND keyClass = :keyClass",
    )
    protected abstract suspend fun incrementKey(day: String, hour: Int, keyClass: String, delta: Long)

    /** Stores absolute values; rows whose value is unchanged keep their sent flag. */
    @Transaction
    open suspend fun upsertUsage(rows: List<UsageBucketEntity>) {
        for (row in rows) {
            if (usageValue(row.day, row.hour, row.packageName) != row.foregroundMs) putUsage(row.copy(sent = false))
        }
    }

    @Transaction
    open suspend fun upsertScreen(rows: List<ScreenBucketEntity>) {
        for (row in rows) {
            if (screenValue(row.day, row.hour) != row.screenOnMs) putScreen(row.copy(sent = false))
        }
    }

    /** Adds increments to the key counts. */
    @Transaction
    open suspend fun addKeyCounts(rows: List<KeyBucketEntity>) {
        for (row in rows.filter { it.count > 0 }) {
            ensureKeyRow(row.day, row.hour, row.keyClass)
            incrementKey(row.day, row.hour, row.keyClass, row.count)
        }
    }

    @Query("SELECT * FROM usage_buckets WHERE sent = 0 ORDER BY day, hour, packageName LIMIT :limit")
    abstract suspend fun pendingUsage(limit: Int): List<UsageBucketEntity>

    @Query("SELECT * FROM key_buckets WHERE sent = 0 ORDER BY day, hour, keyClass LIMIT :limit")
    abstract suspend fun pendingKeys(limit: Int): List<KeyBucketEntity>

    @Query("SELECT * FROM screen_buckets WHERE sent = 0 ORDER BY day, hour LIMIT :limit")
    abstract suspend fun pendingScreen(limit: Int): List<ScreenBucketEntity>

    // Mark-sent only matches rows that still hold the uploaded value. A row that changed while the
    // request was in flight stays unsent and goes out again with its new value.

    @Query(
        "UPDATE usage_buckets SET sent = 1 " +
            "WHERE day = :day AND hour = :hour AND packageName = :packageName AND foregroundMs = :foregroundMs",
    )
    protected abstract suspend fun markUsageSent(day: String, hour: Int, packageName: String, foregroundMs: Long)

    @Query(
        "UPDATE key_buckets SET sent = 1 WHERE day = :day AND hour = :hour AND keyClass = :keyClass AND count = :count",
    )
    protected abstract suspend fun markKeySent(day: String, hour: Int, keyClass: String, count: Long)

    @Query("UPDATE screen_buckets SET sent = 1 WHERE day = :day AND hour = :hour AND screenOnMs = :screenOnMs")
    protected abstract suspend fun markScreenSent(day: String, hour: Int, screenOnMs: Long)

    @Transaction
    open suspend fun markSent(
        usage: List<UsageBucketEntity>,
        keys: List<KeyBucketEntity>,
        screen: List<ScreenBucketEntity>,
    ) {
        usage.forEach { markUsageSent(it.day, it.hour, it.packageName, it.foregroundMs) }
        keys.forEach { markKeySent(it.day, it.hour, it.keyClass, it.count) }
        screen.forEach { markScreenSent(it.day, it.hour, it.screenOnMs) }
    }

    @Query("SELECT * FROM usage_buckets WHERE day BETWEEN :from AND :to")
    abstract fun observeUsage(from: String, to: String): Flow<List<UsageBucketEntity>>

    @Query("SELECT * FROM key_buckets WHERE day BETWEEN :from AND :to")
    abstract fun observeKeys(from: String, to: String): Flow<List<KeyBucketEntity>>

    @Query("SELECT * FROM screen_buckets WHERE day BETWEEN :from AND :to")
    abstract fun observeScreen(from: String, to: String): Flow<List<ScreenBucketEntity>>

    @Query(
        "SELECT (SELECT COUNT(*) FROM usage_buckets WHERE sent = 0) + " +
            "(SELECT COUNT(*) FROM key_buckets WHERE sent = 0) + " +
            "(SELECT COUNT(*) FROM screen_buckets WHERE sent = 0)",
    )
    abstract fun observePendingCount(): Flow<Int>

    @Query("SELECT DISTINCT packageName FROM usage_buckets ORDER BY packageName")
    abstract fun observePackages(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putLabels(rows: List<AppLabelEntity>)

    @Query("SELECT * FROM app_labels")
    abstract suspend fun labels(): List<AppLabelEntity>

    @Query("SELECT * FROM app_labels")
    abstract fun observeLabels(): Flow<List<AppLabelEntity>>

    @Query("DELETE FROM usage_buckets WHERE packageName = :packageName")
    abstract suspend fun deletePackage(packageName: String)

    @Query("DELETE FROM usage_buckets")
    protected abstract suspend fun deleteUsage()

    @Query("DELETE FROM key_buckets")
    protected abstract suspend fun deleteKeys()

    @Query("DELETE FROM screen_buckets")
    protected abstract suspend fun deleteScreen()

    @Query("DELETE FROM app_labels")
    protected abstract suspend fun deleteLabels()

    @Transaction
    open suspend fun deleteAll() {
        deleteUsage()
        deleteKeys()
        deleteScreen()
        deleteLabels()
    }
}
