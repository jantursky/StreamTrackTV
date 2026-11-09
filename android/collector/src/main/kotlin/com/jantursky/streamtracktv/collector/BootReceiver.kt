package com.jantursky.streamtracktv.collector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Puts background work back in place after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            (context.applicationContext as? CollectorHost)?.onBootCompleted()
        }
    }
}
