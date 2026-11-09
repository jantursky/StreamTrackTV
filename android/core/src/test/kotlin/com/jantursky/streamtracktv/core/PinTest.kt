package com.jantursky.streamtracktv.core

import com.jantursky.streamtracktv.core.pin.FREE_ATTEMPTS
import com.jantursky.streamtracktv.core.pin.PinHash
import com.jantursky.streamtracktv.core.pin.isValidPin
import com.jantursky.streamtracktv.core.pin.lockoutMs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PinTest {
    @Test
    fun `a PIN is 4 to 8 digits`() {
        assertTrue(isValidPin("1234"))
        assertTrue(isValidPin("12345678"))
        assertFalse(isValidPin("123"))
        assertFalse(isValidPin("123456789"))
        assertFalse(isValidPin("12a4"))
        assertFalse(isValidPin("12 34"))
        assertFalse(isValidPin(""))
    }

    @Test
    fun `the first tries are free, then the wait doubles up to a cap`() {
        assertEquals(0, lockoutMs(0))
        assertEquals(0, lockoutMs(FREE_ATTEMPTS - 1))
        assertEquals(30_000, lockoutMs(FREE_ATTEMPTS))
        assertEquals(60_000, lockoutMs(FREE_ATTEMPTS + 1))
        assertEquals(120_000, lockoutMs(FREE_ATTEMPTS + 2))
        assertEquals(15 * 60_000L, lockoutMs(FREE_ATTEMPTS + 20))
        assertEquals(15 * 60_000L, lockoutMs(Int.MAX_VALUE))
    }

    @Test
    fun `a hash matches its PIN only`() {
        val hash = PinHash.create("4821", iterations = 1_000)
        assertTrue(hash.matches("4821"))
        assertFalse(hash.matches("4822"))
        assertFalse(hash.matches(""))
    }

    @Test
    fun `salt is random and the hash does not contain the PIN`() {
        val a = PinHash.create("4821", iterations = 1_000)
        val b = PinHash.create("4821", iterations = 1_000)
        assertNotEquals(a.salt.toList(), b.salt.toList())
        assertNotEquals(a.hash.toList(), b.hash.toList())
        assertFalse(String(a.hash, Charsets.ISO_8859_1).contains("4821"))
        assertEquals(16, a.salt.size)
        assertEquals(32, a.hash.size)
    }

    @Test
    fun `the same salt and iterations give the same hash`() {
        val a = PinHash.create("4821", iterations = 1_000)
        val again = PinHash(a.salt, a.hash, a.iterations)
        assertContentEquals(a.hash, again.hash)
        assertTrue(again.matches("4821"))
    }
}
