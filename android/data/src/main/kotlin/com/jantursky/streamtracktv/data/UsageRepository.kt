package com.jantursky.streamtracktv.data

import android.content.Context
import com.jantursky.streamtracktv.core.model.BucketSet
import com.jantursky.streamtracktv.core.model.KeyBucket
import com.jantursky.streamtracktv.core.model.ScreenBucket
import com.jantursky.streamtracktv.core.model.UsageBucket
import com.jantursky.streamtracktv.core.wire.WireLimits
import com.jantursky.streamtracktv.data.db.AppLabelEntity
import com.jantursky.streamtracktv.data.db.BucketDao
import com.jantursky.streamtracktv.data.db.StreamTrackDatabase
import com.jantursky.streamtracktv.data.db.toEntity
import com.jantursky.streamtracktv.data.db.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Local store of hourly buckets and app labels, in domain types. */
class UsageRepository(private val dao: BucketDao) {
    /** Absolute foreground and screen-on values; only changed rows become pending again. */
    suspend fun record(buckets: BucketSet) {
        dao.upsertUsage(buckets.usage.map { it.toEntity() })
        dao.upsertScreen(buckets.screen.map { it.toEntity() })
    }

    /** Key presses since the last call. */
    suspend fun addKeyPresses(increments: List<KeyBucket>) {
        dao.addKeyCounts(increments.map { it.toEntity() })
    }

    suspend fun saveLabels(labels: Map<String, String>) {
        dao.putLabels(labels.map { (pkg, label) -> AppLabelEntity(pkg, label) })
    }

    suspend fun labels(): Map<String, String> = dao.labels().associate { it.packageName to it.label }

    fun observeLabels(): Flow<Map<String, String>> =
        dao.observeLabels().map { rows ->
            rows.associate {
                it.packageName to
                    it.label
            }
        }

    /** Unsent rows, at most [limit] per kind, oldest first. */
    suspend fun pending(limit: Int = WireLimits.MAX_ROWS_PER_KIND + 1): BucketSet =
        BucketSet(
            usage = dao.pendingUsage(limit).map { it.toModel() },
            keys = dao.pendingKeys(limit).mapNotNull { it.toModel() },
            screen = dao.pendingScreen(limit).map { it.toModel() },
        )

    /** Marks rows as uploaded, unless their stored value changed since they were read. */
    suspend fun markSent(rows: BucketSet) {
        dao.markSent(
            usage = rows.usage.map { it.toEntity() },
            keys = rows.keys.map { it.toEntity() },
            screen = rows.screen.map { it.toEntity() },
        )
    }

    fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    /** All buckets of the days from [from] to [to], inclusive. */
    fun observeDays(from: LocalDate, to: LocalDate): Flow<BucketSet> {
        val start = from.toString()
        val end = to.toString()
        return combine(
            dao.observeUsage(start, end),
            dao.observeKeys(start, end),
            dao.observeScreen(start, end),
        ) { usage, keys, screen ->
            BucketSet(
                usage =
                    usage.map { it.toModel() }.sortedWith(
                        compareBy<UsageBucket> { it.slot }.thenBy { it.packageName },
                    ),
                keys =
                    keys.mapNotNull { it.toModel() }.sortedWith(
                        compareBy<KeyBucket> { it.slot }.thenBy { it.keyClass },
                    ),
                screen = screen.map { it.toModel() }.sortedBy(ScreenBucket::slot),
            )
        }
    }

    /** Packages with recorded foreground time. */
    fun observePackages(): Flow<List<String>> = dao.observePackages()

    /** Forgets an app's foreground time, used when the app is excluded. */
    suspend fun deletePackage(packageName: String) = dao.deletePackage(packageName)

    suspend fun deleteAll() = dao.deleteAll()

    companion object {
        /** The app's on-disk store. */
        fun open(context: Context): UsageRepository = UsageRepository(StreamTrackDatabase.create(context).buckets())
    }
}
