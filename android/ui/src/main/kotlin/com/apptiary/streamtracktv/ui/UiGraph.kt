package com.apptiary.streamtracktv.ui

import com.apptiary.streamtracktv.collector.AndroidPermissions
import com.apptiary.streamtracktv.collector.PackageManagerAppCatalog
import com.apptiary.streamtracktv.data.UsageRepository
import com.apptiary.streamtracktv.data.credentials.CredentialStore
import com.apptiary.streamtracktv.data.settings.SettingsRepository
import com.apptiary.streamtracktv.sync.PairingSession
import com.apptiary.streamtracktv.sync.SyncEngine
import com.apptiary.streamtracktv.sync.SyncScheduler

/** Facts about this build and device that the screens show or send. */
data class AppInfo(
    val versionName: String,
    val versionCode: Int,
    val defaultServerUrl: String,
    /** Debug builds may talk to a plain-http server on the local network. */
    val allowHttp: Boolean,
    val deviceName: String,
    val model: String,
)

/** Everything the screens need, created once by the application. */
class UiGraph(
    val usage: UsageRepository,
    val settings: SettingsRepository,
    val credentials: CredentialStore,
    val syncEngine: SyncEngine,
    val syncScheduler: SyncScheduler,
    val pairing: PairingSession,
    val permissions: AndroidPermissions,
    val catalog: PackageManagerAppCatalog,
    val collectNow: suspend () -> Unit,
    val appInfo: AppInfo,
    /** Present in debug builds only. */
    val loadDemoWeek: (suspend () -> Unit)?,
)
