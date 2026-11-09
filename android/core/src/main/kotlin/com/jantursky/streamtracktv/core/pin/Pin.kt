package com.jantursky.streamtracktv.core.pin

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

public const val PIN_MIN_LENGTH: Int = 4
public const val PIN_MAX_LENGTH: Int = 8

/** A PIN is 4 to 8 digits and nothing else. */
public fun isValidPin(pin: String): Boolean = pin.length in PIN_MIN_LENGTH..PIN_MAX_LENGTH && pin.all { it in '0'..'9' }

/** Wrong tries in a row that are answered at once, before the first wait. */
public const val FREE_ATTEMPTS: Int = 3
private const val FIRST_WAIT_MS = 30_000L
private const val MAX_WAIT_MS = 15 * 60_000L

/**
 * How long entry is refused after [failures] wrong tries in a row: none for the first [FREE_ATTEMPTS],
 * then 30 seconds, doubling with each further wrong try, up to 15 minutes.
 */
public fun lockoutMs(failures: Int): Long {
    if (failures < FREE_ATTEMPTS) return 0
    val doublings = (failures - FREE_ATTEMPTS).coerceAtMost(MAX_DOUBLINGS)
    return (FIRST_WAIT_MS shl doublings).coerceAtMost(MAX_WAIT_MS)
}

private const val MAX_DOUBLINGS = 10

/** A PIN as stored: a random salt and the slow hash of the PIN with it. The PIN itself is never kept. */
public class PinHash(public val salt: ByteArray, public val hash: ByteArray, public val iterations: Int) {
    public fun matches(pin: String): Boolean = MessageDigest.isEqual(hash, derive(pin, salt, iterations))

    public companion object {
        public const val DEFAULT_ITERATIONS: Int = 120_000
        private const val SALT_BYTES = 16
        private const val KEY_BITS = 256

        public fun create(
            pin: String,
            iterations: Int = DEFAULT_ITERATIONS,
            random: SecureRandom = SecureRandom(),
        ): PinHash {
            val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
            return PinHash(salt, derive(pin, salt, iterations), iterations)
        }

        internal fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
            val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
            try {
                return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
        }
    }
}
