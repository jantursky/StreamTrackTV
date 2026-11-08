package com.apptiary.streamtracktv

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.work.WorkManager
import com.apptiary.streamtracktv.collector.AndroidPermissions
import com.apptiary.streamtracktv.collector.AndroidUsageEventSource
import com.apptiary.streamtracktv.collector.CollectorScheduler
import com.apptiary.streamtracktv.collector.PackageManagerAppCatalog
import com.apptiary.streamtracktv.collector.UsageCollector
import com.apptiary.streamtracktv.data.UsageRepository
import com.apptiary.streamtracktv.data.credentials.CredentialStore
import com.apptiary.streamtracktv.data.credentials.KeystoreSecretCipher
import com.apptiary.streamtracktv.data.settings.SettingsRepository
import com.apptiary.streamtracktv.sync.PairingSession
import com.apptiary.streamtracktv.sync.StreamTrackApi
import com.apptiary.streamtracktv.sync.SyncEngine
import com.apptiary.streamtracktv.sync.SyncScheduler
import com.apptiary.streamtracktv.ui.AppInfo
import com.apptiary.streamtracktv.ui.UiGraph
import kotlinx.coroutines.CoroutineScope

/** The one place where the app's objects are created and wired together. */
class AppGraph(context: Context, scope: CoroutineScope) {
    private val catalog = PackageManagerAppCatalog(context)
    private val workManager by lazy { WorkManager.getInstance(context) }

    val usage = UsageRepository.open(context)
    val settings =
        SettingsRepository(
            PreferenceDataStoreFactory.create(scope = scope) { context.preferencesDataStoreFile("settings") },
        )
    val credentials =
        CredentialStore(
            PreferenceDataStoreFactory.create(scope = scope) { context.preferencesDataStoreFile("credentials") },
            KeystoreSecretCipher(),
        )
    val collector = UsageCollector(AndroidUsageEventSource(context), usage, settings, catalog)
    val collectorScheduler by lazy { CollectorScheduler(workManager) }
    val syncScheduler by lazy { SyncScheduler(workManager) }

    private val api = StreamTrackApi.create(BuildConfig.VERSION_NAME)
    val syncEngine = SyncEngine(api, usage, settings, credentials)

    val ui: UiGraph by lazy {
        UiGraph(
            usage = usage,
            settings = settings,
            credentials = credentials,
            syncEngine = syncEngine,
            syncScheduler = syncScheduler,
            pairing = PairingSession(api, settings, credentials),
            permissions = AndroidPermissions(context),
            catalog = catalog,
            collectNow = { collector.collect() },
            appInfo =
                AppInfo(
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE,
                    defaultServerUrl = BuildConfig.DEFAULT_SERVER_URL,
                    allowHttp = BuildConfig.DEBUG,
                    deviceName = deviceName(context),
                    model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                ),
            loadDemoWeek = demoWeekLoader(usage),
        )
    }

    private fun deviceName(context: Context): String =
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() }
            ?: Build.MODEL
}
