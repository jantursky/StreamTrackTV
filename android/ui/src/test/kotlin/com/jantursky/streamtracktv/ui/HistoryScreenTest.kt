package com.jantursky.streamtracktv.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.jantursky.streamtracktv.core.insights.HistoryRange
import com.jantursky.streamtracktv.core.insights.QuickPeriod
import com.jantursky.streamtracktv.core.insights.buildHistory
import com.jantursky.streamtracktv.core.insights.compare
import com.jantursky.streamtracktv.core.insights.highlights
import com.jantursky.streamtracktv.core.insights.previousPeriod
import com.jantursky.streamtracktv.core.insights.totals
import com.jantursky.streamtracktv.core.model.BucketSet
import com.jantursky.streamtracktv.core.model.HourSlot
import com.jantursky.streamtracktv.core.model.KeyBucket
import com.jantursky.streamtracktv.core.model.KeyClass
import com.jantursky.streamtracktv.core.model.ScreenBucket
import com.jantursky.streamtracktv.core.model.UsageBucket
import com.jantursky.streamtracktv.ui.history.HISTORY_CLEAR_FILTER_TAG
import com.jantursky.streamtracktv.ui.history.HISTORY_COMPARE_TAG
import com.jantursky.streamtracktv.ui.history.HISTORY_HIGHLIGHTS_TAG
import com.jantursky.streamtracktv.ui.history.HISTORY_NEXT_TAG
import com.jantursky.streamtracktv.ui.history.HISTORY_PREVIOUS_TAG
import com.jantursky.streamtracktv.ui.history.HISTORY_TODAY_TAG
import com.jantursky.streamtracktv.ui.history.HistoryActions
import com.jantursky.streamtracktv.ui.history.HistoryScreen
import com.jantursky.streamtracktv.ui.history.HistoryScreenModel
import com.jantursky.streamtracktv.ui.history.HistoryState
import com.jantursky.streamtracktv.ui.history.historyAppTag
import com.jantursky.streamtracktv.ui.history.historyDayTag
import com.jantursky.streamtracktv.ui.history.historyQuickTag
import com.jantursky.streamtracktv.ui.history.historyRangeTag
import com.jantursky.streamtracktv.ui.theme.StreamTrackTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
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
            setQuick = { calls += "quick${it.name}" },
            openDay = { calls += "day$it" },
        )

    private fun show(state: HistoryState, data: BucketSet = buckets, today: LocalDate = this.today) {
        val period = state.period(today, DayOfWeek.MONDAY)
        val history = buildHistory(period, data, appFilter = state.appFilter)
        val before = buildHistory(previousPeriod(period, state.unit), data, appFilter = state.appFilter)
        val model = HistoryScreenModel(history, labels, compare(history.totals(), before.totals()), highlights(history))
        compose.setContent {
            StreamTrackTheme {
                HistoryScreen(state, model, null, today, actions)
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

    private val yesterday = today.minusDays(1)
    private val twoDays =
        BucketSet(
            usage = buckets.usage + UsageBucket(HourSlot(yesterday, 20), netflix, 30 * MIN),
            keys = buckets.keys,
            screen = buckets.screen + ScreenBucket(HourSlot(yesterday, 20), 25 * MIN),
        )

    @Test
    fun `quick period pills are focusable, call their action and the showing one is selected`() {
        show(HistoryState(today).withQuick(QuickPeriod.YESTERDAY, today, DayOfWeek.MONDAY), twoDays)
        QuickPeriod.entries.forEach { compose.onNodeWithTag(historyQuickTag(it)).assertIsDisplayed() }
        compose.onNodeWithTag(historyQuickTag(QuickPeriod.LAST_WEEK)).performClick()
        assertEquals(listOf("quickLAST_WEEK"), calls)
        compose
            .onNodeWithText(
                yesterday.format(
                    java.time.format.DateTimeFormatter
                        .ofLocalizedDate(java.time.format.FormatStyle.MEDIUM),
                ),
            ).assertIsDisplayed()
    }

    @Test
    fun `step buttons read as week or month steps for a calendar period`() {
        show(HistoryState(today).withQuick(QuickPeriod.LAST_WEEK, today, DayOfWeek.MONDAY))
        compose.onNodeWithText("Previous week").assertIsDisplayed()
        compose.onNodeWithText("Next week").assertIsDisplayed()
    }

    @Test
    fun `comparison shows the difference and is hidden without earlier data`() {
        show(HistoryState(today), twoDays)
        scrollTo(hasTestTag(HISTORY_COMPARE_TAG))
        compose.onNodeWithText("Screen time: +25 min (+100 %)").assertIsDisplayed()
    }

    @Test
    fun `no comparison and no highlights card when the earlier period is empty or nothing was recorded`() {
        show(HistoryState(today))
        compose.onNodeWithTag(HISTORY_COMPARE_TAG).assertDoesNotExist()
    }

    @Test
    fun `highlights show peak hour on a day and a daily average on a week`() {
        show(HistoryState(today), twoDays)
        scrollTo(hasTestTag(HISTORY_HIGHLIGHTS_TAG))
        compose.onNodeWithText("Peak hour").assertIsDisplayed()
        compose.onNodeWithText("Daily average").assertDoesNotExist()
    }

    @Test
    fun `week view lists every day and opening a row calls openDay`() {
        show(HistoryState(today, HistoryRange.WEEK), twoDays)
        (0..6).forEach {
            val tag = historyDayTag(today.minusDays(it.toLong()))
            scrollTo(hasTestTag(tag))
            compose.onNodeWithTag(tag).assertExists()
        }
        scrollTo(hasTestTag(historyDayTag(yesterday)))
        compose.onNodeWithTag(historyDayTag(yesterday)).performClick()
        assertEquals(listOf("day$yesterday"), calls)
        scrollTo(hasText("Daily average"))
        compose.onNodeWithText("Daily average").assertIsDisplayed()
    }

    @Test
    fun `a day row can be reached and opened with the D-pad alone`() {
        show(HistoryState(today, HistoryRange.WEEK), twoDays)
        val target = historyDayTag(yesterday)
        var focused = false
        repeat(40) {
            if (!focused) {
                compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
                compose.waitForIdle()
                focused = runCatching { compose.onNodeWithTag(target).assertIsFocused() }.isSuccess
            }
        }
        assertEquals(true, focused)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        assertEquals(listOf("day$yesterday"), calls)
    }

    private fun showInShell(state: HistoryState, data: BucketSet) {
        val period = state.period(today, DayOfWeek.MONDAY)
        val history = buildHistory(period, data)
        val model = HistoryScreenModel(history, labels)
        compose.setContent {
            StreamTrackTheme {
                NavigationLayout(Destination.HISTORY, onNavigate = {}) { HistoryScreen(state, model, null, today, actions) }
            }
        }
    }

    @Test
    @Config(qualifiers = "w700dp-h540dp-xhdpi")
    fun `on a TV all six quick periods show in full and none wraps its text`() {
        showInShell(HistoryState(today), twoDays)
        val heights =
            QuickPeriod.entries.map {
                compose
                    .onNodeWithTag(historyQuickTag(it))
                    .fetchSemanticsNode()
                    .size.height
            }
        assertEquals(1, heights.toSet().size, "pills differ in height, so one wrapped: $heights")
        QuickPeriod.entries.forEach { compose.onNodeWithTag(historyQuickTag(it)).assertIsDisplayed() }
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-xhdpi")
    fun `Left from the first control and from a row far down both return to the rail`() {
        showInShell(HistoryState(today, HistoryRange.WEEK), twoDays)
        compose.onNodeWithTag(historyRangeTag(HistoryRange.WEEK)).requestFocus()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.waitForIdle()
        compose.onNodeWithTag(historyRangeTag(HistoryRange.DAY)).assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.waitForIdle()
        compose.onNodeWithTag(navTag(Destination.HISTORY)).assertIsFocused()
        val far = historyDayTag(today.minusDays(5))
        scrollTo(hasTestTag(far))
        compose.onNodeWithTag(far).requestFocus()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.waitForIdle()
        compose.onNodeWithTag(navTag(Destination.HISTORY)).assertIsFocused()
    }

    @Test
    fun `day view has no day list`() {
        show(HistoryState(today), twoDays)
        compose.onNodeWithTag(historyDayTag(today)).assertDoesNotExist()
    }

    private fun scrollTo(matcher: SemanticsMatcher) {
        compose.onNode(hasScrollAction()).performScrollToNode(matcher)
    }
}
