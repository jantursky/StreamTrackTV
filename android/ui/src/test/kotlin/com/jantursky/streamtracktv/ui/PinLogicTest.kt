package com.jantursky.streamtracktv.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.jantursky.streamtracktv.core.model.SecurityEventType
import com.jantursky.streamtracktv.core.security.SecurityRecorder
import com.jantursky.streamtracktv.data.pin.PinClock
import com.jantursky.streamtracktv.data.pin.PinStore
import com.jantursky.streamtracktv.ui.lock.LockSession
import com.jantursky.streamtracktv.ui.lock.LockViewModel
import com.jantursky.streamtracktv.ui.lock.PinFlowError
import com.jantursky.streamtracktv.ui.lock.PinFlowMode
import com.jantursky.streamtracktv.ui.lock.PinFlowViewModel
import com.jantursky.streamtracktv.ui.lock.PinStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class ListRecorder : SecurityRecorder {
    val events = mutableListOf<Pair<SecurityEventType, Int?>>()

    override fun record(type: SecurityEventType, value: Int?) {
        events += type to value
    }
}

private class TestClock(var ms: Long = 1_000_000L) : PinClock {
    override fun wallMs() = ms

    override fun elapsedMs() = ms - 900_000L
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PinLogicTest {
    @get:Rule
    val folder = TemporaryFolder()
    private val clock = TestClock()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.pin(): PinStore =
        PinStore(
            PreferenceDataStoreFactory.create(scope = backgroundScope) {
                folder.newFile("pin.preferences_pb").also { it.delete() }
            },
            clock,
            iterations = 1_000,
        )

    /** Waits in real time for [check]; the PIN store works on real background threads. */
    private suspend fun eventually(check: () -> Boolean) =
        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                while (!check()) delay(5)
            }
        }

    private fun type(digits: String, press: (Char) -> Unit) = digits.forEach(press)

    // Lock session

    @Test
    fun `the app starts locked only when a PIN is set, and locks again when it leaves the screen`() =
        runTest(UnconfinedTestDispatcher()) {
            val pin = pin()
            val session = LockSession(pin, backgroundScope)
            assertFalse(session.locked.first { it != null }!!)

            pin.setPin("4821")
            eventually { session.locked.value == true }
            session.unlock()
            eventually { session.locked.value == false }

            session.lock()
            eventually { session.locked.value == true }

            pin.clear()
            eventually { session.locked.value == false }
        }

    @Test
    fun `the lock is unknown until the setting has been read`() =
        runTest(StandardTestDispatcher()) {
            val session = LockSession(pin(), backgroundScope)
            assertNull(session.locked.value)
            runCurrent()
            eventually { session.locked.value == false }
        }

    // Lock screen

    @Test
    fun `a correct PIN opens the lock`() =
        runTest(UnconfinedTestDispatcher()) {
            val pin = pin().also { it.setPin("4821") }
            val session = LockSession(pin, backgroundScope)
            val vm = LockViewModel(pin, session)
            type("4821", vm::onDigit)
            assertEquals("4821", vm.state.value.entered)
            vm.submit()
            eventually { session.locked.value == false }
            assertEquals("", vm.state.value.entered)
        }

    @Test
    fun `a wrong PIN clears the entry and says so, and digits stop at 8`() =
        runTest(UnconfinedTestDispatcher()) {
            val pin = pin().also { it.setPin("4821") }
            val session = LockSession(pin, backgroundScope)
            val vm = LockViewModel(pin, session)
            type("123456789", vm::onDigit)
            assertEquals("12345678", vm.state.value.entered)
            vm.onDelete()
            assertEquals("1234567", vm.state.value.entered)
            vm.submit()
            assertTrue(vm.state.value.wrong)
            assertEquals("", vm.state.value.entered)
            assertEquals(true, session.locked.value)
        }

    @Test
    fun `the third wrong PIN starts a wait that blocks entry`() =
        runTest(UnconfinedTestDispatcher()) {
            val pin = pin().also { it.setPin("4821") }
            val vm = LockViewModel(pin, LockSession(pin, backgroundScope))
            repeat(3) {
                type("0000", vm::onDigit)
                vm.submit()
            }
            assertEquals(30_000, vm.state.value.waitMs)
            vm.onDigit('1')
            assertEquals("", vm.state.value.entered)
        }

    @Test
    fun `a wait left from before is shown when the screen opens`() =
        runTest(UnconfinedTestDispatcher()) {
            val pin = pin().also { it.setPin("4821") }
            repeat(3) { pin.verify("0000") }
            clock.ms += 10_000
            val vm = LockViewModel(pin, LockSession(pin, backgroundScope))
            eventually { vm.state.value.waitMs > 0 }
            assertEquals(20_000, vm.state.value.waitMs)
        }

    @Test
    fun `the wait counts down and entry opens again`() =
        runTest(UnconfinedTestDispatcher()) {
            val pin = pin().also { it.setPin("4821") }
            val vm = LockViewModel(pin, LockSession(pin, backgroundScope))
            repeat(3) {
                type("0000", vm::onDigit)
                vm.submit()
            }
            clock.ms += 31_000
            advanceTimeBy(2_000)
            eventually { vm.state.value.waitMs == 0L }
            assertEquals(0, vm.state.value.waitMs)
            type("4", vm::onDigit)
            assertEquals("4", vm.state.value.entered)
        }

    // Settings flow

    private fun TestScope.flow(mode: PinFlowMode, pin: PinStore) =
        PinFlowViewModel(mode, pin, LockSession(pin, backgroundScope))

    @Test
    fun `turning the PIN on asks twice and then locks nothing out`() =
        runTest(UnconfinedTestDispatcher()) {
            val pin = pin()
            val vm = flow(PinFlowMode.SET, pin)
            assertEquals(PinStep.NEW, vm.state.value.step)
            type("5555", vm::onDigit)
            vm.submit()
            assertEquals(PinStep.REPEAT, vm.state.value.step)
            type("5555", vm::onDigit)
            vm.submit()
            assertTrue(vm.state.value.done)
            assertTrue(pin.isEnabled.first())
        }

    @Test
    fun `a short PIN or a different repeat is refused and nothing is saved`() =
        runTest(UnconfinedTestDispatcher()) {
            val pin = pin()
            val vm = flow(PinFlowMode.SET, pin)
            type("12", vm::onDigit)
            vm.submit()
            assertEquals(PinFlowError.TOO_SHORT, vm.state.value.error)
            assertEquals(PinStep.NEW, vm.state.value.step)

            type("5555", vm::onDigit)
            vm.submit()
            type("5556", vm::onDigit)
            vm.submit()
            assertEquals(PinFlowError.MISMATCH, vm.state.value.error)
            assertEquals(PinStep.NEW, vm.state.value.step)
            assertFalse(pin.isEnabled.first())
        }

    @Test
    fun `changing the PIN needs the current one first`() =
        runTest(UnconfinedTestDispatcher()) {
            val pin = pin().also { it.setPin("4821") }
            val vm = flow(PinFlowMode.CHANGE, pin)
            assertEquals(PinStep.CURRENT, vm.state.value.step)
            type("0000", vm::onDigit)
            vm.submit()
            assertEquals(PinFlowError.WRONG, vm.state.value.error)
            assertEquals(PinStep.CURRENT, vm.state.value.step)

            type("4821", vm::onDigit)
            vm.submit()
            assertEquals(PinStep.NEW, vm.state.value.step)
            type("7777", vm::onDigit)
            vm.submit()
            type("7777", vm::onDigit)
            vm.submit()
            assertTrue(vm.state.value.done)
            assertEquals(com.jantursky.streamtracktv.data.pin.PinCheck.Correct, pin.verify("7777"))
        }

    @Test
    fun `turning the PIN off needs the current one`() =
        runTest(UnconfinedTestDispatcher()) {
            val pin = pin().also { it.setPin("4821") }
            val vm = flow(PinFlowMode.DISABLE, pin)
            type("0000", vm::onDigit)
            vm.submit()
            assertEquals(PinFlowError.WRONG, vm.state.value.error)
            assertTrue(pin.isEnabled.first())

            type("4821", vm::onDigit)
            vm.submit()
            assertTrue(vm.state.value.done)
            assertFalse(pin.isEnabled.first())
        }

    @Test
    fun `changing the PIN is refused while a wait runs`() =
        runTest(UnconfinedTestDispatcher()) {
            val pin = pin().also { it.setPin("4821") }
            repeat(3) { pin.verify("0000") }
            val vm = flow(PinFlowMode.CHANGE, pin)
            type("4821", vm::onDigit)
            vm.submit()
            assertEquals(PinFlowError.WAIT, vm.state.value.error)
            assertEquals(30_000, vm.state.value.waitMs)
        }

    // Security log

    @Test
    fun `the app is recorded as opened once per process`() =
        runTest(UnconfinedTestDispatcher()) {
            val recorder = ListRecorder()
            val session = LockSession(pin(), backgroundScope, recorder)
            session.appOpened()
            session.lock()
            session.appOpened()
            assertEquals(listOf<Pair<SecurityEventType, Int?>>(SecurityEventType.APP_OPENED to null), recorder.events)
        }

    @Test
    fun `unlocking from the lock screen is recorded, a wrong PIN is left to the store`() =
        runTest(UnconfinedTestDispatcher()) {
            val recorder = ListRecorder()
            val pin = pin().also { it.setPin("4821") }
            val vm = LockViewModel(pin, LockSession(pin, backgroundScope), recorder)
            type("0000", vm::onDigit)
            vm.submit()
            assertTrue(recorder.events.isEmpty())
            type("4821", vm::onDigit)
            vm.submit()
            assertEquals(listOf<Pair<SecurityEventType, Int?>>(SecurityEventType.UNLOCKED to null), recorder.events)
        }

    @Test
    fun `turning the PIN on, changing it and turning it off are recorded, with no digits`() =
        runTest(UnconfinedTestDispatcher()) {
            val recorder = ListRecorder()
            val pin = pin()

            fun flow(mode: PinFlowMode) = PinFlowViewModel(mode, pin, LockSession(pin, backgroundScope), recorder)

            val set = flow(PinFlowMode.SET)
            type("4821", set::onDigit)
            set.submit()
            type("4821", set::onDigit)
            set.submit()
            val change = flow(PinFlowMode.CHANGE)
            type("4821", change::onDigit)
            change.submit()
            type("9999", change::onDigit)
            change.submit()
            type("9999", change::onDigit)
            change.submit()
            val off = flow(PinFlowMode.DISABLE)
            type("9999", off::onDigit)
            off.submit()
            assertEquals(
                listOf<Pair<SecurityEventType, Int?>>(
                    SecurityEventType.PIN_ENABLED to null,
                    SecurityEventType.PIN_CHANGED to null,
                    SecurityEventType.PIN_DISABLED to null,
                ),
                recorder.events,
            )
        }
}
