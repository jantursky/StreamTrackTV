package com.apptiary.streamtracktv.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apptiary.streamtracktv.core.pairing.ServerUrl
import com.apptiary.streamtracktv.sync.PairingState
import com.apptiary.streamtracktv.ui.UiGraph
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ServerInputError { INVALID, HTTPS_REQUIRED }

sealed interface PairingUiState {
    data class EnterServer(val input: String, val error: ServerInputError? = null) : PairingUiState

    data class Connecting(val server: ServerUrl, val pairing: PairingState) : PairingUiState
}

/** Server entry, then the code screen. Pairing success flips the stored token and the root leaves this flow. */
class PairingViewModel(private val graph: UiGraph) : ViewModel() {
    private val mutableState =
        MutableStateFlow<PairingUiState>(PairingUiState.EnterServer(graph.appInfo.defaultServerUrl))
    val state: StateFlow<PairingUiState> = mutableState.asStateFlow()
    private var pairingJob: Job? = null

    init {
        viewModelScope.launch {
            val saved = graph.settings.current().serverUrl ?: return@launch
            val current = mutableState.value
            if (current is PairingUiState.EnterServer && current.input == graph.appInfo.defaultServerUrl) {
                mutableState.value = current.copy(input = saved)
            }
        }
    }

    fun onInputChange(input: String) {
        mutableState.value = PairingUiState.EnterServer(input)
    }

    fun submit() {
        val input = (mutableState.value as? PairingUiState.EnterServer)?.input ?: return
        val server = ServerUrl.parse(input, graph.appInfo.allowHttp)
        if (server == null) {
            val httpOnly = input.trim().startsWith("http://") && ServerUrl.parse(input, allowHttp = true) != null
            mutableState.value =
                PairingUiState.EnterServer(
                    input,
                    if (httpOnly) ServerInputError.HTTPS_REQUIRED else ServerInputError.INVALID,
                )
        } else {
            start(server)
        }
    }

    fun retry() {
        (mutableState.value as? PairingUiState.Connecting)?.let { start(it.server) }
    }

    fun changeServer() {
        val server = (mutableState.value as? PairingUiState.Connecting)?.server ?: return
        pairingJob?.cancel()
        mutableState.value = PairingUiState.EnterServer(server.value)
    }

    private fun start(server: ServerUrl) {
        pairingJob?.cancel()
        mutableState.value = PairingUiState.Connecting(server, PairingState.Requesting)
        pairingJob =
            viewModelScope.launch {
                graph.pairing.pair(server, graph.appInfo.deviceName, graph.appInfo.model).collect { pairing ->
                    mutableState.value = PairingUiState.Connecting(server, pairing)
                }
            }
    }
}
