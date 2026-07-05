package com.example.dynamicisland.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.dynamicisland.service.DynamicIslandService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences(DynamicIslandService.PREFS_NAME, Context.MODE_PRIVATE)
        val alwaysOn     = prefs.getBoolean(DynamicIslandService.PREF_ALWAYS_ON, false)
        val wasRunning   = prefs.getBoolean("service_running", false)
        val canOverlay   = android.provider.Settings.canDrawOverlays(context)
        if ((alwaysOn || wasRunning) && canOverlay) {
            context.startForegroundService(
                Intent(context, DynamicIslandService::class.java).apply {
                    action = DynamicIslandService.ACTION_START
                }
            )
        }
    }
}
