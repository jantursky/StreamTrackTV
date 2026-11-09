package com.jantursky.streamtracktv.data

import android.database.sqlite.SQLiteException
import com.jantursky.streamtracktv.core.model.SecurityEvent
import com.jantursky.streamtracktv.core.model.SecurityEventType
import com.jantursky.streamtracktv.core.security.SecurityRecorder
import com.jantursky.streamtracktv.core.wire.WireLimits
import com.jantursky.streamtracktv.data.db.SecurityEventDao
import com.jantursky.streamtracktv.data.db.SecurityEventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The TV's security log: a bounded list of events (see [SecurityEventType]) kept in the database until the
 * server has them. Recording never throws into the screen that records, so a full disk cannot lock the owner
 * out.
 *
 * [scope] must run one coroutine at a time (a single-thread dispatcher), so events are stored in the order
 * they were recorded.
 */
class SecurityLog(
    private val dao: SecurityEventDao,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : SecurityRecorder {
    /** Records [type] now. [value] is clamped to what the server accepts. */
    override fun record(type: SecurityEventType, value: Int?) {
        val row =
            SecurityEventEntity(
                eventId = newId(),
                type = type.wireName,
                atMs = clock(),
                value = value?.coerceIn(0, WireLimits.MAX_EVENT_VALUE),
            )
        scope.launch {
            try {
                dao.add(row, WireLimits.MAX_STORED_EVENTS)
            } catch (_: SQLiteException) {
                // A log that cannot be written must not get in the way of unlocking.
            } catch (_: IllegalStateException) {
                // The database was closed while the app shut down.
            }
        }
    }

    /** Oldest events the server has not accepted yet. */
    suspend fun pending(limit: Int = WireLimits.MAX_EVENTS_PER_BATCH): List<SecurityEvent> =
        dao.pending(limit).mapNotNull { it.toEvent() }

    fun observePending(): Flow<List<SecurityEvent>> = dao.observePending().map { rows -> rows.mapNotNull { it.toEvent() } }

    suspend fun markSent(events: List<SecurityEvent>) {
        if (events.isNotEmpty()) dao.markSent(events.map { it.id })
    }

    suspend fun count(): Int = dao.count()

    suspend fun deleteAll() = dao.deleteAll()

    private fun SecurityEventEntity.toEvent(): SecurityEvent? =
        SecurityEventType.fromWireName(type)?.let { SecurityEvent(id, eventId, it, atMs, value) }
}
