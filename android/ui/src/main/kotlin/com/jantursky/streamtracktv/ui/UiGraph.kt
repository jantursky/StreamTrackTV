package com.jantursky.streamtracktv.ui

import com.jantursky.streamtracktv.collector.AndroidPermissions
import com.jantursky.streamtracktv.collector.PackageManagerAppCatalog
import com.jantursky.streamtracktv.core.security.SecurityRecorder
import com.jantursky.streamtracktv.data.SecurityLog
import com.jantursky.streamtracktv.data.UsageRepository
import com.jantursky.streamtracktv.data.credentials.CredentialStore
import com.jantursky.streamtracktv.data.pin.PinStore
import com.jantursky.streamtracktv.data.settings.SettingsRepository
import com.jantursky.streamtracktv.sync.PairingSession
import com.jantursky.streamtracktv.sync.PinResetSession
import com.jantursky.streamtracktv.sync.SyncEngine
import com.jantursky.streamtracktv.sync.SyncScheduler
import com.jantursky.streamtracktv.ui.lock.LockSession

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

/** The PIN lock: where the PIN is kept, whether the app is locked now, and the reset through the web. */
class PinLockGraph(
    val store: PinStore,
    val session: LockSession,
    val reset: PinResetSession,
    val recorder: SecurityRecorder,
)

/** Everything the screens need, created once by the application. */
@Suppress("LongParameterList") // a plain holder of the app's objects; grouping them further only adds indirection
class UiGraph(
    val usage: UsageRepository,
    val settings: SettingsRepository,
    val credentials: CredentialStore,
    val syncEngine: SyncEngine,
    val syncScheduler: SyncScheduler,
    val pairing: PairingSession,
    val pinLock: PinLockGraph,
    val securityLog: SecurityLog,
    val permissions: AndroidPermissions,
    val catalog: PackageManagerAppCatalog,
    val collectNow: suspend () -> Unit,
    val appInfo: AppInfo,
    /** Present in debug builds only. */
    val loadDemoWeek: (suspend () -> Unit)?,
)
