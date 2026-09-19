package com.richard.glyphcontroller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** 系统插电广播（ACTION_POWER_CONNECTED 为豁免的隐式广播，清单注册即可收到） */
class ChargingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_POWER_CONNECTED == intent.action) {
            Log.d("GlyphCharger", "power connected broadcast")
            try {
                context.startService(Intent(context, ChargingSweepService::class.java))
            } catch (e: Exception) {
                Log.w("GlyphCharger", "start sweep failed: $e")
            }
            // 顺带修复 root 监控循环（进程刚被广播唤醒时 sInstalled 为 false）
            val app = context.applicationContext
            Thread {
                val client = RootGlyphClient()
                RootKeepAlive.ensureInstalled(app, client)
                client.destroy()
            }.start()
        }
    }
}
