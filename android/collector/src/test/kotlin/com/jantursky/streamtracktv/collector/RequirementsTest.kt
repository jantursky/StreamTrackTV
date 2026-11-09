package com.jantursky.streamtracktv.collector

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RequirementsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun setUsageAccess(mode: Int) =
        shadowOf(context.getSystemService(AppOpsManager::class.java))
            .setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName, mode)

    @Test
    fun `reports missing requirements with adb fallbacks when the TV lacks the settings screens`() {
        setUsageAccess(AppOpsManager.MODE_IGNORED)
        val statuses = AndroidPermissions(context).statuses()
        assertEquals(Requirement.entries, statuses.map { it.requirement })
        assertTrue(statuses.none { it.granted })
        statuses.forEach { assertNull(it.settingsIntent) }
        assertEquals(
            "adb shell appops set ${context.packageName} GET_USAGE_STATS allow",
            statuses.first { it.requirement == Requirement.USAGE_ACCESS }.adbCommand,
        )
    }

    @Test
    fun `sees every requirement once granted`() {
        setUsageAccess(AppOpsManager.MODE_ALLOWED)
        val component = ComponentName(context, KeyPressService::class.java).flattenToString()
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "other/.Service:$component",
        )
        shadowOf(
            context.getSystemService(PowerManager::class.java),
        ).setIgnoringBatteryOptimizations(context.packageName, true)
        val granted = AndroidPermissions(context).statuses().filter { it.granted }.map { it.requirement }
        assertEquals(Requirement.entries, granted)
    }

    @Test
    fun `adb commands name the service and the package`() {
        assertEquals(
            "adb shell settings put secure enabled_accessibility_services " +
                "com.jantursky.streamtracktv/.collector.KeyPressService" +
                " && adb shell settings put secure accessibility_enabled 1",
            adbCommand(Requirement.ACCESSIBILITY, "com.jantursky.streamtracktv"),
        )
        assertEquals(
            "adb shell dumpsys deviceidle whitelist +tv.app",
            adbCommand(Requirement.BATTERY_OPTIMIZATION, "tv.app"),
        )
    }
}
