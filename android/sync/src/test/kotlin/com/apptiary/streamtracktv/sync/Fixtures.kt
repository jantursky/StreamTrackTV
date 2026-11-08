package com.apptiary.streamtracktv.sync

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.apptiary.streamtracktv.data.UsageRepository
import com.apptiary.streamtracktv.data.credentials.CredentialStore
import com.apptiary.streamtracktv.data.credentials.SecretCipher
import com.apptiary.streamtracktv.data.db.StreamTrackDatabase
import com.apptiary.streamtracktv.data.settings.SettingsRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import java.io.File

/** Reversible stand-in for the Keystore cipher, which Robolectric cannot provide. */
class XorCipher : SecretCipher {
    override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x33).toByte() }.toByteArray()

    override fun decrypt(sealed: ByteArray) = encrypt(sealed)
}

/** Real Room (in memory) and real DataStore files, as the app uses them. */
class Stores(scope: CoroutineScope, dir: File) {
    val db: StreamTrackDatabase =
        Room
            .inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext<Context>(),
                StreamTrackDatabase::class.java,
            ).allowMainThreadQueries()
            .build()
    val usage = UsageRepository(db.buckets())

    init {
        scope.coroutineContext.job.invokeOnCompletion { db.close() }
    }

    val settings =
        SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") })
    val credentials =
        CredentialStore(
            PreferenceDataStoreFactory.create(scope = scope) {
                File(dir, "credentials.preferences_pb")
            },
            XorCipher(),
        )
}

typealias Answer = suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData

/** A scripted server: answers requests in order and records them. */
class FakeServer(private val answers: List<Answer>) {
    val requests = mutableListOf<HttpRequestData>()

    val api =
        StreamTrackApi(
            StreamTrackApi.httpClient(
                MockEngine { request ->
                    requests += request
                    val answer =
                        answers.getOrNull(requests.lastIndex)
                            ?: error("Unexpected request #${requests.size} to ${request.url}")
                    answer(request)
                },
                appVersion = "test",
            ),
        )

    fun bodyOf(index: Int): String = (requests[index].body as TextContent).text
}

fun json(body: String, status: HttpStatusCode = HttpStatusCode.OK, vararg headers: Pair<String, String>): Answer =
    {
        val all = listOf(HttpHeaders.ContentType to "application/json") + headers
        respond(body, status, headersOf(*all.map { (name, value) -> name to listOf(value) }.toTypedArray()))
    }
