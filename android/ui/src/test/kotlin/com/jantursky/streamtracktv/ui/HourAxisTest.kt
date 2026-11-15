package com.jantursky.streamtracktv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.jantursky.streamtracktv.ui.dashboard.HourAxis
import com.jantursky.streamtracktv.ui.dashboard.axisLabelX
import com.jantursky.streamtracktv.ui.dashboard.hourAxisTag
import com.jantursky.streamtracktv.ui.theme.StreamTrackTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1000dp-h540dp-xhdpi")
class HourAxisTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                StreamTrackTheme { Box(Modifier.width(960.dp)) { HourAxis() } }
            }
        }
    }

    private fun assertLabelsAtTheirHour() {
        val axisWidth =
            compose
                .onNodeWithTag(
                    hourAxisTag(0),
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .layoutInfo.coordinates.parentCoordinates!!
                .size.width
        assertEquals((960 * 2), axisWidth)
        listOf(0, 6, 12, 18, 24).forEach { hour ->
            val bounds = compose.onNodeWithTag(hourAxisTag(hour), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val expectedLeft = axisLabelX(hour, axisWidth, bounds.width.toInt())
            assertEquals(expectedLeft.toFloat(), bounds.left, 1f, "label $hour")
            if (hour in 6..18) {
                // Interior labels are centered on their hour edge, to the pixel.
                assertTrue(abs(bounds.center.x - axisWidth * hour / 24f) <= 1f, "label $hour is centered on its hour")
            }
        }
        assertEquals(
            0f,
            compose
                .onNodeWithTag(hourAxisTag(0), useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot.left,
        )
        assertEquals(
            axisWidth.toFloat(),
            compose
                .onNodeWithTag(hourAxisTag(24), useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot.right,
            1f,
        )
    }

    @Test
    fun `labels sit at hour over 24 of the width at 960 dp`() {
        show(1f)
        assertLabelsAtTheirHour()
    }

    @Test
    fun `labels keep their place at font scale 2_0`() {
        show(2f)
        assertLabelsAtTheirHour()
    }
}
