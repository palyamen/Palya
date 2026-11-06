package com.astra.netspeed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("net_speed", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", false) || !Settings.canDrawOverlays(context)) return

        val serviceIntent = Intent(context, SpeedService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (_: Exception) {
            // Some Android builds restrict automatic foreground-service starts after boot.
        }
    }
}
