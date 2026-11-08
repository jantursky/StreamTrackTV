package com.apptiary.streamtracktv.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.apptiary.streamtracktv.core.insights.Goal
import com.apptiary.streamtracktv.core.insights.GoalTarget
import com.apptiary.streamtracktv.data.settings.Settings
import com.apptiary.streamtracktv.data.settings.SettingsRepository
import com.apptiary.streamtracktv.data.settings.SyncRecord
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.repository() =
        SettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope) {
                folder.newFile("settings.preferences_pb").also { it.delete() }
            },
        )

    @Test
    fun `starts with defaults`() =
        runTest {
            assertEquals(Settings(), repository().current())
        }

    @Test
    fun `stores server, interval and exclusions`() =
        runTest {
            val repo = repository()
            repo.setServerUrl("https://stats.example.com")
            repo.setSyncInterval(5)
            repo.setExcluded("com.a.app", true)
            repo.setExcluded("com.b.app", true)
            repo.setExcluded("com.a.app", false)
            val settings = repo.current()
            assertEquals("https://stats.example.com", settings.serverUrl)
            assertEquals(15, settings.syncIntervalMinutes)
            assertEquals(setOf("com.b.app"), settings.excludedPackages)
            repo.setServerUrl(null)
            assertNull(repo.current().serverUrl)
            repo.setLightTheme(true)
            assertEquals(true, repo.current().lightTheme)
            repo.addRejectedRows(1)
            repo.addRejectedRows(2)
            assertEquals(3, repo.current().rejectedRows)
        }

    @Test
    fun `turns limits into goals`() =
        runTest {
            val repo = repository()
            repo.setScreenLimit(180)
            repo.setAppLimit("com.netflix.ninja", 90)
            repo.setAppLimit("com.disney.disneyplus", 30)
            repo.setAppLimit("com.disney.disneyplus", 45)
            repo.setAppLimit("com.gone.app", 10)
            repo.setAppLimit("com.gone.app", null)
            assertEquals(
                listOf(
                    Goal(GoalTarget.ScreenTime, 180 * 60_000L),
                    Goal(GoalTarget.App("com.disney.disneyplus"), 45 * 60_000L),
                    Goal(GoalTarget.App("com.netflix.ninja"), 90 * 60_000L),
                ),
                repo.current().goals,
            )
            repo.setScreenLimit(null)
            assertNull(repo.current().screenLimitMinutes)
        }

    @Test
    fun `remembers the last sync and the last success`() =
        runTest {
            val repo = repository()
            repo.recordSync(SyncRecord(1000, ok = true, message = null))
            repo.recordSync(SyncRecord(2000, ok = false, message = "Server error 503"))
            val settings = repo.current()
            assertEquals(SyncRecord(2000, ok = false, message = "Server error 503"), settings.lastSync)
            assertEquals(1000L, settings.lastSuccessMs)
            repo.recordSync(SyncRecord(3000, ok = true, message = null))
            assertNull(repo.current().lastSync?.message)
            repo.clear()
            assertEquals(Settings(), repo.current())
        }
}
