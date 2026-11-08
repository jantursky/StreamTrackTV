package com.apptiary.streamtracktv.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class UsageRepositoryTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext<Context>(),
                StreamTrackDatabase::class.java,
            ).allowMainThreadQueries()
            .build()
    private val repo = UsageRepository(db.buckets())

    private val day = LocalDate.parse("2026-11-08")
    private val slot = HourSlot(day, 19)
    private val netflix = "com.netflix.ninja"

    @After
    fun close() = db.close()

    @Test
    fun `recording the same absolute value twice leaves it sent`() =
        runTest {
            val buckets =
                BucketSet(usage = listOf(UsageBucket(slot, netflix, 1000)), screen = listOf(ScreenBucket(slot, 2000)))
            repo.record(buckets)
            assertEquals(buckets, repo.pending())
            repo.markSent(repo.pending())
            repo.record(buckets)
            assertTrue(repo.pending().isEmpty())
            assertEquals(0, repo.observePendingCount().first())
        }

    @Test
    fun `a changed value becomes pending again with the new value`() =
        runTest {
            repo.record(BucketSet(usage = listOf(UsageBucket(slot, netflix, 1000))))
            repo.markSent(repo.pending())
            repo.record(BucketSet(usage = listOf(UsageBucket(slot, netflix, 1500))))
            assertEquals(listOf(UsageBucket(slot, netflix, 1500)), repo.pending().usage)
        }

    @Test
    fun `marking sent skips rows that changed after they were read`() =
        runTest {
            repo.record(BucketSet(screen = listOf(ScreenBucket(slot, 1000))))
            val inFlight = repo.pending()
            repo.record(BucketSet(screen = listOf(ScreenBucket(slot, 1200))))
            repo.markSent(inFlight)
            assertEquals(listOf(ScreenBucket(slot, 1200)), repo.pending().screen)
        }

    @Test
    fun `key presses add up and reopen a sent row`() =
        runTest {
            repo.addKeyPresses(listOf(KeyBucket(slot, KeyClass.DPAD, 3), KeyBucket(slot, KeyClass.BACK, 0)))
            repo.addKeyPresses(listOf(KeyBucket(slot, KeyClass.DPAD, 2)))
            assertEquals(listOf(KeyBucket(slot, KeyClass.DPAD, 5)), repo.pending().keys)
            repo.markSent(repo.pending())
            repo.addKeyPresses(listOf(KeyBucket(slot, KeyClass.DPAD, 1)))
            assertEquals(listOf(KeyBucket(slot, KeyClass.DPAD, 6)), repo.pending().keys)
        }

    @Test
    fun `pending rows come oldest first and respect the limit`() =
        runTest {
            val rows = (0..5).map { UsageBucket(HourSlot(day, 5 - it), netflix, 1000L + it) }
            repo.record(BucketSet(usage = rows))
            assertEquals(listOf(0, 1, 2), repo.pending(limit = 3).usage.map { it.slot.hour })
        }

    @Test
    fun `observes a range of days in slot order`() =
        runTest {
            repo.record(
                BucketSet(
                    usage = listOf(UsageBucket(HourSlot(day, 20), "com.b.app", 1), UsageBucket(slot, "com.a.app", 1)),
                    screen = listOf(ScreenBucket(HourSlot(day.minusDays(3), 1), 1), ScreenBucket(slot, 1)),
                ),
            )
            repo.addKeyPresses(listOf(KeyBucket(slot, KeyClass.SELECT, 1), KeyBucket(slot, KeyClass.DPAD, 1)))
            val seen = repo.observeDays(day.minusDays(1), day).first()
            assertEquals(listOf("com.a.app", "com.b.app"), seen.usage.map { it.packageName })
            assertEquals(listOf(KeyClass.DPAD, KeyClass.SELECT), seen.keys.map { it.keyClass })
            assertEquals(listOf(slot), seen.screen.map { it.slot })
        }

    @Test
    fun `labels, package deletion and wiping everything`() =
        runTest {
            repo.saveLabels(mapOf(netflix to "Netflix"))
            repo.saveLabels(mapOf(netflix to "Netflix TV"))
            assertEquals(mapOf(netflix to "Netflix TV"), repo.labels())
            assertEquals(mapOf(netflix to "Netflix TV"), repo.observeLabels().first())

            repo.record(BucketSet(usage = listOf(UsageBucket(slot, netflix, 1), UsageBucket(slot, "com.other.app", 1))))
            assertEquals(listOf("com.netflix.ninja", "com.other.app"), repo.observePackages().first())
            repo.deletePackage("com.other.app")
            assertEquals(listOf(netflix), repo.observePackages().first())

            repo.addKeyPresses(listOf(KeyBucket(slot, KeyClass.DPAD, 1)))
            repo.deleteAll()
            assertTrue(repo.pending().isEmpty())
            assertTrue(repo.labels().isEmpty())
        }
}
