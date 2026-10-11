package com.richard.glyphcontroller

import android.app.NotificationManager
import android.content.Context

/** Only a known Do Not Disturb filter should suppress notification effects. */
internal object GlyphDoNotDisturb {
    const val PAUSE_NOTIFICATION_SETTING = "notify_pause_dnd"

    fun shouldPauseNotificationFlash(context: Context): Boolean =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean(PAUSE_NOTIFICATION_SETTING, true) && isActive(context)

    fun isActive(context: Context): Boolean = when (
        context.getSystemService(NotificationManager::class.java).currentInterruptionFilter
    ) {
        NotificationManager.INTERRUPTION_FILTER_PRIORITY,
        NotificationManager.INTERRUPTION_FILTER_ALARMS,
        NotificationManager.INTERRUPTION_FILTER_NONE -> true
        else -> false
    }
}
