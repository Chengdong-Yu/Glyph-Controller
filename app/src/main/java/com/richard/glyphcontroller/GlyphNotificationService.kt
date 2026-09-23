package com.richard.glyphcontroller

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * 通知触发闪烁：来通知时三组灯带依次迅速亮起并熄灭一次——
 * 最下方 D1 + E1 -> C1 上弧末段 -> A1 + A2 相机灯带。
 */
class GlyphNotificationService : NotificationListenerService() {

    private val mLock = Any()
    private var mFlashing = false
    private var mKeepAliveInstalled = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "service created")
        // 开机时系统会自动绑定通知监听 -> 借机安装/修复 root 监控循环（幂等）
        if (!mKeepAliveInstalled) {
            mKeepAliveInstalled = true
            Thread {
                val client = RootGlyphClient()
                try {
                    RootKeepAlive.ensureInstalled(this, client)
                    // Notification-listener registration can be lost after MIUI force-stop.
                    RootKeepAlive.ensureListenerRegistered()
                } finally {
                    client.destroy()
                }
            }.start()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // 功能开关：关闭时直接忽略通知
        if (!getSharedPreferences("settings", MODE_PRIVATE).getBoolean("notify_enabled", true)) {
            return
        }
        val n = sbn?.notification ?: return
        // 跳过分组摘要和常驻通知，避免重复触发
        if ((n.flags and (Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_ONGOING_EVENT)) != 0) {
            return
        }
        synchronized(mLock) {
            if (mFlashing) return
            mFlashing = true
        }
        Thread(mFlash).start()
    }

    private val mFlash = Runnable {
        val lease = GlyphEffectCoordinator.beginEffect(GlyphEffectCoordinator.Effect.NOTIFICATION)
        if (lease == null) {
            Log.d(TAG, "Glyph unavailable or higher-priority effect active")
            synchronized(mLock) {
                mFlashing = false
            }
            return@Runnable
        }
        try {
            val off = IntArray(33)
            for (g in GROUPS) {
                val on = IntArray(33)
                for (idx in g) on[idx] = 255
                if (!GlyphEffectCoordinator.renderFrame(lease, on)) return@Runnable
                Thread.sleep(ON_MS)
                if (!GlyphEffectCoordinator.renderFrame(lease, off)) return@Runnable
                Thread.sleep(GAP_MS)
            }
        } catch (ignored: InterruptedException) {
        } finally {
            GlyphEffectCoordinator.finishEffect(this, lease)
            synchronized(mLock) {
                mFlashing = false
            }
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
