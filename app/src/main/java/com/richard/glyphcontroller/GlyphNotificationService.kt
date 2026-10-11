package com.richard.glyphcontroller

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * 通知触发闪烁：来通知时三组灯带依次迅速亮起并熄灭一次——
 * 最下方 D1 + E1 -> C1 上弧末段 -> A1 + A2 相机灯带。
 * 使用 Kotlin 协程调度，并在服务就绪时维护音量指示器监听。
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
        // 功能开关：关闭时直接忽略通知
        if (!getSharedPreferences("settings", MODE_PRIVATE).getBoolean("notify_enabled", true)) {
            return
        }
        if (GlyphDoNotDisturb.shouldPauseNotificationFlash(this)) return
        val n = sbn?.notification ?: return
        // 跳过分组摘要和常驻通知，避免重复触发
        if ((n.flags and (Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_ONGOING_EVENT)) != 0) {
            return
        }

        serviceScope.launch {
            if (!flashMutex.tryLock()) return@launch
            try {
                flashJob = launch { runFlash() }
                flashJob?.join()
            } finally {
                flashMutex.unlock()
            }
        }
    }

    private suspend fun runFlash() {
        if (GlyphDoNotDisturb.shouldPauseNotificationFlash(this)) return
        val lease = GlyphEffectCoordinator.beginEffect(applicationContext, GlyphSession.Effect.NOTIFICATION)
        if (lease == null) {
            Log.d(TAG, "Glyph unavailable or higher-priority effect active")
            return
        }
        try {
            val off = IntArray(GlyphPowerState.ZONE_COUNT)
            for (g in GROUPS) {
                if (GlyphDoNotDisturb.shouldPauseNotificationFlash(this)) return
                val on = IntArray(GlyphPowerState.ZONE_COUNT)
                for (idx in g) on[idx] = 255
                if (!GlyphEffectCoordinator.renderFrame(lease, on, sync = false)) return
                delay(ON_MS)
                if (!GlyphEffectCoordinator.renderFrame(lease, off, sync = false)) return
                delay(GAP_MS)
            }
        } finally {
            GlyphEffectCoordinator.finishEffect(this, lease)
        }
    }

    companion object {
        private const val TAG = "GlyphFlash"
        private const val ON_MS = 90L   // 每组亮起时长
        private const val GAP_MS = 40L  // 组间熄灭间隔

        /** 依次闪烁的三组灯带（frame 索引） */
        private val GROUPS = arrayOf(
            intArrayOf(25, 26, 27, 28, 29, 30, 31, 32, 24),  // 最下方：感叹号竖条 + 圆点
            intArrayOf(13, 14, 15, 16, 17, 18),              // C1_11..C1_16，上弧靠左
            intArrayOf(0, 1)                                 // A1 + A2 相机灯带
        )
    }
}
