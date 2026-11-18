package com.richard.glyphcontroller

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * 通知触发闪烁：来通知时三组灯带依次迅速亮起并熄灭一次——
 * 最下方 D1 + E1 -> C1 上弧末段 -> A1 + A2 相机灯带。
 */
class GlyphNotificationService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val flashMutex = Mutex()
    private var flashJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        GlyphRuntime.ensure(applicationContext)
        GlyphVolumeCoordinator.start(applicationContext)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        GlyphListenerState.onConnected()
        GlyphRuntime.ensure(applicationContext)
        GlyphVolumeCoordinator.start(applicationContext)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        GlyphListenerState.onDisconnected()
        GlyphRuntime.ensure(applicationContext)
    }

    override fun onDestroy() {
        GlyphListenerState.onDisconnected()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (!GlyphListenerState.isNotifyEnabled(this)) {
            return
        }
        if (GlyphDoNotDisturb.shouldPauseNotificationFlash(this)) return
        val n = sbn?.notification ?: return
        if ((n.flags and (Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_ONGOING_EVENT)) != 0) {
            return
        }

        serviceScope.launch {
            if (!flashMutex.tryLock()) return@launch
            try {
                flashJob = launch {
                    GlyphEffects.playNotificationFlash(
                        context = this@GlyphNotificationService,
                        shouldAbort = { GlyphDoNotDisturb.shouldPauseNotificationFlash(this@GlyphNotificationService) },
                    )
                }
                flashJob?.join()
            } finally {
                flashMutex.unlock()
            }
        }
    }
}
