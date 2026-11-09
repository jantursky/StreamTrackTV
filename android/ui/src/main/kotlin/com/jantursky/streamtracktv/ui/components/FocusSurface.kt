package com.jantursky.streamtracktv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.jantursky.streamtracktv.ui.theme.CardShape
import com.jantursky.streamtracktv.ui.theme.LocalPalette

/** Focused elements grow by this factor; together with the ring, focus never relies on color alone. */
const val FOCUS_SCALE = 1.04f

/**
 * The one focus treatment of the app: a 3 dp accent ring plus a 1.04 scale. With [onClick] the
 * surface is clickable (D-Pad center or Enter); without it, it is still focusable so the D-Pad can
 * reach and scroll to read-only content.
 */
@Composable
fun FocusSurface(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    color: Color = LocalPalette.current.surface,
    borderColor: Color = LocalPalette.current.border,
    contentPadding: PaddingValues = PaddingValues(24.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) FOCUS_SCALE else 1f, label = "focusScale")
    val styled =
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }.border(if (focused) 3.dp else 1.dp, if (focused) palette.accent else borderColor, shape)
            .clip(shape)
            .background(color)
    val interactive =
        if (onClick != null) {
            styled.clickable(interactionSource = interaction, indication = null, onClick = onClick)
        } else {
            styled.focusable(interactionSource = interaction)
        }
    Column(interactive.padding(contentPadding), content = content)
}
