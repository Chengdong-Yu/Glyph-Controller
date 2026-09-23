package com.richard.glyphcontroller

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.util.Log

/** Invisible entry point used by the root watchdog after MIUI force-stops the app. */
class WakeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            startService(Intent(this, KeepAliveService::class.java))
            NotificationListenerService.requestRebind(
                ComponentName(this, GlyphNotificationService::class.java)
            )
        } catch (e: Exception) {
            Log.w("GlyphKeepAlive", "wake activity could not restore services", e)
        } finally {
            finish()
        }
    }
}
