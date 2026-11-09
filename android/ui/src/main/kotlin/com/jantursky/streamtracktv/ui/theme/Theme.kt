package com.jantursky.streamtracktv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme

val CardShape = RoundedCornerShape(16.dp)
val PillShape = RoundedCornerShape(percent = 50)

/** 48 dp safe area on every edge, as the TV adaptation in DESIGN.md asks. */
val SafeArea = 48.dp

/**
 * Dark by default for living-room viewing; light is a user setting. TVs do not report a reliable
 * system theme, so it is not followed.
 */
@Composable
fun StreamTrackTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    val scheme =
        if (dark) {
            darkColorScheme(
                primary = palette.accent,
                onPrimary = palette.onAccent,
                background = palette.canvas,
                onBackground = palette.ink,
                surface = palette.surface,
                onSurface = palette.ink,
                onSurfaceVariant = palette.text,
                border = palette.borderStrong,
            )
        } else {
            lightColorScheme(
                primary = palette.accent,
                onPrimary = palette.onAccent,
                background = palette.canvas,
                onBackground = palette.ink,
                surface = palette.surface,
                onSurface = palette.ink,
                onSurfaceVariant = palette.text,
                border = palette.borderStrong,
            )
        }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = StreamTrackTypography, content = content)
    }
}

val RoundedIcon = RoundedCornerShape(8.dp)
