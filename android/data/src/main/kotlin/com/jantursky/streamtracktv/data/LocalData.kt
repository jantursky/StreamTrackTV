package com.jantursky.streamtracktv.data

import android.content.Context
import com.jantursky.streamtracktv.data.db.StreamTrackDatabase
import kotlinx.coroutines.CoroutineScope

/**
 * The app's on-disk database with what lives in it. Usage and the security log share one database
 * instance; Room must not be opened twice on the same file.
 */
class LocalData private constructor(database: StreamTrackDatabase, logScope: CoroutineScope) {
    val usage = UsageRepository(database.buckets())
    val securityLog = SecurityLog(database.securityEvents(), logScope)

    companion object {
        /** [logScope] must run one coroutine at a time, so events are stored in the order they happened. */
        fun open(context: Context, logScope: CoroutineScope): LocalData =
            LocalData(StreamTrackDatabase.create(context), logScope)
    }
}
