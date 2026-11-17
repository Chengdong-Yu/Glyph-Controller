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
import kotlinx.coroutines.launch

/**
 * 充电触发流光：由系统插电广播或 root 看门狗调起，播放自下而上的 29 段流光动画。
 */
class ChargingSweepService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var animationJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!getSharedPreferences("settings", MODE_PRIVATE).getBoolean("charger_enabled", true)) {
            Log.d(TAG, "charger sweep disabled by user, skip")
            stopSelf()
            return START_NOT_STICKY
        }

        animationJob?.cancel()
        animationJob = serviceScope.launch {
            GlyphEffects.playChargingSweep(applicationContext)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "GlyphSweep"
    }
}
