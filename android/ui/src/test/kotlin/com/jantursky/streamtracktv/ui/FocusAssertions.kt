package com.jantursky.streamtracktv.ui

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import kotlin.test.assertTrue

/** The node or one of its ancestors holds focus: rows put their text inside the focusable surface. */
fun SemanticsNodeInteraction.assertIsFocusedAncestor(): SemanticsNodeInteraction {
    var node: SemanticsNode? = fetchSemanticsNode()
    while (node != null) {
        if (node.config.getOrNull(SemanticsProperties.Focused) == true) return this
        node = node.parent
    }
    assertTrue(false, "Neither the node nor an ancestor is focused")
    return this
}
