package com.apptiary.streamtracktv.collector

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.os.Process
import android.provider.Settings

/** What the app needs from the user before it can collect anything. */
enum class Requirement { USAGE_ACCESS, ACCESSIBILITY, BATTERY_OPTIMIZATION }

/**
 * State of one requirement. [settingsIntent] is null when this TV has no screen for it; the
 * checklist then shows [adbCommand] instead.
 */
data class RequirementStatus(
    val requirement: Requirement,
    val granted: Boolean,
    val settingsIntent: Intent?,
    val adbCommand: String,
)

/** adb command that grants [requirement] to [packageName] from a computer. */
fun adbCommand(requirement: Requirement, packageName: String): String =
    when (requirement) {
        Requirement.USAGE_ACCESS -> {
            "adb shell appops set $packageName GET_USAGE_STATS allow"
        }

        Requirement.ACCESSIBILITY -> {
            "adb shell settings put secure enabled_accessibility_services $packageName/${KeyPressService::class.java.name}"
        }

        Requirement.BATTERY_OPTIMIZATION -> {
            "adb shell dumpsys deviceidle whitelist +$packageName"
        }
    }

/** Reads the current state of every [Requirement] from the system. */
class AndroidPermissions(private val context: Context) {
    private val packageName = context.packageName

    fun statuses(): List<RequirementStatus> =
        Requirement.entries.map { requirement ->
            RequirementStatus(
                requirement = requirement,
                granted = isGranted(requirement),
                settingsIntent = settingsIntent(requirement),
                adbCommand = adbCommand(requirement, packageName),
            )
        }

    private fun isGranted(requirement: Requirement): Boolean =
        when (requirement) {
            Requirement.USAGE_ACCESS -> {
                hasUsageAccess()
            }

            Requirement.ACCESSIBILITY -> {
                isServiceEnabled()
            }

            Requirement.BATTERY_OPTIMIZATION -> {
                context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) == true
            }
        }

    private fun settingsIntent(requirement: Requirement): Intent? {
        val action =
            when (requirement) {
                Requirement.USAGE_ACCESS -> Settings.ACTION_USAGE_ACCESS_SETTINGS
                Requirement.ACCESSIBILITY -> Settings.ACTION_ACCESSIBILITY_SETTINGS
                Requirement.BATTERY_OPTIMIZATION -> Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            }
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent.takeIf { context.packageManager.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY) != null }
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    private fun isServiceEnabled(): Boolean {
        val own = ComponentName(context, KeyPressService::class.java)
        val enabled =
            Settings.Secure
                .getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                ).orEmpty()
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == own }
    }
}
