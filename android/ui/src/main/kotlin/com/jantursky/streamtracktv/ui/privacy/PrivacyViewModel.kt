package com.jantursky.streamtracktv.ui.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jantursky.streamtracktv.core.wire.EventsRequestDto
import com.jantursky.streamtracktv.core.wire.PrettyWireJson
import com.jantursky.streamtracktv.core.wire.SyncPayloadDto
import com.jantursky.streamtracktv.ui.UiGraph
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/** Rebuilds the preview whenever pending rows or settings change. */
class PrivacyViewModel(graph: UiGraph) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val preview: StateFlow<PayloadPreview?> =
        combine(
            graph.usage.observePendingCount(),
            graph.settings.settings,
            graph.securityLog.observePending(),
        ) { _, settings, _ -> settings.rejectedRows }
            .mapLatest { rejectedRows ->
                val payload = graph.syncEngine.nextBatch().payload
                val events = graph.syncEngine.nextEvents()
                PayloadPreview(
                    PrettyWireJson.encodeToString(SyncPayloadDto.serializer(), payload),
                    payload.usage.size + payload.keys.size + payload.screen.size,
                    rejectedRows,
                    events.takeIf { it.events.isNotEmpty() }?.let {
                        PrettyWireJson.encodeToString(EventsRequestDto.serializer(), it)
                    },
                    events.events.size,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
