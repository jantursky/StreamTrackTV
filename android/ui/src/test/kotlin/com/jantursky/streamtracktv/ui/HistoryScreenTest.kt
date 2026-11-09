package com.jantursky.streamtracktv.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.jantursky.streamtracktv.core.insights.HistoryRange
import com.jantursky.streamtracktv.core.insights.buildHistory
import com.jantursky.streamtracktv.core.model.BucketSet
import com.jantursky.streamtracktv.core.model.HourSlot
import com.jantursky.streamtracktv.core.model.KeyBucket
import com.jantursky.streamtracktv.core.model.KeyClass
import com.jantursky.streamtracktv.core.model.ScreenBucket
import com.jantursky.streamtracktv.core.model.UsageBucket
import com.jantursky.streamtracktv.ui.history.HISTORY_CLEAR_FILTER_TAG
import com.jantursky.streamtracktv.ui.history.HISTORY_NEXT_TAG
import com.jantursky.streamtracktv.ui.history.HISTORY_PREVIOUS_TAG
import com.jantursky.streamtracktv.ui.history.HISTORY_TODAY_TAG
import com.jantursky.streamtracktv.ui.history.HistoryActions
import com.jantursky.streamtracktv.ui.history.HistoryScreen
import com.jantursky.streamtracktv.ui.history.HistoryScreenModel
import com.jantursky.streamtracktv.ui.history.HistoryState
import com.jantursky.streamtracktv.ui.history.historyAppTag
import com.jantursky.streamtracktv.ui.history.historyRangeTag
import com.jantursky.streamtracktv.ui.theme.StreamTrackTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import kotlin.test.assertEquals

private const val MIN = 60_000L

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class HistoryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.parse("2026-11-08")
    private val netflix = "com.netflix.ninja"
    private val youtube = "com.google.android.youtube.tv"
    private val buckets =
        BucketSet(
            usage =
                listOf(
                    UsageBucket(HourSlot(today, 19), netflix, 40 * MIN),
                    UsageBucket(HourSlot(today, 20), youtube, 20 * MIN),
                ),
            keys = listOf(KeyBucket(HourSlot(today, 19), KeyClass.DPAD, 30)),
            screen = listOf(ScreenBucket(HourSlot(today, 19), 50 * MIN)),
        )
    private val labels = mapOf(netflix to "Netflix", youtube to "YouTube")

    private val calls = mutableListOf<String>()
    private val actions =
        HistoryActions(
            step = { calls += "step$it" },
            goToday = { calls += "today" },
            setRange = { calls += "range${it.name}" },
            setApp = { calls += "app$it" },
        )

    private fun show(state: HistoryState, data: BucketSet = buckets, today: LocalDate = this.today) {
        val history = buildHistory(state.endDay, state.range, data, appFilter = state.appFilter)
        compose.setContent {
            StreamTrackTheme {
                HistoryScreen(state, HistoryScreenModel(history, labels), null, today, actions)
            }
        }
    }

    @Test
    fun `range pills and day steps call their actions, next and today are hidden on today`() {
        show(HistoryState(today))
        compose.onNodeWithTag(historyRangeTag(HistoryRange.WEEK)).performClick()
        compose.onNodeWithTag(HISTORY_PREVIOUS_TAG).performClick()
        compose.onNodeWithTag(HISTORY_NEXT_TAG).assertDoesNotExist()
        compose.onNodeWithTag(HISTORY_TODAY_TAG).assertDoesNotExist()
        assertEquals(listOf("rangeWEEK", "step-1"), calls)
    }

    @Test
    fun `next day and jump to today appear on an earlier day`() {
        show(HistoryState(today.minusDays(2)))
        compose.onNodeWithTag(HISTORY_NEXT_TAG).performClick()
        compose.onNodeWithTag(HISTORY_TODAY_TAG).performClick()
        assertEquals(listOf("step1", "today"), calls)
    }

    @Test
    fun `an app row sets the filter and the clear button removes it`() {
        show(HistoryState(today))
        scrollTo(hasTestTag(historyAppTag(youtube)))
        compose.onNodeWithTag(historyAppTag(youtube)).performClick()
        assertEquals(listOf("app$youtube"), calls)
    }

    @Test
    fun `filtered view offers to clear the filter`() {
        show(HistoryState(today, appFilter = youtube))
        scrollTo(hasText("Showing only YouTube"))
        compose.onNodeWithText("Showing only YouTube").assertIsDisplayed()
        compose.onNodeWithTag(HISTORY_CLEAR_FILTER_TAG).performClick()
        assertEquals(listOf("appnull"), calls)
    }

    @Test
    fun `a day without data says so and still steps`() {
        show(HistoryState(today.minusDays(9)))
        compose.onNodeWithText("Nothing was recorded in this range. Step to another day.").assertIsDisplayed()
        compose.onNodeWithTag(HISTORY_PREVIOUS_TAG).performClick()
        assertEquals(listOf("step-1"), calls)
    }

    @Test
    fun `hours with data show their totals and key classes are listed`() {
        show(HistoryState(today))
        compose.onNodeWithText("1 h").assertIsDisplayed()
        scrollTo(hasText("Netflix"))
        compose.onNodeWithText("Netflix").assertIsDisplayed()
        scrollTo(hasText("D-Pad"))
        compose.onNodeWithText("D-Pad").assertIsDisplayed()
    }

    private fun scrollTo(matcher: SemanticsMatcher) {
        compose.onNode(hasScrollAction()).performScrollToNode(matcher)
    }
}
