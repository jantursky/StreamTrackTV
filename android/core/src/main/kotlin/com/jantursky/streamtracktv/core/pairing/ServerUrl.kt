package com.jantursky.streamtracktv.core.pairing

import java.net.URI
import java.net.URISyntaxException

/** Base URL of a StreamTrack TV server, without a trailing slash. */
@JvmInline
public value class ServerUrl private constructor(public val value: String) {
    public fun endpoint(path: String): String = "$value/api/v1/$path"

    /** Web page where the user confirms a pairing code. */
    public val pairPage: String get() = "$value/pair"

    /** Address the QR code points to: [pairPage] with the code filled in. */
    public fun pairLink(code: String): String = "$pairPage?code=$code"

    /** Web page where the owner confirms a PIN reset code, with the code filled in. */
    public fun pinResetLink(code: String): String = "$value/pin-reset?code=$code"

    override fun toString(): String = value

    public companion object {
        /**
         * Parses what the user typed. A missing scheme means `https://`. Plain `http://` is accepted only
         * when [allowHttp] is set, which debug builds use for a server on the local network.
         */
        public fun parse(input: String, allowHttp: Boolean): ServerUrl? {
            val trimmed = input.trim()
            if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
            val withScheme = (if ("://" in trimmed) trimmed else "https://$trimmed").trimEnd('/')
            val uri =
                try {
                    URI(withScheme)
                } catch (_: URISyntaxException) {
                    return null
                }
            val schemeOk = uri.scheme == "https" || (allowHttp && uri.scheme == "http")
            val shapeOk =
                !uri.host.isNullOrEmpty() && uri.rawQuery == null && uri.rawFragment == null && uri.userInfo == null
            return if (schemeOk && shapeOk) ServerUrl(withScheme) else null
        }
    }
}
