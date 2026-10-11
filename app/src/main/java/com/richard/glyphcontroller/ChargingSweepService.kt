package com.richard.glyphcontroller

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 充电触发流光：由 root 看门狗在检测到充电器插入（sysfs 上升沿）时
 * 通过 am startservice 调起，使用 Kotlin 协程播放从下往上的丝滑流光动画。
 */
class ChargingSweepService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var animationJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 功能开关：关闭时不执行流光
        if (!getSharedPreferences("settings", MODE_PRIVATE).getBoolean("charger_enabled", true)) {
            Log.d(TAG, "charger sweep disabled by user, skip")
            stopSelf()
            return START_NOT_STICKY
        }

        animationJob?.cancel()
        animationJob = serviceScope.launch {
            runAnimation()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private suspend fun runAnimation() {
        val lease = GlyphEffectCoordinator.beginEffect(applicationContext, GlyphSession.Effect.CHARGING)
        if (lease == null) {
            Log.w(TAG, "Glyph unavailable or higher-priority effect is active")
            return
        }
        try {
            val f = IntArray(GlyphPowerState.ZONE_COUNT)
            // 从下往上扫：波前 2 段以半亮/微亮预亮，使用非阻塞异步帧写入
            for (i in SEQUENCE.indices) {
                for (j in 0..i) {
                    f[SEQUENCE[j]] = 255
                }
                if (i + 1 < SEQUENCE.size) f[SEQUENCE[i + 1]] = 130
                if (i + 2 < SEQUENCE.size) f[SEQUENCE[i + 2]] = 60
                if (!GlyphEffectCoordinator.renderFrame(lease, f, sync = false)) return
                delay(STEP_MS)
            }
            // 保持全亮
            delay(HOLD_MS)
            // 快速渐暗熄灭
            var b = 170
            while (b > 0) {
                for (idx in SEQUENCE) f[idx] = b
                if (!GlyphEffectCoordinator.renderFrame(lease, f, sync = false)) return
                delay(FADE_MS)
                b -= 85
            }
        } finally {
            GlyphEffectCoordinator.finishEffect(applicationContext, lease)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "GlyphSweep"
        private const val STEP_MS = 15L   // 逐段点亮间隔 (ms)
        private const val HOLD_MS = 400L  // 全亮保持时长 (ms)
        private const val FADE_MS = 30L   // 熄灭渐暗每帧间隔 (ms)

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
