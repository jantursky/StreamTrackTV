package com.jantursky.streamtracktv.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.tv.material3.Text
import com.jantursky.streamtracktv.ui.components.FocusSurface
import com.jantursky.streamtracktv.ui.components.ShareBar
import com.jantursky.streamtracktv.ui.dashboard.DashboardScreen
import com.jantursky.streamtracktv.ui.theme.StreamTrackTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h3000dp-xhdpi")
class CardSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun showToday() {
        val model = DashboardFixture.model()
        val catalog = DashboardFixture.catalog()
        compose.setContent {
            StreamTrackTheme { DashboardScreen(model, catalog, onOpenSetup = {}, onSyncNow = {}) }
        }
    }

    @Test
    fun `a surface with a description is one node that carries it`() {
        compose.setContent {
            StreamTrackTheme {
                FocusSurface(onClick = null, modifier = Modifier.testTag("card"), description = "Screen time, 3 h") {
                    Text("Screen time")
                    Text("3 h")
                }
            }
        }
        compose.onNodeWithTag("card").assertContentDescriptionEquals("Screen time, 3 h")
    }

    @Test
    fun `a surface without a description keeps its children's own semantics`() {
        compose.setContent {
            StreamTrackTheme {
                FocusSurface(onClick = null) { Text("Own text") }
            }
        }
        compose.onNodeWithText("Own text").assertExists()
    }

    @Test
    fun `the screen time stat card reads title and value together`() {
        showToday()
        val description = describedNodes().map { it.second }.single { Regex("Screen time, \\d+ (h|min)( \\d+ min)?").matches(it) }
        assertNotNull(description)
    }

    @Test
    fun `a top app row reads the name and time and reports its share as progress`() {
        showToday()
        val row = compose.onNode(hasContentDescription("Netflix, ", substring = true)).fetchSemanticsNode()
        val state = row.config.getOrNull(SemanticsProperties.StateDescription)
        assertNotNull(state)
        assertEquals(true, state.endsWith(" percent"))
        assertNotNull(row.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo))
    }

    @Test
    fun `a goal row reads the name state and used time and reports percent`() {
        showToday()
        val states = listOf("On track", "Near limit", "Over limit")
        val row = describedNodes().single { (_, text) -> states.any { text.startsWith("Screen time, $it") } }
        assertNotNull(row.first.config.getOrNull(SemanticsProperties.StateDescription))
        assertEquals(true, row.second.contains(" of "))
    }

    private fun describedNodes() =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)).fetchSemanticsNodes().map {
            it to it.config[SemanticsProperties.ContentDescription].joinToString()
        }

    @Test
    fun `the Sync card is one node with its status lines`() {
        showToday()
        compose
            .onNode(hasContentDescription("Not synced yet", substring = true) or hasContentDescription("Last sync", substring = true))
            .assertExists()
    }

    @Test
    fun `a bar at 0_8 reports 80 percent as progress`() {
        compose.setContent {
            StreamTheme { ShareBar(0.8f, label = "Used", modifier = Modifier.fillMaxWidth().testTag("bar")) }
        }
        val config = compose.onNodeWithTag("bar").fetchSemanticsNode().config
        assertEquals("80 percent", config.getOrNull(SemanticsProperties.StateDescription))
        assertEquals(0.8f, config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.current)
        assertEquals(listOf("Used"), config.getOrNull(SemanticsProperties.ContentDescription))
    }

    @Test
    fun `the hourly chart has one summary and its axis labels are hidden`() {
        showToday()
        describedNodes().single { it.second.startsWith("Screen on ") }
        listOf("00", "06", "12", "18", "24").forEach {
            assertEquals(0, compose.onAllNodesWithText(it).fetchSemanticsNodes().size, "label $it")
        }
    }

    @Test
    fun `app icons are decorative inside their row`() {
        showToday()
        // The monogram letter of an uninstalled app is not a node of its own.
        assertEquals(0, compose.onAllNodesWithText("N").fetchSemanticsNodes().size)
    }
}

@androidx.compose.runtime.Composable
private fun StreamTheme(content: @androidx.compose.runtime.Composable () -> Unit) = StreamTrackTheme { Column { content() } }
