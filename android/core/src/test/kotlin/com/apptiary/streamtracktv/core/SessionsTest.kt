package com.apptiary.streamtracktv.core

import com.apptiary.streamtracktv.core.aggregation.AppSession
import com.apptiary.streamtracktv.core.aggregation.DeviceEvent.ActivityPaused
import com.apptiary.streamtracktv.core.aggregation.DeviceEvent.ActivityResumed
import com.apptiary.streamtracktv.core.aggregation.DeviceEvent.DeviceStartup
import com.apptiary.streamtracktv.core.aggregation.DeviceEvent.ScreenOff
import com.apptiary.streamtracktv.core.aggregation.DeviceEvent.ScreenOn
import com.apptiary.streamtracktv.core.aggregation.foregroundSessions
import com.apptiary.streamtracktv.core.aggregation.screenOnIntervals
import com.apptiary.streamtracktv.core.model.TimeInterval
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionsTest {
    private val netflix = "com.netflix.ninja"
    private val youtube = "com.google.android.youtube.tv"

    @Test
    fun `an app is in the foreground from resume to pause`() {
        val sessions =
            foregroundSessions(
                listOf(ActivityResumed(100, netflix, "Main"), ActivityPaused(400, netflix, "Main")),
                endMs = 1000,
            )
        assertEquals(listOf(AppSession(netflix, TimeInterval(100, 400))), sessions)
    }

    @Test
    fun `switching apps ends the previous session`() {
        val sessions =
            foregroundSessions(
                listOf(ActivityResumed(100, netflix, "Main"), ActivityResumed(300, youtube, "Main"), ActivityPaused(320, netflix, "Main")),
                endMs = 500,
            )
        assertEquals(
            listOf(AppSession(netflix, TimeInterval(100, 300)), AppSession(youtube, TimeInterval(300, 500))),
            sessions,
        )
    }

    @Test
    fun `moving between activities of one app keeps one session`() {
        val sessions =
            foregroundSessions(
                listOf(
                    ActivityResumed(100, netflix, "Browse"),
                    ActivityResumed(200, netflix, "Player"),
                    ActivityPaused(201, netflix, "Browse"),
                    ActivityPaused(900, netflix, "Player"),
                ),
                endMs = 1000,
            )
        assertEquals(listOf(AppSession(netflix, TimeInterval(100, 900))), sessions)
    }

    @Test
    fun `screen off ends the session and unsorted input is handled`() {
        val sessions =
            foregroundSessions(
                listOf(ScreenOff(500), ActivityResumed(100, netflix, "Main"), ScreenOn(50), ActivityPaused(600, netflix, "Main")),
                endMs = 1000,
            )
        assertEquals(listOf(AppSession(netflix, TimeInterval(100, 500))), sessions)
    }

    @Test
    fun `a pause of another app is ignored`() {
        val sessions =
            foregroundSessions(
                listOf(ActivityResumed(100, netflix, "Main"), ActivityPaused(200, youtube, "Main")),
                endMs = 300,
            )
        assertEquals(listOf(AppSession(netflix, TimeInterval(100, 300))), sessions)
    }

    @Test
    fun `screen on time follows interactive events`() {
        val intervals =
            screenOnIntervals(
                listOf(ScreenOn(100), ScreenOff(300), ScreenOn(400), ScreenOn(450)),
                startMs = 0,
                endMs = 1000,
                fallback = emptyList(),
            )
        assertEquals(listOf(TimeInterval(100, 300), TimeInterval(450, 1000)), intervals)
    }

    @Test
    fun `a boot after a power loss closes the session and the screen at the last event`() {
        val events =
            listOf(
                ScreenOn(0),
                ActivityResumed(100, netflix, "Browse"),
                ActivityResumed(200, netflix, "Player"),
                DeviceStartup(70_000),
                ScreenOn(70_100),
                ActivityResumed(70_200, youtube, "Main"),
            )
        val sessions = foregroundSessions(events, endMs = 80_000)
        assertEquals(listOf(AppSession(netflix, TimeInterval(100, 200)), AppSession(youtube, TimeInterval(70_200, 80_000))), sessions)
        assertEquals(
            listOf(TimeInterval(0, 200), TimeInterval(70_100, 80_000)),
            screenOnIntervals(events, startMs = 0, endMs = 80_000, fallback = sessions),
        )
    }

    @Test
    fun `a screen on while the screen is on is a missed screen off`() {
        val events =
            listOf(
                ScreenOn(0),
                ActivityResumed(100, netflix, "Browse"),
                ActivityResumed(200, netflix, "Player"),
                ScreenOn(5000),
                ActivityResumed(5100, youtube, "Main"),
            )
        val sessions = foregroundSessions(events, endMs = 6000)
        assertEquals(listOf(AppSession(netflix, TimeInterval(100, 200)), AppSession(youtube, TimeInterval(5100, 6000))), sessions)
        assertEquals(
            listOf(TimeInterval(0, 200), TimeInterval(5000, 6000)),
            screenOnIntervals(events, startMs = 0, endMs = 6000, fallback = sessions),
        )
    }

    @Test
    fun `an app resuming just before the screen turns on keeps its session`() {
        val events = listOf(ScreenOff(0), ActivityResumed(100, netflix, "Main"), ScreenOn(101))
        assertEquals(listOf(AppSession(netflix, TimeInterval(100, 500))), foregroundSessions(events, endMs = 500))
    }

    @Test
    fun `a boot with nothing open changes nothing`() {
        val events = listOf(DeviceStartup(10), ScreenOn(20), ScreenOff(30))
        assertEquals(listOf(TimeInterval(20, 30)), screenOnIntervals(events, startMs = 0, endMs = 100, fallback = emptyList()))
        assertEquals(emptyList<AppSession>(), foregroundSessions(events, endMs = 100))
    }

    @Test
    fun `screen was on since the start when the first event turns it off`() {
        val intervals = screenOnIntervals(listOf(ScreenOff(300), ScreenOff(350)), startMs = 0, endMs = 1000, fallback = emptyList())
        assertEquals(listOf(TimeInterval(0, 300)), intervals)
    }

    @Test
    fun `without screen events the app sessions stand in`() {
        val intervals =
            screenOnIntervals(
                listOf(ActivityResumed(100, netflix, "Main")),
                startMs = 0,
                endMs = 1000,
                fallback = listOf(AppSession(netflix, TimeInterval(100, 300)), AppSession(youtube, TimeInterval(250, 400))),
            )
        assertEquals(listOf(TimeInterval(100, 400)), intervals)
    }
}
