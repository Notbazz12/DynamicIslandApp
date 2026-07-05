package com.example.dynamicisland.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.example.dynamicisland.state.IslandEventBus
import com.example.dynamicisland.state.IslandState

class IslandAccessibilityService : AccessibilityService() {

    companion object {
        // Apps de redes sociales que activan el modo mini
        private val SOCIAL_MEDIA_PACKAGES = setOf(
            "com.instagram.android",
            "com.zhiliaoapp.musically",   // TikTok
            "com.ss.android.ugc.trill",   // TikTok alternativo
            "com.twitter.android",
            "com.x.android",
            "com.facebook.katana",
            "com.facebook.lite",
            "com.snapchat.android",
            "com.reddit.frontpage",
            "com.pinterest",
            "com.linkedin.android",
            "com.google.android.youtube",
        )

        // Acceso estático para que DynamicIslandService pueda consultarlo
        @Volatile var isSocialMediaForeground: Boolean = false
            private set
        @Volatile var isGamingForeground: Boolean = false
            private set
        @Volatile var foregroundPackage: String = ""
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            if (pkg == foregroundPackage) return  // sin cambio
            foregroundPackage = pkg

            val wasInSocial = isSocialMediaForeground
            isSocialMediaForeground = pkg in SOCIAL_MEDIA_PACKAGES

            // Notificar al servicio si cambió el modo
            if (wasInSocial != isSocialMediaForeground) {
                sendBroadcast(Intent(DynamicIslandService.ACTION_SOCIAL_MODE).apply {
                    `package` = packageName
                    putExtra("active", isSocialMediaForeground)
                })
            }
        }
    }

    override fun onInterrupt() {}
}
