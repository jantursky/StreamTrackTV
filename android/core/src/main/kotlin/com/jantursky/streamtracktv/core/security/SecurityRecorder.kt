package com.jantursky.streamtracktv.core.security

import com.jantursky.streamtracktv.core.model.SecurityEventType

/**
 * Where the lock screen and the PIN settings report what happens, for the security log. Recording must
 * never throw and never wait: the screens call it on their way to doing something else.
 */
public interface SecurityRecorder {
    /** [value] is the wrong tries so far (`PIN_WRONG`) or the wait in seconds (`PIN_WAIT_STARTED`). */
    public fun record(type: SecurityEventType, value: Int? = null)

    /** Records nothing, for code that has no log. */
    public companion object None : SecurityRecorder {
        override fun record(type: SecurityEventType, value: Int?) {
            // nothing to record
        }
    }
}
