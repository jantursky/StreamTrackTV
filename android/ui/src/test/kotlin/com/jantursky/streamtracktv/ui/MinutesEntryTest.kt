package com.jantursky.streamtracktv.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jantursky.streamtracktv.ui.lock.PIN_DELETE_TAG
import com.jantursky.streamtracktv.ui.lock.PIN_SUBMIT_TAG
import com.jantursky.streamtracktv.ui.lock.pinKeyTag
import com.jantursky.streamtracktv.ui.settings.ENTRY_ERROR_TAG
import com.jantursky.streamtracktv.ui.settings.ENTRY_REMOVE_TAG
import com.jantursky.streamtracktv.ui.settings.EntryKind
import com.jantursky.streamtracktv.ui.settings.MinutesEntryScreen
import com.jantursky.streamtracktv.ui.settings.formatClock
import com.jantursky.streamtracktv.ui.theme.StreamTrackTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-xhdpi")
class MinutesEntryTest {
    @get:Rule
    val compose = createComposeRule()

    private val saved = mutableListOf<Int?>()
    private var backs = 0

    private fun show(kind: EntryKind, current: Int? = null) {
        saved.clear()
        backs = 0
        compose.setContent {
            StreamTrackTheme {
                MinutesEntryScreen(
                    title = "Screen time limit",
                    kind = kind,
                    current = current,
                    onSave = { saved += it },
                    onBack = { backs++ },
                )
            }
        }
    }

    private fun press(digits: String) = digits.forEach { compose.onNodeWithTag(pinKeyTag(it.toString())).performClick() }

    @Test
    fun `saves a typed number of minutes`() {
        show(EntryKind.MINUTES)
        press("95")
        compose.onNodeWithTag(PIN_SUBMIT_TAG).performClick()
        assertEquals(listOf<Int?>(95), saved)
    }

    @Test
    fun `refuses 0 and 1441 and keeps the old value`() {
        show(EntryKind.MINUTES, current = 120)
        press("0")
        compose.onNodeWithTag(PIN_SUBMIT_TAG).performClick()
        compose.onNodeWithTag(ENTRY_ERROR_TAG).assertIsDisplayed()
        assertTrue(saved.isEmpty())

        compose.onNodeWithTag(PIN_DELETE_TAG).performClick()
        press("1441")
        compose.onNodeWithTag(PIN_SUBMIT_TAG).performClick()
        compose.onNodeWithTag(ENTRY_ERROR_TAG).assertIsDisplayed()
        assertTrue(saved.isEmpty(), "1441 is above the one-day maximum")
    }

    @Test
    fun `remove clears the value and back leaves the screen`() {
        show(EntryKind.MINUTES, current = 60)
        compose.onNodeWithTag(ENTRY_REMOVE_TAG).performClick()
        assertEquals(listOf<Int?>(null), saved)

        compose.onNodeWithText("Back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `a clock time is typed as HHMM and saved as minutes since midnight`() {
        show(EntryKind.CLOCK)
        press("2230")
        compose.onNodeWithTag(PIN_SUBMIT_TAG).performClick()
        assertEquals(listOf<Int?>(22 * 60 + 30), saved)
        assertEquals("22:30", formatClock(22 * 60 + 30))
    }

    @Test
    fun `an impossible clock time is refused`() {
        show(EntryKind.CLOCK)
        press("2460")
        compose.onNodeWithTag(PIN_SUBMIT_TAG).performClick()
        assertTrue(saved.isEmpty())
        compose.onNodeWithTag(ENTRY_ERROR_TAG).assertIsDisplayed()
    }
}
