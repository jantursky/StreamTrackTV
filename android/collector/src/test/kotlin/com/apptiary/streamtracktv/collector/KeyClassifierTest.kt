package com.apptiary.streamtracktv.collector

import android.view.KeyEvent
import com.apptiary.streamtracktv.core.model.KeyClass
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyClassifierTest {
    @Test
    fun `maps remote keys to their class`() {
        mapOf(
            KeyEvent.KEYCODE_DPAD_UP to KeyClass.DPAD,
            KeyEvent.KEYCODE_DPAD_DOWN_LEFT to KeyClass.DPAD,
            KeyEvent.KEYCODE_DPAD_CENTER to KeyClass.SELECT,
            KeyEvent.KEYCODE_ENTER to KeyClass.SELECT,
            KeyEvent.KEYCODE_BACK to KeyClass.BACK,
            KeyEvent.KEYCODE_HOME to KeyClass.HOME,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to KeyClass.MEDIA,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD to KeyClass.MEDIA,
            KeyEvent.KEYCODE_VOLUME_MUTE to KeyClass.VOLUME,
            KeyEvent.KEYCODE_A to KeyClass.OTHER,
            KeyEvent.KEYCODE_5 to KeyClass.OTHER,
            KeyEvent.KEYCODE_SEARCH to KeyClass.OTHER,
        ).forEach { (code, expected) -> assertEquals(expected, classifyKey(code), "key code $code") }
    }
}
