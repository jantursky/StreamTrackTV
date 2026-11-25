package com.jantursky.streamtracktv.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.jantursky.streamtracktv.core.insights.Goal
import com.jantursky.streamtracktv.core.insights.GoalTarget
import com.jantursky.streamtracktv.data.settings.Settings
import com.jantursky.streamtracktv.data.settings.SettingsRepository
import com.jantursky.streamtracktv.data.settings.SyncRecord
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
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
    fun `stores weekend limits, bedtime, reminder switch and the shown record`() =
        runTest {
            val repo = repository()
            repo.setScreenLimitWeekend(240)
            repo.setAppLimitWeekend("com.netflix.ninja", 120)
            repo.setAppLimitWeekend("com.gone.app", 30)
            repo.setAppLimitWeekend("com.gone.app", null)
            repo.setBedtime(22 * 60, 7 * 60)
            repo.setRemindersEnabled(false)
            repo.recordShownReminders(setOf("2026-11-25|screen|80|180"), bedtimeMs = 5_000L)
            val settings = repo.current()
            assertEquals(240, settings.screenLimitWeekendMinutes)
            assertEquals(mapOf("com.netflix.ninja" to 120), settings.appLimitsWeekendMinutes)
            assertEquals(22 * 60, settings.bedtimeStartMinute)
            assertEquals(7 * 60, settings.bedtimeEndMinute)
            assertEquals(false, settings.remindersEnabled)
            assertEquals(setOf("2026-11-25|screen|80|180"), settings.shownReminders)
            assertEquals(5_000L, settings.lastBedtimeReminderMs)
            repo.setBedtime(null, 7 * 60)
            assertNull(repo.current().bedtimeStartMinute)
            assertNull(repo.current().bedtimeEndMinute)
            repo.recordShownReminders(emptySet(), bedtimeMs = null)
            assertNull(repo.current().lastBedtimeReminderMs)
        }

    @Test
    fun `limits saved before weekend values read as base values`() =
        runTest {
            val repo = repository()
            repo.setScreenLimit(120)
            repo.setAppLimit("com.netflix.ninja", 90)
            val settings = repo.current()
            assertEquals(120, settings.screenLimitMinutes)
            assertNull(settings.screenLimitWeekendMinutes)
            assertEquals(emptyMap(), settings.appLimitsWeekendMinutes)
            assertEquals(true, settings.remindersEnabled)
            assertEquals(emptySet(), settings.shownReminders)
        }

    @Test
    fun `goalsFor uses the weekend value on Saturday and Sunday only`() {
        val settings =
            Settings(
                screenLimitMinutes = 120,
                screenLimitWeekendMinutes = 240,
                appLimitsMinutes = mapOf("com.netflix.ninja" to 60, "com.disney.disneyplus" to 30),
                appLimitsWeekendMinutes = mapOf("com.netflix.ninja" to 90),
            )
        val base = settings.goals
        val weekend =
            listOf(
                Goal(GoalTarget.ScreenTime, 240 * 60_000L),
                Goal(GoalTarget.App("com.disney.disneyplus"), 30 * 60_000L),
                Goal(GoalTarget.App("com.netflix.ninja"), 90 * 60_000L),
            )
        assertEquals(base, settings.goalsFor(LocalDate.of(2026, 11, 27))) // Friday
        assertEquals(weekend, settings.goalsFor(LocalDate.of(2026, 11, 28))) // Saturday
        assertEquals(weekend, settings.goalsFor(LocalDate.of(2026, 11, 29))) // Sunday
        assertEquals(base, settings.goalsFor(LocalDate.of(2026, 11, 30))) // Monday
    }

    @Test
    fun `a limit without a weekend value keeps its base value on the weekend`() {
        val settings = Settings(screenLimitMinutes = 120, appLimitsMinutes = mapOf("com.a.app" to 15))
        assertEquals(settings.goals, settings.goalsFor(LocalDate.of(2026, 11, 28)))
        assertEquals(settings.goals, settings.goalsFor(LocalDate.of(2026, 11, 29)))
    }

    @Test
    fun `clear removes the new settings too`() =
        runTest {
            val repo = repository()
            repo.setScreenLimitWeekend(60)
            repo.setAppLimitWeekend("com.a.app", 30)
            repo.setBedtime(1, 2)
            repo.setRemindersEnabled(false)
            repo.recordShownReminders(setOf("x"), bedtimeMs = 1L)
            repo.clear()
            assertEquals(Settings(), repo.current())
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
