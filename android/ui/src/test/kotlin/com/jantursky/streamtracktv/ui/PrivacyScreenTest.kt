package com.jantursky.streamtracktv.ui

import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import com.jantursky.streamtracktv.ui.privacy.EVENTS_TAG
import com.jantursky.streamtracktv.ui.privacy.PayloadPreview
import com.jantursky.streamtracktv.ui.privacy.PrivacyScreen
import com.jantursky.streamtracktv.ui.theme.StreamTrackTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PrivacyScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `tells how many rows the server refused`() {
        compose.setContent {
            StreamTrackTheme { PrivacyScreen(PayloadPreview(json = "{}", rows = 0, rejectedRows = 2)) }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        compose.onNodeWithText("Nothing is waiting to be uploaded.").assertExists()
        compose.onNodeWithText("The server refused 2 rows one by one", substring = true).assertExists()
    }

    @Test
    fun `lists the security events that are waiting, exactly as they will be sent`() {
        val events = """{"events":[{"eventId":"x","type":"pin_wrong","at":"2026-11-09T10:15:00Z","value":3}]}"""
        compose.setContent {
            StreamTrackTheme {
                PrivacyScreen(PayloadPreview(json = "{}", rows = 0, rejectedRows = 0, eventsJson = events, events = 1))
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        compose.onNodeWithText("1 security event is waiting", substring = true).assertExists()
        compose.onNodeWithTag(EVENTS_TAG).assertExists()
        compose.onNodeWithText("No security events are waiting to be uploaded.").assertDoesNotExist()
    }

    @Test
    fun `says so when no security event is waiting`() {
        compose.setContent {
            StreamTrackTheme { PrivacyScreen(PayloadPreview(json = "{}", rows = 0, rejectedRows = 0)) }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        compose.onNodeWithText("No security events are waiting to be uploaded.").assertExists()
    }

    @Test
    fun `names security events among the data collected and the PIN among what is never collected`() {
        compose.setContent { StreamTrackTheme { PrivacyScreen(null) } }
        compose.onNodeWithText("Security events: when the app is opened", substring = true).assertExists()
        compose.onNodeWithText("Your PIN or any digit you type on the lock screen").assertExists()
    }
}
