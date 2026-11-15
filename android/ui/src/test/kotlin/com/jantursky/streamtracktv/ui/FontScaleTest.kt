package com.jantursky.streamtracktv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jantursky.streamtracktv.ui.dashboard.DashboardScreen
import com.jantursky.streamtracktv.ui.lock.PinPad
import com.jantursky.streamtracktv.ui.theme.StreamTrackTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Labels must stay whole at the system font scales a TV user can pick. The scale is applied through
 * the density, which is what the system setting does to every `sp` value.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h3000dp-xhdpi")
class FontScaleTest {
    @get:Rule
    val compose = createComposeRule()

    private fun scaled(fontScale: Float, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                StreamTrackTheme { content() }
            }
        }
    }

    /** Every text in the tree is fully laid out: no overflow in either direction. */
    private fun assertNoTextOverflow(minimumTexts: Int) {
        val nodes =
            compose
                .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
                .fetchSemanticsNodes()
        assertTrue(nodes.size >= minimumTexts, "expected at least $minimumTexts texts, found ${nodes.size}")
        nodes.forEach { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config
                .getOrNull(SemanticsActions.GetTextLayoutResult)
                ?.action
                ?.invoke(results)
            val result = results.single()
            val text = result.layoutInput.text.text
            assertEquals(false, result.didOverflowWidth, "width overflow: $text")
            assertEquals(false, result.didOverflowHeight, "height overflow: $text")
        }
    }

    private fun showRail(scale: Float) = scaled(scale) { NavigationLayout(Destination.TODAY, onNavigate = {}) {} }

    private fun showPad(scale: Float) = scaled(scale) { PinPad(enabled = true, onDigit = {}, onDelete = {}, onSubmit = {}) }

    private fun showToday(scale: Float) {
        val model = DashboardFixture.model()
        val catalog = DashboardFixture.catalog()
        scaled(scale) { DashboardScreen(model, catalog, onOpenSetup = {}, onSyncNow = {}) }
    }

    @Test
    fun `the rail shows every label at 1_3`() {
        showRail(1.3f)
        assertNoTextOverflow(Destination.entries.size)
    }

    @Test
    fun `the rail shows every label at 2_0`() {
        showRail(2.0f)
        assertNoTextOverflow(Destination.entries.size)
    }

    @Test
    fun `the PIN pad shows every key at 1_3`() {
        showPad(1.3f)
        assertNoTextOverflow(12)
    }

    @Test
    fun `the PIN pad shows every key at 2_0`() {
        showPad(2.0f)
        assertNoTextOverflow(12)
    }

    @Test
    fun `Today shows its cards at 1_3`() {
        showToday(1.3f)
        assertNoTextOverflow(20)
    }

    @Test
    fun `Today shows its cards at 2_0`() {
        showToday(2.0f)
        assertNoTextOverflow(20)
    }
}
