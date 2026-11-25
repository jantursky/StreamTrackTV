package com.jantursky.streamtracktv.collector

import android.accessibilityservice.AccessibilityService
import android.os.PowerManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.jantursky.streamtracktv.core.aggregation.KeyCounter
import com.jantursky.streamtracktv.core.insights.BedtimeWindow
import com.jantursky.streamtracktv.core.insights.planReminders
import com.jantursky.streamtracktv.core.insights.summarizeDay
import com.jantursky.streamtracktv.data.settings.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * Counts remote key presses by class and, once a minute while the screen is on and a limit or bedtime is set,
 * shows a short reminder. It never consumes a key, receives no accessibility events and cannot read window
 * content (see `res/xml/key_press_service.xml`).
 */
class KeyPressService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val counter = KeyCounter { ZoneId.systemDefault() }
    private val repository by lazy { (application as CollectorHost).usageRepository }
    private val banner by lazy { ReminderBanner(this) }

    override fun onServiceConnected() {
        scope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flush()
                remindSafely()
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            counter.record(System.currentTimeMillis(), classifyKey(event.keyCode))
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.launch { withContext(NonCancellable) { flush() } }.invokeOnCompletion { scope.cancel() }
        super.onDestroy()
    }

    private suspend fun flush() {
        val increments = counter.drain()
        if (increments.isNotEmpty()) repository.addKeyPresses(increments)
    }

    /** A failed check is retried a minute later; it must not stop the key counting loop. */
    private suspend fun remindSafely() {
        try {
            remind()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            // Next minute tries again.
        }
    }

    /** Screen on and something set: collect usage, plan the reminders, store what was shown, then show them. */
    private suspend fun remind() {
        if (!getSystemService(PowerManager::class.java).isInteractive) return
        val host = application as CollectorHost
        val settings = host.settingsRepository.current()
        val today = LocalDate.now(zone())
        val goals = settings.goalsFor(today)
        val bedtime = settings.bedtimeWindow()
        if (!settings.remindersEnabled || (goals.isEmpty() && bedtime == null)) return

        host.usageCollector.collect()
        val day = summarizeDay(today, repository.observeDays(today, today).first())
        val plan =
            planReminders(
                goals = goals,
                bedtime = bedtime,
                enabled = settings.remindersEnabled,
                day = day,
                nowMs = System.currentTimeMillis(),
                zone = zone(),
                interactive = true,
                shown = settings.shownReminders,
                lastBedtimeMs = settings.lastBedtimeReminderMs,
            )
        if (plan.shown != settings.shownReminders || plan.lastBedtimeMs != settings.lastBedtimeReminderMs) {
            host.settingsRepository.recordShownReminders(plan.shown, plan.lastBedtimeMs)
        }
        if (plan.reminders.isNotEmpty()) {
            banner.show(
                plan.reminders.joinToString("\n") { reminder ->
                    reminderText(this, reminder) { pkg -> host.appCatalog.label(pkg) ?: pkg }
                },
            )
        }
    }

    private fun zone(): ZoneId = ZoneId.systemDefault()

    private fun Settings.bedtimeWindow(): BedtimeWindow? {
        val start = bedtimeStartMinute ?: return null
        val end = bedtimeEndMinute ?: return null
        val minutesPerDay = 24 * 60
        return if (start in 0 until minutesPerDay && end in 0 until minutesPerDay) BedtimeWindow(start, end) else null
    }

    private companion object {
        const val FLUSH_INTERVAL_MS = 60_000L
    }
}
