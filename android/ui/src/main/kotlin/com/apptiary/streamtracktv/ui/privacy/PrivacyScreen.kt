package com.apptiary.streamtracktv.ui.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.apptiary.streamtracktv.ui.R
import com.apptiary.streamtracktv.ui.components.Caption
import com.apptiary.streamtracktv.ui.components.FocusSurface
import com.apptiary.streamtracktv.ui.components.SectionTitle
import com.apptiary.streamtracktv.ui.theme.CardShape
import com.apptiary.streamtracktv.ui.theme.LocalPalette
import kotlinx.coroutines.launch

const val PAYLOAD_TAG = "privacy-payload"
private val PAYLOAD_HEIGHT = 420.dp

/** The next upload as JSON with its row count, and how many rows the server refused so far. */
data class PayloadPreview(val json: String, val rows: Int, val rejectedRows: Int)

private val collected =
    listOf(
        R.string.privacy_collected_1,
        R.string.privacy_collected_2,
        R.string.privacy_collected_3,
        R.string.privacy_collected_4,
    )
private val neverCollected =
    listOf(
        R.string.privacy_never_1,
        R.string.privacy_never_2,
        R.string.privacy_never_3,
        R.string.privacy_never_4,
        R.string.privacy_never_5,
        R.string.privacy_never_6,
    )

@Composable
fun PrivacyScreen(preview: PayloadPreview?) {
    val palette = LocalPalette.current
    LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp), contentPadding = PaddingValues(bottom = 48.dp)) {
        item {
            Text(
                stringResource(R.string.privacy_title),
                style = MaterialTheme.typography.displayMedium,
                color = palette.ink,
            )
        }
        item {
            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                FactList(R.string.privacy_collected_title, collected, "+", Modifier.weight(1f).fillMaxHeight())
                FactList(R.string.privacy_never_title, neverCollected, "×", Modifier.weight(1f).fillMaxHeight())
            }
        }
        item {
            SectionTitle(stringResource(R.string.privacy_payload_title))
            if (preview != null && preview.rows == 0) Caption(stringResource(R.string.privacy_payload_empty))
            if (preview != null && preview.rows > 0) {
                Caption(pluralStringResource(R.plurals.privacy_payload_body, preview.rows, preview.rows))
                Spacer(Modifier.height(12.dp))
                PayloadBox(preview.json)
            }
            if (preview != null && preview.rejectedRows > 0) {
                Spacer(Modifier.height(12.dp))
                Caption(pluralStringResource(R.plurals.privacy_rejected_rows, preview.rejectedRows, preview.rejectedRows))
            }
        }
    }
}

@Composable
private fun FactList(title: Int, facts: List<Int>, mark: String, modifier: Modifier) {
    val palette = LocalPalette.current
    FocusSurface(onClick = null, modifier = modifier) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = palette.ink)
        Spacer(Modifier.height(8.dp))
        facts.forEach { fact ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Text(
                    mark,
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.accentEdge,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Text(stringResource(fact), style = MaterialTheme.typography.bodyMedium, color = palette.ink)
            }
        }
    }
}

/** Focusable JSON view; D-Pad up and down scroll it while it has focus. */
@Composable
private fun PayloadBox(json: String) {
    val palette = LocalPalette.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val step = with(LocalDensity.current) { 160.dp.toPx() }
    Text(
        json,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 18.sp),
        color = palette.ink,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(PAYLOAD_HEIGHT)
                .border(if (focused) 3.dp else 1.dp, if (focused) palette.accent else palette.border, CardShape)
                .background(palette.surface, CardShape)
                .onPreviewKeyEvent { event ->
                    val delta =
                        when (event.key) {
                            Key.DirectionDown -> step
                            Key.DirectionUp -> -step
                            else -> return@onPreviewKeyEvent false
                        }
                    val canScroll = if (delta > 0) scroll.value < scroll.maxValue else scroll.value > 0
                    if (canScroll && event.type == KeyEventType.KeyDown) scope.launch { scroll.animateScrollBy(delta) }
                    canScroll
                }.focusable(interactionSource = interaction)
                .verticalScroll(scroll)
                .padding(24.dp)
                .testTag(PAYLOAD_TAG),
    )
}
