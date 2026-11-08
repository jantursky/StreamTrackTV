package com.apptiary.streamtracktv.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Design tokens from `design/DESIGN.md`. Cyan accent is the only chromatic fill. */
@Immutable
data class Palette(
    val accent: Color,
    val accentEdge: Color,
    val accentWash: Color,
    val ink: Color,
    val soot: Color,
    val text: Color,
    val textFaint: Color,
    val borderStrong: Color,
    val border: Color,
    val canvas: Color,
    val surface: Color,
    /** Text on the accent fill; ink reads better than white on both accent shades. */
    val onAccent: Color,
    val isDark: Boolean,
)

val DarkPalette =
    Palette(
        accent = Color(0xFF5BB6F5),
        accentEdge = Color(0xFF7CC4F7),
        accentWash = Color(0xFF12324A),
        ink = Color(0xFFFAFAF9),
        soot = Color(0xFFE7E5E4),
        text = Color(0xFFA8A29E),
        textFaint = Color(0xFF78716C),
        borderStrong = Color(0xFF44403C),
        border = Color(0xFF292524),
        canvas = Color(0xFF0C0A09),
        surface = Color(0xFF1C1917),
        onAccent = Color(0xFF0C0A09),
        isDark = true,
    )

val LightPalette =
    Palette(
        accent = Color(0xFF3BA6F1),
        accentEdge = Color(0xFF3398E1),
        accentWash = Color(0xFFC1E1F7),
        ink = Color(0xFF0C0A09),
        soot = Color(0xFF1C1917),
        text = Color(0xFF78716C),
        textFaint = Color(0xFFA8A29E),
        borderStrong = Color(0xFFD6D3D1),
        border = Color(0xFFE8E6E5),
        canvas = Color(0xFFFAFAF9),
        surface = Color(0xFFFFFFFF),
        onAccent = Color(0xFF0C0A09),
        isDark = false,
    )

val LocalPalette = staticCompositionLocalOf { DarkPalette }
