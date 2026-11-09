package com.jantursky.streamtracktv.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.jantursky.streamtracktv.core.insights.Goal
import com.jantursky.streamtracktv.core.insights.GoalTarget
import com.jantursky.streamtracktv.core.model.SyncInterval
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Result of the last sync run, shown on the dashboard. */
data class SyncRecord(val atMs: Long, val ok: Boolean, val message: String?)

data class Settings(
    val serverUrl: String? = null,
    val syncIntervalMinutes: Int = SyncInterval.DEFAULT_MINUTES,
    val excludedPackages: Set<String> = emptySet(),
    val screenLimitMinutes: Int? = null,
    val appLimitsMinutes: Map<String, Int> = emptyMap(),
    val lastSync: SyncRecord? = null,
    val lastSuccessMs: Long? = null,
    val lightTheme: Boolean = false,
    /** Rows the server refused one by one and that were set aside instead of being sent again. */
    val rejectedRows: Int = 0,
) {
    val goals: List<Goal>
        get() =
            listOfNotNull(screenLimitMinutes?.let { Goal(GoalTarget.ScreenTime, it * MINUTE_MS) }) +
                appLimitsMinutes.toSortedMap().map { (pkg, minutes) -> Goal(GoalTarget.App(pkg), minutes * MINUTE_MS) }
}

/** User settings in a Preferences DataStore. The device token lives in [com.jantursky.streamtracktv.data.credentials.CredentialStore]. */
class SettingsRepository(private val store: DataStore<Preferences>) {
    val settings: Flow<Settings> = store.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    suspend fun setServerUrl(url: String?) =
        store.edit { prefs ->
            if (url == null) prefs.remove(SERVER_URL) else prefs[SERVER_URL] = url
        }

    suspend fun setSyncInterval(minutes: Int) = store.edit { it[SYNC_INTERVAL] = SyncInterval.clamp(minutes) }

    suspend fun setExcluded(packageName: String, excluded: Boolean) =
        store.edit { prefs ->
            val current = prefs[EXCLUDED].orEmpty()
            prefs[EXCLUDED] = if (excluded) current + packageName else current - packageName
        }

    /** Daily screen time limit in minutes; null removes it. */
    suspend fun setScreenLimit(minutes: Int?) =
        store.edit { prefs ->
            if (minutes == null) prefs.remove(SCREEN_LIMIT) else prefs[SCREEN_LIMIT] = minutes
        }

    /** Daily limit for one app in minutes; null removes it. */
    suspend fun setAppLimit(packageName: String, minutes: Int?) =
        store.edit { prefs ->
            val limits = decodeLimits(prefs[APP_LIMITS].orEmpty()) - packageName
            val updated = if (minutes == null) limits else limits + (packageName to minutes)
            prefs[APP_LIMITS] = updated.map { (pkg, value) -> "$pkg=$value" }.toSet()
        }

    suspend fun setLightTheme(light: Boolean) = store.edit { it[LIGHT_THEME] = light }

    suspend fun recordSync(record: SyncRecord) =
        store.edit { prefs ->
            prefs[LAST_SYNC_AT] = record.atMs
            prefs[LAST_SYNC_OK] = if (record.ok) 1 else 0
            if (record.message == null) prefs.remove(LAST_SYNC_MESSAGE) else prefs[LAST_SYNC_MESSAGE] = record.message
            if (record.ok) prefs[LAST_SUCCESS_AT] = record.atMs
        }

    suspend fun addRejectedRows(count: Int) = store.edit { it[REJECTED_ROWS] = (it[REJECTED_ROWS] ?: 0) + count }

    /** Back to defaults, keeping nothing. */
    suspend fun clear() = store.edit { it.clear() }

    private fun Preferences.toSettings() =
        Settings(
            serverUrl = this[SERVER_URL],
            syncIntervalMinutes = this[SYNC_INTERVAL] ?: SyncInterval.DEFAULT_MINUTES,
            excludedPackages = this[EXCLUDED].orEmpty(),
            screenLimitMinutes = this[SCREEN_LIMIT],
            appLimitsMinutes = decodeLimits(this[APP_LIMITS].orEmpty()),
            lastSync = this[LAST_SYNC_AT]?.let { SyncRecord(it, this[LAST_SYNC_OK] == 1, this[LAST_SYNC_MESSAGE]) },
            lastSuccessMs = this[LAST_SUCCESS_AT],
            lightTheme = this[LIGHT_THEME] ?: false,
            rejectedRows = this[REJECTED_ROWS] ?: 0,
        )

    private companion object {
        val SERVER_URL = stringPreferencesKey("server_url")
        val SYNC_INTERVAL = intPreferencesKey("sync_interval_minutes")
        val EXCLUDED = stringSetPreferencesKey("excluded_packages")
        val SCREEN_LIMIT = intPreferencesKey("screen_limit_minutes")
        val APP_LIMITS = stringSetPreferencesKey("app_limits")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val LAST_SYNC_OK = intPreferencesKey("last_sync_ok")
        val LAST_SYNC_MESSAGE = stringPreferencesKey("last_sync_message")
        val LAST_SUCCESS_AT = longPreferencesKey("last_success_at")
        val LIGHT_THEME = booleanPreferencesKey("light_theme")
        val REJECTED_ROWS = intPreferencesKey("rejected_rows")

        /** Entries look like `com.netflix.ninja=90`; anything else is ignored. */
        fun decodeLimits(entries: Set<String>): Map<String, Int> =
            entries
                .mapNotNull { entry ->
                    val pkg = entry.substringBeforeLast('=', "")
                    val minutes = entry.substringAfterLast('=').toIntOrNull()
                    if (pkg.isNotEmpty() && minutes != null && minutes > 0) pkg to minutes else null
                }.toMap()
    }
}

private const val MINUTE_MS = 60_000L
