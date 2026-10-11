package com.richard.glyphcontroller

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Log

/** Permission, a successful rebind request and an actual connection are distinct states. */
object GlyphListenerState {
    enum class Status { OFF, CONNECTED, NEEDS_ACCESS, RECOVERING, FAILED }
    @Volatile private var connected = false
    @Volatile private var recoveringUntil = 0L
    @Volatile private var nextAttempt = 0L
    private var retrySeconds = 15L

    private fun component(context: Context) = ComponentName(context, GlyphNotificationService::class.java)
    private fun enabled(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        .getBoolean("notify_enabled", true)
    private fun granted(context: Context) = context.getSystemService(NotificationManager::class.java)
        .isNotificationListenerAccessGranted(component(context))

    fun status(context: Context): Status = when {
        !enabled(context) -> Status.OFF
        connected && granted(context) -> Status.CONNECTED
        SystemClock.elapsedRealtime() < recoveringUntil -> Status.RECOVERING
        !granted(context) -> Status.NEEDS_ACCESS
        else -> Status.FAILED
    }

    @Synchronized
    fun onConnected() {
        connected = true
        recoveringUntil = 0
        nextAttempt = 0
        retrySeconds = 15
    }

    fun onDisconnected() { connected = false }

    /** Called on the recovery worker, with bounded retries rather than repeated grants. */
    fun repair(context: Context) {
        if (!enabled(context) || (connected && granted(context))) return
        val now = SystemClock.elapsedRealtime()
        synchronized(this) {
            if (now < nextAttempt) return
            nextAttempt = now + retrySeconds * 1000
            retrySeconds = (retrySeconds * 2).coerceAtMost(120)
            recoveringUntil = now + 10_000
        }
        try {
            if (!granted(context) && !RootKeepAlive.ensureListenerRegistered()) {
                recoveringUntil = 0
                return
            }
            if (!connected) NotificationListenerService.requestRebind(component(context))
            // Only onListenerConnected may change the state to CONNECTED.
        } catch (error: Exception) {
            recoveringUntil = 0
            Log.w("GlyphListener", "Listener recovery failed", error)
        }
    }
}
