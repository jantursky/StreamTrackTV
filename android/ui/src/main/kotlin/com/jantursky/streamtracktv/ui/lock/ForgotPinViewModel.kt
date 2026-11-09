package com.jantursky.streamtracktv.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jantursky.streamtracktv.sync.PinResetState
import com.jantursky.streamtracktv.ui.UiGraph
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Runs the PIN reset and opens the app once the owner has confirmed it on the web. */
class ForgotPinViewModel(private val graph: UiGraph) : ViewModel() {
    private val _state = MutableStateFlow<PinResetState>(PinResetState.Requesting)
    val state: StateFlow<PinResetState> = _state
    private var job: Job? = null

    init {
        retry()
    }

    fun retry() {
        job?.cancel()
        _state.value = PinResetState.Requesting
        job =
            viewModelScope.launch {
                graph.pinLock.reset.reset().collect { next ->
                    _state.value = next
                    if (next == PinResetState.Cleared) graph.pinLock.session.unlock()
                }
            }
    }
}
