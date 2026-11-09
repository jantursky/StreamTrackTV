package com.jantursky.streamtracktv.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.jantursky.streamtracktv.sync.PinResetFailure
import com.jantursky.streamtracktv.sync.PinResetState
import com.jantursky.streamtracktv.ui.lock.FORGOT_PIN_TAG
import com.jantursky.streamtracktv.ui.lock.ForgotPinScreen
import com.jantursky.streamtracktv.ui.lock.LockScreen
import com.jantursky.streamtracktv.ui.lock.LockUiState
import com.jantursky.streamtracktv.ui.lock.PIN_DELETE_TAG
import com.jantursky.streamtracktv.ui.lock.PIN_MESSAGE_TAG
import com.jantursky.streamtracktv.ui.lock.PIN_SUBMIT_TAG
import com.jantursky.streamtracktv.ui.lock.PinFlowError
import com.jantursky.streamtracktv.ui.lock.PinFlowMode
import com.jantursky.streamtracktv.ui.lock.PinFlowScreen
import com.jantursky.streamtracktv.ui.lock.PinFlowState
import com.jantursky.streamtracktv.ui.lock.PinStep
import com.jantursky.streamtracktv.ui.lock.RESET_CANCEL_TAG
import com.jantursky.streamtracktv.ui.lock.RESET_CODE_TAG
import com.jantursky.streamtracktv.ui.lock.RESET_RETRY_TAG
import com.jantursky.streamtracktv.ui.lock.formatWait
import com.jantursky.streamtracktv.ui.lock.pinKeyTag
import com.jantursky.streamtracktv.ui.theme.StreamTrackTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-xhdpi")
class PinScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private fun lock(state: LockUiState = LockUiState()) {
        compose.setContent {
            StreamTrackTheme {
                LockScreen(
                    state,
                    onDigit = { events += "digit$it" },
                    onDelete = { events += "delete" },
                    onSubmit = { events += "submit" },
                    onForgot = { events += "forgot" },
                )
            }
        }
    }

    @Test
    fun `the keypad starts on its first key and every key reports its digit`() {
        lock()
        compose.onNodeWithTag(pinKeyTag("1")).assertIsFocused()
        ('0'..'9').forEach { compose.onNodeWithTag(pinKeyTag(it.toString())).performClick() }
        compose.onNodeWithTag(PIN_DELETE_TAG).performClick()
        compose.onNodeWithTag(PIN_SUBMIT_TAG).performClick()
        assertEquals(('0'..'9').map { "digit$it" } + listOf("delete", "submit"), events)
    }

    @Test
    fun `number keys and backspace of a remote work too`() {
        lock()
        compose.onRoot().performKeyInput {
            pressKey(Key.Four)
            pressKey(Key.NumPad8)
            pressKey(Key.Backspace)
            pressKey(Key.DirectionDown)
        }
        assertEquals(listOf("digit4", "digit8", "delete"), events)
    }

    @Test
    fun `a wrong PIN says so`() {
        lock(LockUiState(wrong = true))
        compose.onNodeWithText("Wrong PIN. Try again.").assertIsDisplayed()
    }

    @Test
    fun `during a wait the message counts down and the keys do nothing`() {
        lock(LockUiState(waitMs = 30_000))
        compose.onNodeWithText("Too many tries. Try again in 0:30.").assertIsDisplayed()
        compose.onNodeWithTag(pinKeyTag("1")).performClick()
        compose.onNodeWithTag(PIN_SUBMIT_TAG).performClick()
        assertEquals(emptyList(), events)
    }

    @Test
    fun `forgot PIN is offered on the lock screen`() {
        lock()
        compose.onNodeWithTag(FORGOT_PIN_TAG).performClick()
        assertEquals(listOf("forgot"), events)
    }

    @Test
    fun `waits read as minutes and seconds, rounded up`() {
        assertEquals("0:30", formatWait(30_000))
        assertEquals("0:01", formatWait(1))
        assertEquals("2:05", formatWait(125_000))
        assertEquals("0:00", formatWait(-5))
    }

    private fun flow(state: PinFlowState) {
        compose.setContent {
            StreamTrackTheme {
                PinFlowScreen(state, { events += "digit$it" }, { events += "delete" }, { events += "submit" }, { events += "cancel" })
            }
        }
    }

    @Test
    fun `the settings flow names the step and can be cancelled`() {
        flow(PinFlowState(PinFlowMode.CHANGE, PinStep.CURRENT))
        compose.onNodeWithText("Enter your current PIN").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(listOf("cancel"), events)
    }

    @Test
    fun `the flow says why an entry was refused`() {
        flow(PinFlowState(PinFlowMode.SET, PinStep.NEW, error = PinFlowError.TOO_SHORT))
        compose.onNodeWithText("Use 4 to 8 digits.").assertIsDisplayed()
        compose.onNodeWithTag(PIN_MESSAGE_TAG).assertIsDisplayed()
    }

    @Test
    fun `the reset screen shows the code and offers a retry only when one can help`() {
        compose.setContent {
            StreamTrackTheme {
                ForgotPinScreen(
                    PinResetState.ShowingCode("K7M2QX", "https://tv.example.com/pin-reset?code=K7M2QX", 1),
                    onRetry = { events += "retry" },
                    onCancel = { events += "cancel" },
                )
            }
        }
        compose.onNodeWithTag(RESET_CODE_TAG).assertIsDisplayed()
        compose.onNodeWithTag(RESET_CANCEL_TAG).performClick()
        assertEquals(listOf("cancel"), events)
    }

    @Test
    fun `an unpaired TV gets no retry button and a way out`() {
        compose.setContent {
            StreamTrackTheme {
                ForgotPinScreen(PinResetState.Failed(PinResetFailure.NOT_PAIRED), { events += "retry" }, { events += "cancel" })
            }
        }
        compose.onNodeWithTag(RESET_CANCEL_TAG).assertIsDisplayed()
        assertFalse(
            runCatching { compose.onNodeWithTag(RESET_RETRY_TAG).assertIsDisplayed() }.isSuccess,
        )
    }

    @Test
    fun `an offline TV can retry`() {
        compose.setContent {
            StreamTrackTheme {
                ForgotPinScreen(PinResetState.Failed(PinResetFailure.NETWORK), { events += "retry" }, { events += "cancel" })
            }
        }
        compose.onNodeWithTag(RESET_RETRY_TAG).performClick()
        assertEquals(listOf("retry"), events)
    }
}
