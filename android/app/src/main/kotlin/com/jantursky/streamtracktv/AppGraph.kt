package com.jantursky.streamtracktv

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.work.WorkManager
import com.jantursky.streamtracktv.collector.AndroidPermissions
import com.jantursky.streamtracktv.collector.AndroidUsageEventSource
import com.jantursky.streamtracktv.collector.CollectorScheduler
import com.jantursky.streamtracktv.collector.PackageManagerAppCatalog
import com.jantursky.streamtracktv.collector.UsageCollector
import com.jantursky.streamtracktv.data.LocalData
import com.jantursky.streamtracktv.data.credentials.CredentialStore
import com.jantursky.streamtracktv.data.credentials.KeystoreSecretCipher
import com.jantursky.streamtracktv.data.pin.PinStore
import com.jantursky.streamtracktv.data.settings.SettingsRepository
import com.jantursky.streamtracktv.sync.PairingSession
import com.jantursky.streamtracktv.sync.PinResetSession
import com.jantursky.streamtracktv.sync.StreamTrackApi
import com.jantursky.streamtracktv.sync.SyncEngine
import com.jantursky.streamtracktv.sync.SyncScheduler
import com.jantursky.streamtracktv.ui.AppInfo
import com.jantursky.streamtracktv.ui.PinLockGraph
import com.jantursky.streamtracktv.ui.UiGraph
import com.jantursky.streamtracktv.ui.lock.LockSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob

/** The one place where the app's objects are created and wired together. */
class AppGraph(context: Context, scope: CoroutineScope) {
    private val catalog = PackageManagerAppCatalog(context)
    private val workManager by lazy { WorkManager.getInstance(context) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val local =
        LocalData.open(context, CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1)))
    val usage = local.usage
    val securityLog = local.securityLog
    val settings =
        SettingsRepository(
            PreferenceDataStoreFactory.create(scope = scope) { context.preferencesDataStoreFile("settings") },
        )
    val credentials =
        CredentialStore(
            PreferenceDataStoreFactory.create(scope = scope) { context.preferencesDataStoreFile("credentials") },
            KeystoreSecretCipher(),
        )
    val pin =
        PinStore(
            PreferenceDataStoreFactory.create(scope = scope) { context.preferencesDataStoreFile("pin") },
            recorder = securityLog,
        )
    private val lock = LockSession(pin, scope, securityLog)
    val collector = UsageCollector(AndroidUsageEventSource(context), usage, settings, catalog)
    val collectorScheduler by lazy { CollectorScheduler(workManager) }
    val syncScheduler by lazy { SyncScheduler(workManager) }

    private val api = StreamTrackApi.create(BuildConfig.VERSION_NAME)
    val syncEngine = SyncEngine(api, usage, settings, credentials, securityLog)

    val ui: UiGraph by lazy {
        UiGraph(
            usage = usage,
            settings = settings,
            credentials = credentials,
            syncEngine = syncEngine,
            syncScheduler = syncScheduler,
            pairing = PairingSession(api, settings, credentials),
            securityLog = securityLog,
            pinLock =
                PinLockGraph(
                    pin,
                    lock,
                    PinResetSession(api, settings, credentials, pin, recorder = securityLog),
                    securityLog,
                ),
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
