package com.jantursky.streamtracktv.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jantursky.streamtracktv.core.model.SecurityEventType
import com.jantursky.streamtracktv.core.security.SecurityRecorder
import com.jantursky.streamtracktv.data.pin.PinCheck
import com.jantursky.streamtracktv.data.pin.PinClock
import com.jantursky.streamtracktv.data.pin.PinStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class ListRecorder : SecurityRecorder {
    val events = mutableListOf<Pair<SecurityEventType, Int?>>()

    override fun record(type: SecurityEventType, value: Int?) {
        events += type to value
    }
}

private class FakeClock(var wall: Long = 1_000_000L, var elapsed: Long = 5_000L) : PinClock {
    override fun wallMs() = wall

    override fun elapsedMs() = elapsed

    fun advance(ms: Long) {
        wall += ms
        elapsed += ms
    }
}

class PinStoreTest {
    @get:Rule
    val folder = TemporaryFolder()
    private val clock = FakeClock()
    private lateinit var dataStore: DataStore<Preferences>
    private val recorder = ListRecorder()

    private fun TestScope.pinStore(): PinStore {
        if (!::dataStore.isInitialized) {
            dataStore =
                PreferenceDataStoreFactory.create(scope = backgroundScope) {
                    folder.newFile("pin.preferences_pb").also { it.delete() }
                }
        }
        return PinStore(dataStore, clock, iterations = 1_000, recorder = recorder)
    }

    @Test
    fun `no PIN until one is set, and only valid ones are accepted`() =
        runTest {
            val pin = pinStore()
            assertFalse(pin.isEnabled.first())
            assertEquals(PinCheck.NotSet, pin.verify("1234"))
            assertFalse(pin.setPin("12"))
            assertFalse(pin.setPin("abcd"))
            assertFalse(pin.isEnabled.first())
            assertTrue(pin.setPin("4821"))
            assertTrue(pin.isEnabled.first())
        }

    @Test
    fun `the right PIN unlocks and the wrong one does not`() =
        runTest {
            val pin = pinStore()
            pin.setPin("4821")
            assertEquals(PinCheck.Correct, pin.verify("4821"))
            assertEquals(PinCheck.Wrong(0), pin.verify("0000"))
        }

    @Test
    fun `the third wrong try in a row starts a wait that grows`() =
        runTest {
            val pin = pinStore()
            pin.setPin("4821")
            assertEquals(PinCheck.Wrong(0), pin.verify("1"))
            assertEquals(PinCheck.Wrong(0), pin.verify("2"))
            assertEquals(PinCheck.Wrong(30_000), pin.verify("3"))
            assertEquals(PinCheck.Locked(30_000), pin.verify("4821"))

            clock.advance(30_000)
            assertEquals(PinCheck.Wrong(60_000), pin.verify("0000"))
            clock.advance(20_000)
            assertEquals(PinCheck.Locked(40_000), pin.verify("4821"))
        }

    @Test
    fun `a correct PIN after the wait clears the count`() =
        runTest {
            val pin = pinStore()
            pin.setPin("4821")
            repeat(3) { pin.verify("0000") }
            clock.advance(30_000)
            assertEquals(PinCheck.Correct, pin.verify("4821"))
            assertEquals(PinCheck.Wrong(0), pin.verify("0000"))
        }

    @Test
    fun `the wait survives a restart of the app`() =
        runTest {
            val first = pinStore()
            first.setPin("4821")
            repeat(3) { first.verify("0000") }
            clock.advance(10_000)
            val restarted = PinStore(dataStore, clock, iterations = 1_000)
            assertEquals(20_000, restarted.remainingWaitMs())
            assertEquals(PinCheck.Locked(20_000), restarted.verify("4821"))
        }

    @Test
    fun `moving the wall clock back does not shorten the wait`() =
        runTest {
            val pin = pinStore()
            pin.setPin("4821")
            repeat(3) { pin.verify("0000") }
            clock.wall -= 3_600_000
            clock.elapsed += 5_000
            assertEquals(25_000, pin.remainingWaitMs())
        }

    @Test
    fun `after a reboot the wall clock decides`() =
        runTest {
            val pin = pinStore()
            pin.setPin("4821")
            repeat(3) { pin.verify("0000") }
            clock.wall += 12_000
            clock.elapsed = 100
            assertEquals(18_000, pin.remainingWaitMs())
        }

    @Test
    fun `change needs the current PIN and a valid new one`() =
        runTest {
            val pin = pinStore()
            pin.setPin("4821")
            assertEquals(PinCheck.Wrong(0), pin.change("0000", "5555"))
            assertEquals(PinCheck.Wrong(0), pin.change("4821", "12"))
            assertEquals(PinCheck.Correct, pin.change("4821", "5555"))
            assertEquals(PinCheck.Wrong(0), pin.verify("4821"))
            assertEquals(PinCheck.Correct, pin.verify("5555"))
        }

    @Test
    fun `disable needs the current PIN`() =
        runTest {
            val pin = pinStore()
            pin.setPin("4821")
            assertEquals(PinCheck.Wrong(0), pin.disable("0000"))
            assertTrue(pin.isEnabled.first())
            assertEquals(PinCheck.Correct, pin.disable("4821"))
            assertFalse(pin.isEnabled.first())
        }

    @Test
    fun `clear removes the PIN and the wait`() =
        runTest {
            val pin = pinStore()
            pin.setPin("4821")
            repeat(3) { pin.verify("0000") }
            pin.clear()
            assertFalse(pin.isEnabled.first())
            assertEquals(0, pin.remainingWaitMs())
        }

    @Test
    fun `stored data holds a salt and a hash and not the PIN`() =
        runTest {
            val pin = pinStore()
            pin.setPin("4821")
            val prefs =
                dataStore.data
                    .first()
                    .asMap()
                    .mapKeys { it.key.name }
            assertTrue("pin_salt" in prefs && "pin_hash" in prefs)
            assertTrue(prefs.values.none { it.toString().contains("4821") })
            assertEquals(null, dataStore.data.first()[stringPreferencesKey("pin")])
        }

    @Test
    fun `wrong tries and the wait are recorded as counts and seconds, a right PIN and a locked try are not`() =
        runTest {
            val pin = pinStore()
            pin.setPin("4821")
            pin.verify("4821")
            pin.verify("0001")
            pin.verify("0002")
            pin.verify("0003")
            pin.verify("4821")
            assertEquals(
                listOf<Pair<SecurityEventType, Int?>>(
                    SecurityEventType.PIN_WRONG to 1,
                    SecurityEventType.PIN_WRONG to 2,
                    SecurityEventType.PIN_WRONG to 3,
                    SecurityEventType.PIN_WAIT_STARTED to 30,
                ),
                recorder.events,
            )
            clock.advance(30_000)
            pin.verify("0004")
            assertEquals(
                listOf<Pair<SecurityEventType, Int?>>(SecurityEventType.PIN_WRONG to 4, SecurityEventType.PIN_WAIT_STARTED to 60),
                recorder.events.takeLast(2),
            )
        }
}
