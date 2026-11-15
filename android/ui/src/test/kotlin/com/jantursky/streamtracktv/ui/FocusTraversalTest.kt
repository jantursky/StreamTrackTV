package com.jantursky.streamtracktv.ui

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.jantursky.streamtracktv.data.settings.Settings
import com.jantursky.streamtracktv.ui.components.PillButton
import com.jantursky.streamtracktv.ui.dashboard.DashboardScreen
import com.jantursky.streamtracktv.ui.settings.SettingsActions
import com.jantursky.streamtracktv.ui.settings.SettingsModel
import com.jantursky.streamtracktv.ui.settings.SettingsScreen
import com.jantursky.streamtracktv.ui.theme.StreamTrackTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val MAX_STEPS = 20
private const val DOWN_STOPS = 6

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-xhdpi")
class FocusTraversalTest {
    @get:Rule
    val compose = createComposeRule()

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun `the rail starts on the current tab and the D-Pad walks it and into the content`() {
        var current by mutableStateOf(Destination.TODAY)
        compose.setContent {
            StreamTrackTheme {
                NavigationLayout(current, onNavigate = { current = it }) { PillButton("Content action", {}) }
            }
        }
        compose.onNodeWithTag(navTag(Destination.TODAY)).assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag(navTag(Destination.HISTORY)).assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag(navTag(Destination.SETUP)).assertIsFocused()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        assertEquals(Destination.PRIVACY, current)
        press(Key.DirectionRight)
        compose.onNodeWithText("Content action").assertIsFocused()
        press(Key.DirectionLeft)
        compose.onNodeWithTag(navTag(Destination.PRIVACY)).assertIsFocused()
    }

    @Test
    fun `settings rows are reachable in order and open a confirmation on the safe choice`() {
        var interval = 0
        val actions =
            SettingsActions(
                cycleInterval = { interval = it },
                cycleScreenLimit = {},
                toggleTheme = {},
                openApps = {},
                unpair = {},
                deleteLocalData = {},
                loadDemoWeek = null,
            )
        compose.setContent {
            StreamTrackTheme {
                NavigationLayout(Destination.SETTINGS, onNavigate = {}) {
                    SettingsScreen(SettingsModel(Settings(serverUrl = "https://tv.example.com"), emptyList()), actions)
                }
            }
        }
        press(Key.DirectionRight)
        // Focus enters the list beside the current tab; walk up to its first row.
        repeat(6) { press(Key.DirectionUp) }
        compose.onNodeWithText("Sync interval").assertIsFocusedAncestor()
        press(Key.DirectionCenter)
        assertEquals(60, interval)
        repeat(5) { press(Key.DirectionDown) }
        compose.onNodeWithText("Server").assertIsFocusedAncestor()
        press(Key.DirectionCenter)
        compose.onNodeWithText("Cancel").assertIsFocused()
    }

    private fun showToday(recompositions: MutableIntState? = null) {
        val model = DashboardFixture.model()
        val catalog = DashboardFixture.catalog()
        compose.setContent {
            StreamTrackTheme {
                NavigationLayout(Destination.TODAY, onNavigate = {}) {
                    recompositions?.intValue
                    DashboardScreen(model, catalog, onOpenSetup = {}, onSyncNow = {})
                }
            }
        }
    }

    /** Node ids change when a lazy item is scrolled out and back, so a stop is identified by its look. */
    private fun focusedStop(): String {
        val node = compose.onNode(isFocused()).fetchSemanticsNode()
        return "${node.boundsInRoot}${node.config.getOrNull(SemanticsProperties.ContentDescription)}"
    }

    /** Presses [key] until focus stops moving and returns how many presses moved it. */
    private fun walk(key: Key): Int {
        var moves = 0
        while (moves < MAX_STEPS) {
            val before = focusedStop()
            press(key)
            if (focusedStop() == before) return moves
            moves++
        }
        return moves
    }

    @Test
    fun `one Right press from the rail lands on Sync now`() {
        showToday()
        compose.onNodeWithTag(navTag(Destination.TODAY)).assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithText("Sync now").assertIsFocusedAncestor()
    }

    @Test
    fun `Down walks through the cards of Today and Up returns to Sync now without a stop`() {
        showToday()
        press(Key.DirectionRight)
        // Remote presses (beside Screen time), Top apps, Active hours, Daily limits, This week, Sync.
        assertEquals(DOWN_STOPS, walk(Key.DirectionDown))
        // Back up the same column, then one more stop on Screen time before Sync now.
        assertTrue(walk(Key.DirectionUp) >= DOWN_STOPS)
        compose.onNodeWithText("Sync now").assertIsFocusedAncestor()
    }

    @Test
    fun `the rail takes focus only when it first appears, not on later recompositions`() {
        val recompositions = mutableIntStateOf(0)
        showToday(recompositions)
        press(Key.DirectionRight)
        val inContent = focusedStop()
        recompositions.intValue++
        compose.waitForIdle()
        assertEquals(inContent, focusedStop())
        compose.onNodeWithTag(navTag(Destination.TODAY)).assertIsNotFocused()
    }
}
