package com.jantursky.streamtracktv.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.jantursky.streamtracktv.core.pairing.ServerUrl
import com.jantursky.streamtracktv.sync.PairingFailure
import com.jantursky.streamtracktv.sync.PairingState
import com.jantursky.streamtracktv.ui.pairing.CODE_TAG
import com.jantursky.streamtracktv.ui.pairing.CodeScreen
import com.jantursky.streamtracktv.ui.pairing.PairingUiState
import com.jantursky.streamtracktv.ui.pairing.SERVER_FIELD_TAG
import com.jantursky.streamtracktv.ui.pairing.ServerInputError
import com.jantursky.streamtracktv.ui.pairing.ServerScreen
import com.jantursky.streamtracktv.ui.theme.StreamTrackTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class PairingScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val server = ServerUrl.parse("tv.example.com", allowHttp = false)!!

    @Test
    fun `server field takes focus, accepts text and submits`() {
        var input = ""
        var submitted = 0
        compose.setContent {
            StreamTrackTheme {
                ServerScreen(PairingUiState.EnterServer(input), { input = it }, { submitted++ })
            }
        }
        compose.onNodeWithTag(SERVER_FIELD_TAG).assertIsFocused()
        compose.onNodeWithTag(SERVER_FIELD_TAG).performTextInput("tv.example.com")
        assertEquals("tv.example.com", input)
        compose.onNodeWithText("Continue").performClick()
        assertEquals(1, submitted)
    }

    @Test
    fun `shows why an address was refused`() {
        compose.setContent {
            StreamTrackTheme {
                ServerScreen(
                    PairingUiState.EnterServer("http://x", ServerInputError.HTTPS_REQUIRED),
                    {},
                    {},
                )
            }
        }
        compose.onNodeWithText("Use an https:// address.").assertIsDisplayed()
    }

    @Test
    fun `shows the code large with the page to enter it on`() {
        val state =
            PairingUiState.Connecting(
                server,
                PairingState.ShowingCode(
                    "K7M2QX",
                    server.pairLink("K7M2QX"),
                    System.currentTimeMillis() + 600_000,
                ),
            )
        compose.setContent { StreamTrackTheme { CodeScreen(state, {}, {}) } }
        compose.onNodeWithTag(CODE_TAG).assertIsDisplayed()
        compose.onNodeWithText("K 7 M 2 Q X").assertIsDisplayed()
        compose.onNodeWithText("https://tv.example.com/pair", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Change server").assertIsFocused()
    }

    @Test
    fun `a failure offers a retry with focus on it`() {
        var retried = 0
        val state = PairingUiState.Connecting(server, PairingState.Failed(PairingFailure.NETWORK))
        compose.setContent { StreamTrackTheme { CodeScreen(state, { retried++ }, {}) } }
        compose.onNodeWithText("The server cannot be reached", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Try again").assertIsFocused().performClick()
        assertEquals(1, retried)
    }
}
