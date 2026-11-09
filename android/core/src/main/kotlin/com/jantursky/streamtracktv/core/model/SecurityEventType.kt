package com.jantursky.streamtracktv.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the TV records at its lock screen and in its PIN settings, mirrors `SECURITY_EVENT_TYPES` in
 * `packages/schema`. An event is only its type, its time and one small number: never anything typed.
 */
@Serializable
public enum class SecurityEventType(public val wireName: String) {
    @SerialName("app_opened")
    APP_OPENED("app_opened"),

    @SerialName("lock_shown")
    LOCK_SHOWN("lock_shown"),

    /** `value` is the number of wrong tries so far. */
    @SerialName("pin_wrong")
    PIN_WRONG("pin_wrong"),

    /** `value` is the wait in seconds. */
    @SerialName("pin_wait_started")
    PIN_WAIT_STARTED("pin_wait_started"),

    @SerialName("unlocked")
    UNLOCKED("unlocked"),

    @SerialName("forgot_pin_opened")
    FORGOT_PIN_OPENED("forgot_pin_opened"),

    @SerialName("pin_reset_confirmed")
    PIN_RESET_CONFIRMED("pin_reset_confirmed"),

    @SerialName("pin_enabled")
    PIN_ENABLED("pin_enabled"),

    @SerialName("pin_changed")
    PIN_CHANGED("pin_changed"),

    @SerialName("pin_disabled")
    PIN_DISABLED("pin_disabled"),
    ;

    public companion object {
        public fun fromWireName(name: String): SecurityEventType? = entries.firstOrNull { it.wireName == name }
    }
}
