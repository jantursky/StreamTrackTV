package com.apptiary.streamtracktv.collector

import android.view.KeyEvent
import com.apptiary.streamtracktv.core.model.KeyClass

private val dpadKeys =
    setOf(
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_UP_LEFT,
        KeyEvent.KEYCODE_DPAD_UP_RIGHT,
        KeyEvent.KEYCODE_DPAD_DOWN_LEFT,
        KeyEvent.KEYCODE_DPAD_DOWN_RIGHT,
    )

private val selectKeys =
    setOf(
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_BUTTON_A,
    )

private val backKeys = setOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BUTTON_B)

private val homeKeys = setOf(KeyEvent.KEYCODE_HOME)

private val mediaKeys =
    setOf(
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_STOP,
        KeyEvent.KEYCODE_MEDIA_NEXT,
        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        KeyEvent.KEYCODE_MEDIA_REWIND,
        KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
        KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
        KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
        KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
        KeyEvent.KEYCODE_MEDIA_RECORD,
        KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK,
        KeyEvent.KEYCODE_CAPTIONS,
    )

private val volumeKeys = setOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE)

/** Reduces a key code to its class. The code itself is discarded right here and stored nowhere. */
fun classifyKey(keyCode: Int): KeyClass =
    when (keyCode) {
        in dpadKeys -> KeyClass.DPAD
        in selectKeys -> KeyClass.SELECT
        in backKeys -> KeyClass.BACK
        in homeKeys -> KeyClass.HOME
        in mediaKeys -> KeyClass.MEDIA
        in volumeKeys -> KeyClass.VOLUME
        else -> KeyClass.OTHER
    }
