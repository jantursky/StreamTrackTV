package com.jantursky.streamtracktv.collector

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jantursky.streamtracktv.core.aggregation.DeviceEvent
import com.jantursky.streamtracktv.core.model.HourSlot
import com.jantursky.streamtracktv.core.model.ScreenBucket
import com.jantursky.streamtracktv.core.model.UsageBucket
import com.jantursky.streamtracktv.data.UsageRepository
import com.jantursky.streamtracktv.data.db.StreamTrackDatabase
import com.jantursky.streamtracktv.data.settings.SettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class UsageCollectorTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val zone = ZoneId.of("Europe/Bratislava")
    private val db =
        Room
            .inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext<Context>(),
                StreamTrackDatabase::class.java,
            ).allowMainThreadQueries()
            .build()
    private val repository = UsageRepository(db.buckets())

    private fun at(local: String) =
        LocalDateTime
            .parse(local)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    @After
    fun close() = db.close()

    @Test
    fun `stores absolute buckets and labels, and asks for events with a lookback`() =
        runTest {
            val settings =
                SettingsRepository(
                    PreferenceDataStoreFactory.create(scope = backgroundScope) {
                        folder.newFile("s.preferences_pb").also { it.delete() }
                    },
                )
            settings.setExcluded("com.secret.app", true)
            var requested: Pair<Long, Long>? = null
            val events =
                listOf(
                    DeviceEvent.ScreenOn(at("2026-11-08T18:50")),
                    DeviceEvent.ActivityResumed(at("2026-11-08T19:00"), "com.netflix.ninja", "Main"),
                    DeviceEvent.ActivityResumed(at("2026-11-08T19:30"), "com.secret.app", "Main"),
                )
            val collector =
                UsageCollector(
                    source = { from, to ->
                        requested = from to to
                        events
                    },
                    repository = repository,
                    settings = settings,
                    catalog = { pkg -> if (pkg == "com.netflix.ninja") "Netflix" else null },
                    now = { at("2026-11-08T19:45") },
                    zone = { zone },
                )

            val first = collector.collect()
            val slot = HourSlot(LocalDate.parse("2026-11-08"), 19)
            assertEquals(listOf(UsageBucket(slot, "com.netflix.ninja", 30 * 60_000L)), first.usage)
            assertEquals(
                listOf(
                    ScreenBucket(HourSlot(LocalDate.parse("2026-11-08"), 18), 10 * 60_000L),
                    ScreenBucket(
                        slot,
                        45 * 60_000L,
                    ),
                ),
                first.screen,
            )
            assertEquals(at("2026-11-06T18:00") to at("2026-11-08T19:45"), requested)
            assertEquals(mapOf("com.netflix.ninja" to "Netflix"), repository.labels())

            repository.markSent(repository.pending())
            collector.collect()
            assertTrue(repository.pending().isEmpty(), "Collecting the same events again must not create pending rows")
        }
}
