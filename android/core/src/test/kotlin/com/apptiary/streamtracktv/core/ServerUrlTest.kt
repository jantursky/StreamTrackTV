package com.apptiary.streamtracktv.core

import com.apptiary.streamtracktv.core.pairing.ServerUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerUrlTest {
    @Test
    fun `adds https and drops trailing slashes`() {
        assertEquals("https://stats.example.com", ServerUrl.parse("  stats.example.com/ ", allowHttp = false)?.value)
        assertEquals("https://example.com/tv", ServerUrl.parse("https://example.com/tv//", allowHttp = false)?.value)
    }

    @Test
    fun `allows plain http only when asked`() {
        assertNull(ServerUrl.parse("http://192.168.1.5:3000", allowHttp = false))
        assertEquals("http://192.168.1.5:3000", ServerUrl.parse("http://192.168.1.5:3000", allowHttp = true)?.value)
    }

    @Test
    fun `rejects input that is not a server address`() {
        listOf(
            "",
            "   ",
            "ftp://example.com",
            "https://",
            "exa mple.com",
            "https://example.com?x=1",
            "https://user@example.com",
            "https://exa^mple.com",
        ).forEach { assertNull(ServerUrl.parse(it, allowHttp = true), it) }
    }

    @Test
    fun `builds endpoints and the pairing link`() {
        val url = ServerUrl.parse("example.com", allowHttp = false)!!
        assertEquals("https://example.com/api/v1/sync", url.endpoint("sync"))
        assertEquals("https://example.com/pair?code=K7M2QX", url.pairLink("K7M2QX"))
        assertEquals("https://example.com/pair", url.pairPage)
        assertEquals("https://example.com", url.toString())
    }
}
