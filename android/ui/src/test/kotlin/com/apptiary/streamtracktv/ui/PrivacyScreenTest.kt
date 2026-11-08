package com.apptiary.streamtracktv.ui

import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import com.apptiary.streamtracktv.ui.privacy.PayloadPreview
import com.apptiary.streamtracktv.ui.privacy.PrivacyScreen
import com.apptiary.streamtracktv.ui.theme.StreamTrackTheme
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
}
