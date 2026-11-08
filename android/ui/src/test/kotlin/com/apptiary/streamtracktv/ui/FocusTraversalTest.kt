package com.apptiary.streamtracktv.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.apptiary.streamtracktv.data.settings.Settings
import com.apptiary.streamtracktv.ui.components.PillButton
import com.apptiary.streamtracktv.ui.settings.SettingsActions
import com.apptiary.streamtracktv.ui.settings.SettingsModel
import com.apptiary.streamtracktv.ui.settings.SettingsScreen
import com.apptiary.streamtracktv.ui.theme.StreamTrackTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
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
        repeat(4) { press(Key.DirectionDown) }
        compose.onNodeWithText("Server").assertIsFocusedAncestor()
        press(Key.DirectionCenter)
        compose.onNodeWithText("Cancel").assertIsFocused()
    }
}
