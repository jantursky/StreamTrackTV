package com.jantursky.streamtracktv.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.jantursky.streamtracktv.core.model.BucketSet
import com.jantursky.streamtracktv.core.model.HourSlot
import com.jantursky.streamtracktv.core.model.ScreenBucket
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SyncWorkerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val retries = mutableListOf<Long>()

    private suspend fun TestScope.run(answer: Answer, collect: suspend () -> Unit = {}): ListenableWorker.Result {
        val stores = Stores(backgroundScope, folder.newFolder())
        stores.settings.setServerUrl("https://tv.example.com")
        stores.credentials.save("t".repeat(64))
        stores.usage.record(BucketSet(screen = listOf(ScreenBucket(HourSlot(LocalDate.parse("2026-11-08"), 1), 1000))))
        val engine = SyncEngine(FakeServer(listOf(answer)).api, stores.usage, stores.settings, stores.credentials, stores.securityLog)
        val factory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ) =
                    SyncWorker(appContext, workerParameters, collect, engine, retries::add)
            }
        return TestListenableWorkerBuilder<SyncWorker>(context).setWorkerFactory(factory).build().doWork()
    }

    private val ok = json("""{"accepted":{"usage":0,"keys":0,"screen":1},"serverTime":"2026-11-08T19:15:01Z"}""")

    @Test
    fun `succeeds after an upload even when collection is not allowed`() =
        runTest {
            assertEquals(ListenableWorker.Result.success(), run(ok) { throw SecurityException("no usage access") })
        }

    @Test
    fun `retries on server errors and fails on rejected data`() =
        runTest {
            assertEquals(ListenableWorker.Result.retry(), run(json("{}", HttpStatusCode.ServiceUnavailable)))
            assertEquals(ListenableWorker.Result.failure(), run(json("{}", HttpStatusCode.Forbidden)))
        }

    @Test
    fun `a Retry-After schedules the next attempt instead of backing off`() =
        runTest {
            val limited = json("{}", HttpStatusCode.TooManyRequests, HttpHeaders.RetryAfter to "120")
            assertEquals(ListenableWorker.Result.success(), run(limited))
            assertEquals(listOf(120L), retries)
            assertEquals(ListenableWorker.Result.retry(), run(json("{}", HttpStatusCode.TooManyRequests)))
            assertEquals(listOf(120L), retries)
        }

    @Test
    fun `the retry is one delayed unique request`() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        val workManager = WorkManager.getInstance(context)
        val scheduler = SyncScheduler(workManager)
        scheduler.retryAfter(3600)
        scheduler.retryAfter(1)
        val infos = workManager.getWorkInfosForUniqueWork(SyncScheduler.RETRY_NAME).get()
        assertEquals(listOf(30_000L, 3_600_000L), infos.map { it.initialDelayMillis }.sorted())
        assertTrue(infos.all { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED })
        scheduler.cancel()
        assertTrue(workManager.getWorkInfosForUniqueWork(SyncScheduler.RETRY_NAME).get().all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun `a revoked device ends the run quietly`() =
        runTest {
            assertEquals(ListenableWorker.Result.success(), run(json("{}", HttpStatusCode.Unauthorized)))
        }
}
