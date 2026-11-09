package com.jantursky.streamtracktv.data.pin

import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jantursky.streamtracktv.core.model.SecurityEventType
import com.jantursky.streamtracktv.core.pin.PinHash
import com.jantursky.streamtracktv.core.pin.isValidPin
import com.jantursky.streamtracktv.core.pin.lockoutMs
import com.jantursky.streamtracktv.core.security.SecurityRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Base64

/** Time as the lock sees it: wall clock, and the monotonic clock that a clock change cannot move. */
interface PinClock {
    fun wallMs(): Long

    fun elapsedMs(): Long
}

object SystemPinClock : PinClock {
    override fun wallMs(): Long = System.currentTimeMillis()

    override fun elapsedMs(): Long = SystemClock.elapsedRealtime()
}

/** Answer to an attempt to unlock. */
sealed interface PinCheck {
    data object Correct : PinCheck

    /** Wrong PIN; [waitMs] is the wait that now applies, 0 while free tries are left. */
    data class Wrong(val waitMs: Long) : PinCheck

    /** Entry is refused for [remainingMs] more. The PIN was not looked at. */
    data class Locked(val remainingMs: Long) : PinCheck

    /** No PIN is set. */
    data object NotSet : PinCheck
}

/**
 * The PIN lock's storage: the salted hash, and the wrong-try count with the time it last waited, so a
 * restart of the app does not clear a wait. Only the hash is stored, never the PIN.
 */
class PinStore(
    private val store: DataStore<Preferences>,
    private val clock: PinClock = SystemPinClock,
    private val iterations: Int = PinHash.DEFAULT_ITERATIONS,
    private val recorder: SecurityRecorder = SecurityRecorder.None,
) {
    val isEnabled: Flow<Boolean> = store.data.map { it[HASH] != null }

    /** Sets or replaces the PIN, clearing any wait. Returns false when [pin] is not 4 to 8 digits. */
    suspend fun setPin(pin: String): Boolean {
        if (!isValidPin(pin)) return false
        val hash = withContext(Dispatchers.Default) { PinHash.create(pin, iterations) }
        store.edit { prefs ->
            prefs[SALT] = encode(hash.salt)
            prefs[HASH] = encode(hash.hash)
            prefs[ITERATIONS] = hash.iterations
            clearAttempts(prefs)
        }
        return true
    }

    /** Remaining wait in milliseconds, 0 when entry is allowed. */
    suspend fun remainingWaitMs(): Long = store.data.first().remainingWait()

    suspend fun verify(pin: String): PinCheck {
        val prefs = store.data.first()
        val stored = prefs.toHash()
        val remaining = prefs.remainingWait()
        return when {
            stored == null -> {
                PinCheck.NotSet
            }

            remaining > 0 -> {
                PinCheck.Locked(remaining)
            }

            withContext(Dispatchers.Default) { stored.matches(pin) } -> {
                store.edit { clearAttempts(it) }
                PinCheck.Correct
            }

            else -> {
                PinCheck.Wrong(recordFailure())
            }
        }
    }

    /**
     * Counts one more wrong try and, from the third on, starts the wait. Returns that wait, and tells the
     * security log about the try and the wait (the count and the seconds, never what was typed).
     */
    private suspend fun recordFailure(): Long {
        var wait = 0L
        var count = 0
        store.edit { edit ->
            val failures = (edit[FAILURES] ?: 0) + 1
            count = failures
            wait = lockoutMs(failures)
            edit[FAILURES] = failures
            if (wait > 0) {
                edit[LOCK_MS] = wait
                edit[LOCKED_AT_WALL] = clock.wallMs()
                edit[LOCKED_AT_ELAPSED] = clock.elapsedMs()
            }
        }
        recorder.record(SecurityEventType.PIN_WRONG, count)
        if (wait > 0) recorder.record(SecurityEventType.PIN_WAIT_STARTED, (wait / MS_PER_SECOND).toInt())
        return wait
    }

    /** Replaces the PIN when [current] is right. */
    suspend fun change(current: String, new: String): PinCheck {
        if (!isValidPin(new)) return PinCheck.Wrong(0)
        val check = verify(current)
        if (check is PinCheck.Correct) setPin(new)
        return check
    }

    /** Turns the lock off when [current] is right. */
    suspend fun disable(current: String): PinCheck {
        val check = verify(current)
        if (check is PinCheck.Correct) clear()
        return check
    }

    /** Removes the PIN and any wait, without asking: used after a confirmed reset and by unpairing. */
    suspend fun clear() =
        store.edit { prefs ->
            prefs.remove(SALT)
            prefs.remove(HASH)
            prefs.remove(ITERATIONS)
            clearAttempts(prefs)
        }

    private fun Preferences.toHash(): PinHash? {
        val salt = this[SALT]?.let(::decode) ?: return null
        val hash = this[HASH]?.let(::decode) ?: return null
        return PinHash(salt, hash, this[ITERATIONS] ?: PinHash.DEFAULT_ITERATIONS)
    }

    /**
     * The longer of what the wall clock and the monotonic clock say is left. Moving the wall clock back does not
     * shorten the wait, and a reboot (the monotonic clock restarts) falls back to the wall clock.
     */
    private fun Preferences.remainingWait(): Long {
        val total = this[LOCK_MS]
        val atWall = this[LOCKED_AT_WALL]
        val atElapsed = this[LOCKED_AT_ELAPSED]
        if (total == null || atWall == null || atElapsed == null) return 0
        val nowWall = clock.wallMs()
        val nowElapsed = clock.elapsedMs()
        // A clock set back must not stretch the wait, nor one set forward shorten it: the monotonic clock
        // leads while it is valid, and the wall clock covers a reboot.
        val elapsedLeft = if (nowElapsed >= atElapsed) total - (nowElapsed - atElapsed) else null
        val wallLeft = if (nowWall >= atWall) total - (nowWall - atWall) else null
        val left = if (elapsedLeft != null) maxOf(elapsedLeft, wallLeft ?: elapsedLeft) else wallLeft ?: total
        return left.coerceIn(0, total)
    }

    private fun clearAttempts(prefs: androidx.datastore.preferences.core.MutablePreferences) {
        prefs.remove(FAILURES)
        prefs.remove(LOCK_MS)
        prefs.remove(LOCKED_AT_WALL)
        prefs.remove(LOCKED_AT_ELAPSED)
    }

    private companion object {
        const val MS_PER_SECOND = 1_000L
        val SALT = stringPreferencesKey("pin_salt")
        val HASH = stringPreferencesKey("pin_hash")
        val ITERATIONS = intPreferencesKey("pin_iterations")
        val FAILURES = intPreferencesKey("pin_failures")
        val LOCK_MS = longPreferencesKey("pin_lock_ms")
        val LOCKED_AT_WALL = longPreferencesKey("pin_locked_at_wall")
        val LOCKED_AT_ELAPSED = longPreferencesKey("pin_locked_at_elapsed")

        fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

        fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)
    }
}
