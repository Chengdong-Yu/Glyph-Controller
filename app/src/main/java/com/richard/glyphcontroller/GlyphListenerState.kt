package com.richard.glyphcontroller

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Permission, a successful rebind request and an actual connection are distinct states. */
object GlyphListenerState {
    enum class Status { OFF, CONNECTED, NEEDS_ACCESS, RECOVERING, FAILED }

    @Volatile private var connected = false
    @Volatile private var recoveringUntil = 0L
    @Volatile private var nextAttempt = 0L
    private var retrySeconds = 15L

    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes.asStateFlow()

    private fun notifyChanged() {
        _changes.value = _changes.value + 1L
    }

    private fun component(context: Context) = ComponentName(context, GlyphNotificationService::class.java)

    fun isAccessGranted(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(component(context))

    /**
     * Fresh installs without listener permission start disabled so turning on Notification Flash
     * for the first time can guide the user to grant Notification Access. Existing installs that
     * already granted access or saved an explicit preference keep their state.
     */
    fun isNotifyEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return if (prefs.contains("notify_enabled")) {
            prefs.getBoolean("notify_enabled", false)
        } else {
            isAccessGranted(context)
        }
    }

    fun status(context: Context): Status = when {
        !isNotifyEnabled(context) -> Status.OFF
        connected && isAccessGranted(context) -> Status.CONNECTED
        SystemClock.elapsedRealtime() < recoveringUntil -> Status.RECOVERING
        !isAccessGranted(context) -> Status.NEEDS_ACCESS
        else -> Status.FAILED
    }

    @Synchronized
    fun onConnected() {
        connected = true
        recoveringUntil = 0
        nextAttempt = 0
        retrySeconds = 15
        notifyChanged()
    }

    fun onDisconnected() {
        connected = false
        notifyChanged()
    }

    /** Called on the recovery worker, with bounded retries rather than repeated grants. */
    fun repair(context: Context) {
        if (!isNotifyEnabled(context) || (connected && isAccessGranted(context))) return
        val now = SystemClock.elapsedRealtime()
        synchronized(this) {
            if (now < nextAttempt) return
            nextAttempt = now + retrySeconds * 1000
            retrySeconds = (retrySeconds * 2).coerceAtMost(120)
            recoveringUntil = now + 10_000
        }
        notifyChanged()
        try {
            if (!isAccessGranted(context) && !RootKeepAlive.ensureListenerRegistered()) {
                recoveringUntil = 0
                notifyChanged()
                return
            }
            if (!connected) NotificationListenerService.requestRebind(component(context))
            // Only onListenerConnected may change the state to CONNECTED.
        } catch (error: Exception) {
            recoveringUntil = 0
            notifyChanged()
            Log.w("GlyphListener", "Listener recovery failed", error)
        }
    }
}
