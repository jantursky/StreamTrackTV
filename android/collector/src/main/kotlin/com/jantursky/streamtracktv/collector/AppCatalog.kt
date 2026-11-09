package com.jantursky.streamtracktv.collector

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/** Display names of installed apps. */
fun interface AppCatalog {
    fun label(packageName: String): String?
}

/** Labels and icons from the package manager; visibility comes from the `queries` in the manifest. */
class PackageManagerAppCatalog(context: Context) : AppCatalog {
    private val packageManager = context.packageManager

    override fun label(packageName: String): String? =
        try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

    /** TV banner when the app has one, else its launcher icon; null when the app is gone. */
    fun icon(packageName: String): Drawable? =
        try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            info.loadBanner(packageManager) ?: info.loadIcon(packageManager)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

    /** Apps a user can open on this TV, for the exclusion and limit lists. */
    fun launchablePackages(): Set<String> {
        val leanback =
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val launcher =
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
        return listOf(leanback, launcher)
            .flatMap { packageManager.queryIntentActivities(it, 0) }
            .map { it.activityInfo.packageName }
            .toSet()
    }
}
