package com.jantursky.streamtracktv.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import com.jantursky.streamtracktv.ui.components.Caption

private const val HOURS_PER_DAY = 24
private val AXIS_HOURS = listOf(0, 6, 12, 18, 24)

fun hourAxisTag(hour: Int) = "hour-axis-$hour"

/** Left edge of a label centered on [hour] of 24 across [width], kept inside `0..width - labelWidth`. */
fun axisLabelX(hour: Int, width: Int, labelWidth: Int): Int =
    (width * hour / HOURS_PER_DAY - labelWidth / 2).coerceIn(0, (width - labelWidth).coerceAtLeast(0))

/**
 * Hour labels under the 24 bars, each centered on its hour edge. They are decoration: the chart's
 * own description already carries the values, so TalkBack skips them.
 */
@Composable
fun HourAxis(modifier: Modifier = Modifier) {
    Layout(
        content = {
            AXIS_HOURS.forEach { hour ->
                Box(Modifier.layoutId(hour).testTag(hourAxisTag(hour))) { Caption(hour.toString().padStart(2, '0')) }
            }
        },
        modifier = modifier.clearAndSetSemantics { },
    ) { measurables, constraints ->
        val loose = Constraints(maxWidth = constraints.maxWidth)
        val placeables = measurables.map { it.layoutId as Int to it.measure(loose) }
        val width = constraints.maxWidth
        val height = placeables.maxOfOrNull { it.second.height } ?: 0
        layout(width, height) {
            placeables.forEach { (hour, placeable) ->
                placeable.placeRelative(axisLabelX(hour, width, placeable.width), 0)
            }
        }
    }
}
