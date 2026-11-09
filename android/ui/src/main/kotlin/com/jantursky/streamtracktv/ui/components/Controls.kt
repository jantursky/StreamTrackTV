package com.jantursky.streamtracktv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jantursky.streamtracktv.ui.theme.LocalPalette
import com.jantursky.streamtracktv.ui.theme.PillShape

/** Pill button. [primary] is the cyan filled action; there should be at most one per screen area. */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    val palette = LocalPalette.current
    FocusSurface(
        onClick = onClick,
        modifier = modifier,
        shape = PillShape,
        color = if (primary) palette.accent else Color.Transparent,
        borderColor = if (primary) palette.accentEdge else palette.borderStrong,
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (primary) palette.onAccent else palette.ink)
    }
}

/** A focusable settings row: label and helper on the left, current value on the right. */
@Composable
fun OptionRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    helper: String? = null,
) {
    val palette = LocalPalette.current
    FocusSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, color = palette.ink)
                helper?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.text) }
            }
            Spacer(Modifier.width(24.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = palette.accentEdge,
                textAlign = TextAlign.End,
            )
        }
    }
}

/** Done / needed mark that uses a glyph and a word, not only color. */
@Composable
fun StatusMark(done: Boolean, doneLabel: String, pendingLabel: String) {
    val palette = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (done) palette.accent else palette.border),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (done) "✓" else "!",
                style = MaterialTheme.typography.labelMedium,
                color = if (done) palette.onAccent else palette.ink,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(if (done) doneLabel else pendingLabel, style = MaterialTheme.typography.labelLarge, color = palette.ink)
    }
}

/** Horizontal bar for shares and goal progress; values above 1 fill the bar. */
@Composable
fun ShareBar(fraction: Float, modifier: Modifier = Modifier, description: String? = null) {
    val palette = LocalPalette.current
    Box(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(PillShape)
            .background(palette.border)
            .semantics { description?.let { contentDescription = it } },
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(10.dp)
                .clip(PillShape)
                .background(palette.accent),
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = LocalPalette.current.ink,
        modifier = modifier.padding(bottom = 12.dp),
    )
}

/** Small caption under a value. */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = LocalPalette.current.text, modifier = modifier)
}

/** Highlight span from the design system: accent-edge text on an accent-wash pill. */
@Composable
fun Highlight(text: String, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = palette.accentEdge,
        modifier =
            modifier
                .clip(PillShape)
                .background(palette.accentWash)
                .padding(PaddingValues(horizontal = 16.dp, vertical = 6.dp)),
    )
}
