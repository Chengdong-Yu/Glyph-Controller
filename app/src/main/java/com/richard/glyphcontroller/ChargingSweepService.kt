package com.richard.glyphcontroller

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * 充电触发流光：由 root 看门狗在检测到充电器插入（sysfs 上升沿）时
 * 通过 am startservice 调起，播放从下往上的流光动画。
 */
class ChargingSweepService : Service() {

    private val mLock = Any()
    private var mClient: RootGlyphClient? = null
    private var mAnimRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 功能开关：关闭时不执行流光（root 看门狗仍会调用本服务，此处直接忽略）
        if (!getSharedPreferences("settings", MODE_PRIVATE).getBoolean("charger_enabled", true)) {
            Log.d(TAG, "charger sweep disabled by user, skip")
            return START_NOT_STICKY
        }
        synchronized(mLock) {
            if (mAnimRunning) {
                Log.d(TAG, "sweep already running, skip")
                return START_NOT_STICKY
            }
            mAnimRunning = true
        }
        Thread(mAnimation).start()
        return START_NOT_STICKY
    }

    private val mAnimation = Runnable {
        val client: RootGlyphClient
        synchronized(mLock) {
            if (mClient == null) mClient = RootGlyphClient()
            client = mClient!!
        }
        if (!client.isRootAvailable()) {
            Log.w(TAG, "no root, skip sweep")
            synchronized(mLock) {
                mAnimRunning = false
            }
            stopSelf()
            return@Runnable
        }
        try {
            val f = IntArray(33)
            // 从下往上扫：已扫过的段全亮，波前 2 段以半亮/微亮预亮，过渡丝滑
            for (i in SEQUENCE.indices) {
                synchronized(mLock) {
                    for (j in 0..i) {
                        f[SEQUENCE[j]] = 255
                    }
                    if (i + 1 < SEQUENCE.size) f[SEQUENCE[i + 1]] = 130
                    if (i + 2 < SEQUENCE.size) f[SEQUENCE[i + 2]] = 60
                    client.setZoneColors(f)
                }
                Thread.sleep(STEP_MS)
            }
            // 保持全亮
            Thread.sleep(HOLD_MS)
            // 快速渐暗熄灭（2 帧 + 熄灯）
            var b = 170
            while (b > 0) {
                for (idx in SEQUENCE) f[idx] = b
                synchronized(mLock) {
                    client.setZoneColors(f)
                }
                Thread.sleep(FADE_MS)
                b -= 85
            }
        } catch (ignored: InterruptedException) {
        } finally {
            client.turnOff()
            synchronized(mLock) {
                mAnimRunning = false
            }
            stopSelf()
        }
    }

    override fun onDestroy() {
        synchronized(mLock) {
            mClient?.destroy()
            mClient = null
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "GlyphSweep"
        private const val STEP_MS = 15L   // 逐段点亮间隔
        private const val HOLD_MS = 400L  // 全亮保持时长
        private const val FADE_MS = 30L   // 熄灭渐暗每帧间隔

        /** 从下往上的点亮顺序 */
        private val SEQUENCE = intArrayOf(
            25, 26, 27, 28, 29, 30, 31, 32,   // 感叹号竖条：底 -> 顶
            24,                                // 圆点
            21, 22,                            // 左下弧、右下弧
            20, 23,                            // 左竖条、右竖条
            19,                                // 上侧弧
            3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18 // 主环：右下 -> 顶
        )
    }
}
