package com.jantursky.streamtracktv.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SecurityEventDao {
    @Insert
    protected abstract suspend fun insert(row: SecurityEventEntity): Long

    /** Keeps the newest [keep] rows, sent or not. */
    @Query("DELETE FROM security_events WHERE id NOT IN (SELECT id FROM security_events ORDER BY id DESC LIMIT :keep)")
    protected abstract suspend fun trim(keep: Int)

    /** Adds the row and drops the oldest beyond [keep], in one transaction. */
    @Transaction
    open suspend fun add(row: SecurityEventEntity, keep: Int) {
        insert(row)
        trim(keep)
    }

    @Query("SELECT * FROM security_events WHERE sent = 0 ORDER BY id LIMIT :limit")
    abstract suspend fun pending(limit: Int): List<SecurityEventEntity>

    @Query("SELECT * FROM security_events WHERE sent = 0 ORDER BY id")
    abstract fun observePending(): Flow<List<SecurityEventEntity>>

    @Query("UPDATE security_events SET sent = 1 WHERE id IN (:ids)")
    abstract suspend fun markSent(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM security_events")
    abstract suspend fun count(): Int

    @Query("DELETE FROM security_events")
    abstract suspend fun deleteAll()
}
