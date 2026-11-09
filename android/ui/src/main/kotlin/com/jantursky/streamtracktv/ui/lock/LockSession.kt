package com.jantursky.streamtracktv.ui.lock

import com.jantursky.streamtracktv.core.model.SecurityEventType
import com.jantursky.streamtracktv.core.security.SecurityRecorder
import com.jantursky.streamtracktv.data.pin.PinStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Whether the app is behind the PIN right now. It lives as long as the process, so a recreated activity
 * does not skip the lock; it locks again whenever the app leaves the screen.
 */
class LockSession(
    pin: PinStore,
    scope: CoroutineScope,
    private val recorder: SecurityRecorder = SecurityRecorder.None,
) {
    private val unlocked = MutableStateFlow(false)
    private var openedRecorded = false

    /** `true` while the lock screen must show, `false` when the app may show, null until the setting is read. */
    val locked: StateFlow<Boolean?> =
        combine(pin.isEnabled, unlocked) { enabled, open -> enabled && !open }
            .stateIn(scope, SharingStarted.Eagerly, null)

    /** Records that the app was opened, once per process: coming back from the background is not opening it. */
    fun appOpened() {
        if (openedRecorded) return
        openedRecorded = true
        recorder.record(SecurityEventType.APP_OPENED)
    }

    fun unlock() {
        unlocked.value = true
    }

    /** Called when the app goes to the background; a no-op while no PIN is set. */
    fun lock() {
        unlocked.value = false
    }
}
