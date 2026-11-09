package com.jantursky.streamtracktv.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jantursky.streamtracktv.core.model.SecurityEventType
import com.jantursky.streamtracktv.core.pin.PIN_MAX_LENGTH
import com.jantursky.streamtracktv.core.pin.isValidPin
import com.jantursky.streamtracktv.core.security.SecurityRecorder
import com.jantursky.streamtracktv.data.pin.PinCheck
import com.jantursky.streamtracktv.data.pin.PinStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the owner is doing with the PIN from Settings. */
enum class PinFlowMode { SET, CHANGE, DISABLE }

/** Which PIN is being asked for. */
enum class PinStep { CURRENT, NEW, REPEAT }

enum class PinFlowError { WRONG, TOO_SHORT, MISMATCH, WAIT }

data class PinFlowState(
    val mode: PinFlowMode,
    val step: PinStep,
    val entered: String = "",
    val error: PinFlowError? = null,
    val waitMs: Long = 0,
    val done: Boolean = false,
)

private fun firstStep(mode: PinFlowMode) = if (mode == PinFlowMode.SET) PinStep.NEW else PinStep.CURRENT

/**
 * Turn the PIN on, change it, or turn it off: current PIN first where one exists, then the new PIN twice.
 * Changing or turning off checks the current PIN before it does anything.
 */
class PinFlowViewModel(
    private val mode: PinFlowMode,
    private val pin: PinStore,
    private val lock: LockSession,
    private val recorder: SecurityRecorder = SecurityRecorder.None,
) : ViewModel() {
    private val _state = MutableStateFlow(PinFlowState(mode, firstStep(mode)))
    val state: StateFlow<PinFlowState> = _state
    private var first = ""

    fun onDigit(digit: Char) =
        _state.update {
            if (it.entered.length >= PIN_MAX_LENGTH) it else it.copy(entered = it.entered + digit, error = null)
        }

    fun onDelete() = _state.update { it.copy(entered = it.entered.dropLast(1)) }

    fun onSubmit() {
        viewModelScope.launch { submit() }
    }

    /** Applies the entered PIN to the current step; returns when that is done. */
    suspend fun submit() {
        val entered = _state.value.entered
        if (entered.isEmpty()) return
        when (_state.value.step) {
            PinStep.CURRENT -> submitCurrent(entered)
            PinStep.NEW -> submitNew(entered)
            PinStep.REPEAT -> submitRepeat(entered)
        }
    }

    private suspend fun submitCurrent(entered: String) {
        val check = if (mode == PinFlowMode.DISABLE) pin.disable(entered) else pin.verify(entered)
        when (check) {
            PinCheck.Correct, PinCheck.NotSet -> {
                if (mode == PinFlowMode.DISABLE) {
                    recorder.record(SecurityEventType.PIN_DISABLED)
                    _state.value = _state.value.copy(entered = "", done = true)
                } else {
                    _state.value = PinFlowState(mode, PinStep.NEW)
                }
            }

            is PinCheck.Wrong -> {
                _state.value = _state.value.copy(entered = "", error = PinFlowError.WRONG)
            }

            is PinCheck.Locked -> {
                _state.value = _state.value.copy(entered = "", error = PinFlowError.WAIT, waitMs = check.remainingMs)
            }
        }
    }

    private fun submitNew(entered: String) {
        if (!isValidPin(entered)) {
            _state.update { it.copy(entered = "", error = PinFlowError.TOO_SHORT) }
            return
        }
        first = entered
        _state.value = PinFlowState(mode, PinStep.REPEAT)
    }

    private suspend fun submitRepeat(entered: String) {
        if (entered != first) {
            first = ""
            _state.value = PinFlowState(mode, PinStep.NEW, error = PinFlowError.MISMATCH)
            return
        }
        pin.setPin(entered)
        recorder.record(if (mode == PinFlowMode.SET) SecurityEventType.PIN_ENABLED else SecurityEventType.PIN_CHANGED)
        // The owner just proved who they are; do not lock them out of the screen they are using.
        lock.unlock()
        _state.value = _state.value.copy(entered = "", done = true)
    }
}
