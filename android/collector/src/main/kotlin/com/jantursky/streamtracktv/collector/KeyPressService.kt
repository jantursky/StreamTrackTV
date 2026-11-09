package com.jantursky.streamtracktv.collector

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.jantursky.streamtracktv.core.aggregation.KeyCounter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * Counts remote key presses by class. It never consumes a key, receives no accessibility events and
 * cannot read window content (see `res/xml/key_press_service.xml`).
 */
class KeyPressService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val counter = KeyCounter { ZoneId.systemDefault() }
    private val repository by lazy { (application as CollectorHost).usageRepository }

    override fun onServiceConnected() {
        scope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flush()
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

    private companion object {
        const val FLUSH_INTERVAL_MS = 60_000L
    }
}
