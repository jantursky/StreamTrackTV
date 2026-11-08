package com.apptiary.streamtracktv.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apptiary.streamtracktv.core.model.BucketSet
import com.apptiary.streamtracktv.core.model.HourSlot
import com.apptiary.streamtracktv.core.model.KeyBucket
import com.apptiary.streamtracktv.core.model.KeyClass
import com.apptiary.streamtracktv.core.model.ScreenBucket
import com.apptiary.streamtracktv.core.model.UsageBucket
import com.apptiary.streamtracktv.data.db.StreamTrackDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs the bucket bookkeeping on the device's own SQLite, which differs per Android version. */
@RunWith(AndroidJUnit4::class)
class BucketDaoInstrumentedTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext<Context>(),
                StreamTrackDatabase::class.java,
            ).build()
    private val repo = UsageRepository(db.buckets())
    private val slot = HourSlot(LocalDate.parse("2026-11-08"), 19)

    @After
    fun close() = db.close()

    @Test
    fun upsertKeepsSentFlagForUnchangedValues() =
        runTest {
            val buckets =
                BucketSet(
                    usage = listOf(UsageBucket(slot, "com.netflix.ninja", 1000)),
                    screen = listOf(ScreenBucket(slot, 1000)),
                )
            repo.record(buckets)
            repo.markSent(repo.pending())
            repo.record(buckets)
            assertTrue(repo.pending().isEmpty())
            assertEquals(0, repo.observePendingCount().first())
        }

    @Test
    fun keyIncrementsReopenSentRows() =
        runTest {
            repo.addKeyPresses(listOf(KeyBucket(slot, KeyClass.MEDIA, 2)))
            repo.markSent(repo.pending())
            repo.addKeyPresses(listOf(KeyBucket(slot, KeyClass.MEDIA, 1)))
            assertEquals(listOf(KeyBucket(slot, KeyClass.MEDIA, 3)), repo.pending().keys)
        }

    @Test
    fun deleteAllEmptiesEveryTable() =
        runTest {
            repo.record(BucketSet(usage = listOf(UsageBucket(slot, "com.netflix.ninja", 1))))
            repo.saveLabels(mapOf("com.netflix.ninja" to "Netflix"))
            repo.deleteAll()
            assertTrue(repo.pending().isEmpty())
            assertTrue(repo.labels().isEmpty())
        }
}
