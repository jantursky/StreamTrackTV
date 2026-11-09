package com.jantursky.streamtracktv.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.jantursky.streamtracktv.data.settings.Settings
import com.jantursky.streamtracktv.ui.lock.PinFlowMode
import com.jantursky.streamtracktv.ui.settings.SettingsActions
import com.jantursky.streamtracktv.ui.settings.SettingsModel
import com.jantursky.streamtracktv.ui.settings.SettingsScreen
import com.jantursky.streamtracktv.ui.theme.StreamTrackTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsPinTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<PinFlowMode>()

    private fun show(pinEnabled: Boolean) {
        val actions =
            SettingsActions(
                cycleInterval = {},
                cycleScreenLimit = {},
                toggleTheme = {},
                openApps = {},
                unpair = {},
                deleteLocalData = {},
                loadDemoWeek = null,
                openPin = { opened += it },
            )
        compose.setContent {
            StreamTrackTheme {
                SettingsScreen(SettingsModel(Settings(), emptyList(), pinEnabled), actions)
            }
        }
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    @Test
    fun `without a PIN the row turns it on`() {
        show(pinEnabled = false)
        scrollTo("PIN lock")
        compose.onNodeWithText("PIN lock").assertIsDisplayed()
        compose.onNodeWithText("PIN lock").performClick()
        assertEquals(listOf(PinFlowMode.SET), opened)
    }

    @Test
    fun `with a PIN the rows change it or turn it off`() {
        show(pinEnabled = true)
        scrollTo("PIN lock")
        compose.onNodeWithText("PIN lock").performClick()
        scrollTo("Turn off PIN")
        compose.onNodeWithText("Turn off PIN").performClick()
        assertEquals(listOf(PinFlowMode.CHANGE, PinFlowMode.DISABLE), opened)
    }
}
