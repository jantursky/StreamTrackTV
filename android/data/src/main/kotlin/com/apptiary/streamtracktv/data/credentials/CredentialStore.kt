package com.apptiary.streamtracktv.data.credentials

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.GeneralSecurityException
import java.util.Base64

/** Encrypts small secrets before they touch disk. */
interface SecretCipher {
    fun encrypt(plain: ByteArray): ByteArray

    /** Throws [GeneralSecurityException] when the data cannot be decrypted, e.g. after the key was lost. */
    fun decrypt(sealed: ByteArray): ByteArray
}

/**
 * The device token, encrypted with a key that never leaves the Android Keystore. A token that no
 * longer decrypts (backup restored onto another device) counts as no token, so the TV pairs again.
 */
class CredentialStore(private val store: DataStore<Preferences>, private val cipher: SecretCipher) {
    val isPaired: Flow<Boolean> = store.data.map { it[TOKEN] != null }

    suspend fun token(): String? {
        val sealed = store.data.first()[TOKEN] ?: return null
        return try {
            cipher.decrypt(Base64.getDecoder().decode(sealed)).decodeToString()
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    suspend fun save(token: String) {
        val sealed = Base64.getEncoder().encodeToString(cipher.encrypt(token.encodeToByteArray()))
        store.edit { it[TOKEN] = sealed }
    }

    suspend fun clear() {
        store.edit { it.remove(TOKEN) }
    }

    private companion object {
        val TOKEN = stringPreferencesKey("device_token")
    }
}
