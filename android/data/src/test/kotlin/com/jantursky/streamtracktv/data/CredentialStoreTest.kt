package com.jantursky.streamtracktv.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.jantursky.streamtracktv.data.credentials.CredentialStore
import com.jantursky.streamtracktv.data.credentials.SecretCipher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.GeneralSecurityException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reversible stand-in for the Keystore cipher, which Robolectric cannot provide. */
private class XorCipher(private val broken: Boolean = false) : SecretCipher {
    override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()

    override fun decrypt(sealed: ByteArray): ByteArray {
        if (broken) throw GeneralSecurityException("Key lost")
        return encrypt(sealed)
    }
}

class CredentialStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.store(file: File, cipher: SecretCipher = XorCipher()) =
        CredentialStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { file }, cipher)

    private fun prefsFile() = File(folder.root, "credentials.preferences_pb")

    @Test
    fun `saves, encrypts and clears the token`() =
        runTest {
            val file = prefsFile()
            val store = store(file)
            assertNull(store.token())
            assertFalse(store.isPaired.first())
            store.save("t".repeat(64))
            assertEquals("t".repeat(64), store.token())
            assertTrue(store.isPaired.first())
            assertFalse(file.readText(Charsets.ISO_8859_1).contains("tttt"), "Token must not be stored in plain text")
            store.clear()
            assertNull(store.token())
        }

    @Test
    fun `a token that no longer decrypts counts as missing`() =
        runTest {
            val store = store(prefsFile(), XorCipher(broken = true))
            store.save("secret-token")
            assertNull(store.token())
            assertTrue(store.isPaired.first())
        }
}
