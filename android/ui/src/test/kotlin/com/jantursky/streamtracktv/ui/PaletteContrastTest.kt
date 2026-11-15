package com.jantursky.streamtracktv.ui

import androidx.compose.ui.graphics.Color
import com.jantursky.streamtracktv.ui.theme.DarkPalette
import com.jantursky.streamtracktv.ui.theme.LightPalette
import com.jantursky.streamtracktv.ui.theme.Palette
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

private const val TEXT_RATIO = 4.5
private const val GRAPHIC_RATIO = 3.0
private const val LINEAR_CUTOFF = 0.03928
private const val LINEAR_DIVISOR = 12.92
private const val GAMMA_OFFSET = 0.055
private const val GAMMA_DIVISOR = 1.055
private const val GAMMA = 2.4
private const val RED_WEIGHT = 0.2126
private const val GREEN_WEIGHT = 0.7152
private const val BLUE_WEIGHT = 0.0722
private const val FLARE = 0.05

private fun channel(value: Float): Double {
    val v = value.toDouble()
    return if (v <= LINEAR_CUTOFF) v / LINEAR_DIVISOR else ((v + GAMMA_OFFSET) / GAMMA_DIVISOR).pow(GAMMA)
}

private fun luminance(color: Color): Double =
    RED_WEIGHT * channel(color.red) + GREEN_WEIGHT * channel(color.green) + BLUE_WEIGHT * channel(color.blue)

/** WCAG 2 contrast ratio between two opaque colors. */
fun contrastRatio(a: Color, b: Color): Double {
    val la = luminance(a)
    val lb = luminance(b)
    return (maxOf(la, lb) + FLARE) / (minOf(la, lb) + FLARE)
}

class PaletteContrastTest {
    private val themes = mapOf("light" to LightPalette, "dark" to DarkPalette)

    private fun check(pairs: (Palette) -> List<Triple<String, Pair<Color, Color>, Double>>) {
        val failures =
            themes.flatMap { (theme, palette) ->
                pairs(palette).mapNotNull { (name, colors, minimum) ->
                    val ratio = contrastRatio(colors.first, colors.second)
                    if (ratio < minimum) "$theme $name %.2f < $minimum".format(ratio) else null
                }
            }
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }

    @Test
    fun `text colors reach 4_5 to 1 on the canvas and the surface`() =
        check { p ->
            listOf(p.ink, p.text, p.soot, p.accentEdge).let { texts ->
                val names = listOf("ink", "text", "soot", "accentEdge")
                texts.flatMapIndexed { i, color ->
                    listOf(
                        Triple("${names[i]} on canvas", color to p.canvas, TEXT_RATIO),
                        Triple("${names[i]} on surface", color to p.surface, TEXT_RATIO),
                    )
                }
            } + Triple("onAccent on accent", p.onAccent to p.accent, TEXT_RATIO) +
                Triple("ink on accentWash", p.ink to p.accentWash, TEXT_RATIO) +
                Triple("accentEdge on accentWash", p.accentEdge to p.accentWash, TEXT_RATIO) +
                Triple("canvas on soot", p.canvas to p.soot, TEXT_RATIO)
        }

    @Test
    fun `focus ring and bar fill reach 3 to 1`() =
        check { p ->
            listOf(
                Triple("focusRing on surface", p.focusRing to p.surface, GRAPHIC_RATIO),
                Triple("focusRing on canvas", p.focusRing to p.canvas, GRAPHIC_RATIO),
            )
        }
}
