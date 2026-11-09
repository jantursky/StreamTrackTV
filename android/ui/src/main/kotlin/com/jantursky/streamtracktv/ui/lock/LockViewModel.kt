package com.jantursky.streamtracktv.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jantursky.streamtracktv.core.model.SecurityEventType
import com.jantursky.streamtracktv.core.pin.PIN_MAX_LENGTH
import com.jantursky.streamtracktv.core.security.SecurityRecorder
import com.jantursky.streamtracktv.data.pin.PinCheck
import com.jantursky.streamtracktv.data.pin.PinStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LockUiState(val entered: String = "", val wrong: Boolean = false, val waitMs: Long = 0)

/** Typing, checking and waiting on the lock screen. A correct PIN opens the [LockSession]. */
class LockViewModel(
    private val pin: PinStore,
    private val lock: LockSession,
    private val recorder: SecurityRecorder = SecurityRecorder.None,
) : ViewModel() {
    private val _state = MutableStateFlow(LockUiState())
    val state: StateFlow<LockUiState> = _state
    private var ticker: Job? = null

    init {
        // A wait that began before the app was closed is still running.
        viewModelScope.launch { pin.remainingWaitMs().takeIf { it > 0 }?.let(::startWait) }
    }

    fun onDigit(digit: Char) =
        _state.update {
            if (it.waitMs > 0 || it.entered.length >= PIN_MAX_LENGTH) it else it.copy(entered = it.entered + digit, wrong = false)
        }

    fun onDelete() = _state.update { if (it.waitMs > 0) it else it.copy(entered = it.entered.dropLast(1)) }

    fun onSubmit() {
        viewModelScope.launch { submit() }
    }

    /** Checks the entered PIN; returns when the answer has been applied. */
    suspend fun submit() {
        val entered = _state.value.entered
        if (entered.isEmpty() || _state.value.waitMs > 0) return
        when (val check = pin.verify(entered)) {
            PinCheck.Correct, PinCheck.NotSet -> {
                _state.value = LockUiState()
                recorder.record(SecurityEventType.UNLOCKED)
                lock.unlock()
            }

            is PinCheck.Wrong -> {
                _state.value = LockUiState(wrong = true)
                if (check.waitMs > 0) startWait(check.waitMs)
            }

            is PinCheck.Locked -> {
                _state.value = LockUiState()
                startWait(check.remainingMs)
            }
        }
    }

    private fun startWait(ms: Long) {
        ticker?.cancel()
        _state.update { it.copy(waitMs = ms, entered = "") }
        ticker =
            viewModelScope.launch {
                var left = ms
                while (left > 0) {
                    delay(TICK_MS)
                    left = pin.remainingWaitMs()
                    _state.update { it.copy(waitMs = left) }
                }
            }
    }

    private companion object {
        const val TICK_MS = 1_000L
    }
}
