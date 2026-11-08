package com.apptiary.streamtracktv.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Class of a remote key press. The app records only this class, never the key code, so the server
 * cannot reconstruct what was typed.
 */
@Serializable
public enum class KeyClass(public val wireName: String) {
    @SerialName("dpad")
    DPAD("dpad"),

    @SerialName("select")
    SELECT("select"),

    @SerialName("back")
    BACK("back"),

    @SerialName("home")
    HOME("home"),

    @SerialName("media")
    MEDIA("media"),

    @SerialName("volume")
    VOLUME("volume"),

    @SerialName("other")
    OTHER("other"),
    ;

    public companion object {
        public fun fromWireName(name: String): KeyClass? = entries.firstOrNull { it.wireName == name }
    }
}
