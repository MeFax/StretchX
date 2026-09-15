package com.stretchx.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.i("BootReceiver", "Device boot detected. Ensuring S25 Ultra native resolution is active.")
            if (ShizukuManager.isAvailable()) {
                DisplayOptimizer.resetToNative()
            }
        }
    }
}
