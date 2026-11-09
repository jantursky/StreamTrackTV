package com.jantursky.streamtracktv.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jantursky.streamtracktv.core.model.BucketSet
import com.jantursky.streamtracktv.core.model.HourSlot
import com.jantursky.streamtracktv.core.model.SecurityEventType
import com.jantursky.streamtracktv.core.model.UsageBucket
import com.jantursky.streamtracktv.core.wire.WireLimits
import com.jantursky.streamtracktv.data.db.StreamTrackDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SecurityLogTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db =
        Room
            .inMemoryDatabaseBuilder(context, StreamTrackDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(Runnable::run)
            .setTransactionExecutor(Runnable::run)
            .build()
    private val scope = TestScope(StandardTestDispatcher())
    private var time = 1_000L
    private var ids = 0
    private val log = SecurityLog(db.securityEvents(), scope, { time++ }, { "id-${ids++}" })

    @After
    fun close() = db.close()

    @Test
    fun `events come back oldest first with their time and value`() =
        scope.runTest {
            log.record(SecurityEventType.APP_OPENED)
            log.record(SecurityEventType.PIN_WRONG, 3)
            log.record(SecurityEventType.PIN_WAIT_STARTED, 30)
            advanceUntilIdle()
            val pending = log.pending()
            assertEquals(
                listOf(SecurityEventType.APP_OPENED, SecurityEventType.PIN_WRONG, SecurityEventType.PIN_WAIT_STARTED),
                pending.map { it.type },
            )
            assertEquals(listOf(null, 3, 30), pending.map { it.value })
            assertEquals(listOf(1_000L, 1_001L, 1_002L), pending.map { it.atMs })
            assertEquals(listOf("id-0", "id-1", "id-2"), pending.map { it.eventId })
        }

    @Test
    fun `an event is marked sent only when asked, and sent events are not pending`() =
        scope.runTest {
            repeat(3) { log.record(SecurityEventType.UNLOCKED) }
            advanceUntilIdle()
            val first = log.pending(limit = 2)
            assertEquals(2, first.size)
            log.markSent(first)
            assertEquals(listOf("id-2"), log.pending().map { it.eventId })
            assertEquals(3, log.count())
        }

    @Test
    fun `a pending batch is capped by the limit`() =
        scope.runTest {
            repeat(5) { log.record(SecurityEventType.LOCK_SHOWN) }
            advanceUntilIdle()
            assertEquals(WireLimits.MAX_EVENTS_PER_BATCH, 100)
            assertEquals(3, log.pending(limit = 3).size)
        }

    @Test
    fun `the log keeps the newest 500 events, sent or not`() =
        scope.runTest {
            repeat(WireLimits.MAX_STORED_EVENTS + 1) { log.record(SecurityEventType.APP_OPENED) }
            advanceUntilIdle()
            assertEquals(WireLimits.MAX_STORED_EVENTS, log.count())
            val all = log.pending(limit = 1000)
            assertEquals("id-1", all.first().eventId)
            assertEquals("id-${WireLimits.MAX_STORED_EVENTS}", all.last().eventId)
        }

    @Test
    fun `a value outside what the server takes is clamped`() =
        scope.runTest {
            log.record(SecurityEventType.PIN_WRONG, Int.MAX_VALUE)
            log.record(SecurityEventType.PIN_WRONG, -5)
            advanceUntilIdle()
            assertEquals(listOf(WireLimits.MAX_EVENT_VALUE, 0), log.pending().map { it.value })
        }

    @Test
    fun `recording on a closed database does not throw`() =
        scope.runTest {
            db.close()
            log.record(SecurityEventType.APP_OPENED)
            advanceUntilIdle()
        }

    @Test
    fun `deleting all empties the log`() =
        scope.runTest {
            log.record(SecurityEventType.APP_OPENED)
            advanceUntilIdle()
            log.deleteAll()
            assertEquals(0, log.count())
        }

    /** Builds the exported version 1 database and opens it with the current schema, as an update would. */
    @Test
    fun `version 1 data survives the migration to the security log`() =
        scope.runTest {
            val name = "migration.db"
            val schema =
                JSONObject(File("schemas/com.jantursky.streamtracktv.data.db.StreamTrackDatabase/1.json").readText())
                    .getJSONObject("database")
            val path = context.getDatabasePath(name).also { it.parentFile?.mkdirs() }
            SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
                old.execSQL(
                    "INSERT INTO usage_buckets (day, hour, packageName, foregroundMs, sent) VALUES ('2026-11-08', 19, 'a.b', 5, 0)",
                )
                old.version = 1
            }
            val migrated =
                Room
                    .databaseBuilder(context, StreamTrackDatabase::class.java, name)
                    .allowMainThreadQueries()
                    .setQueryExecutor(Runnable::run)
                    .setTransactionExecutor(Runnable::run)
                    .build()
            try {
                val slot = HourSlot(LocalDate.parse("2026-11-08"), 19)
                assertEquals(
                    BucketSet(usage = listOf(UsageBucket(slot, "a.b", 5))),
                    UsageRepository(migrated.buckets()).pending(),
                )
                val migratedLog = SecurityLog(migrated.securityEvents(), scope, { 1L }, { "x" })
                migratedLog.record(SecurityEventType.APP_OPENED)
                advanceUntilIdle()
                assertTrue(migratedLog.pending().single().type == SecurityEventType.APP_OPENED)
            } finally {
                migrated.close()
            }
        }
}
