package com.richard.glyphcontroller

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/** 空的粘性服务：root 看门狗用它把被杀的进程拉活；拉活的同时顺带修复监控循环 */
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("GlyphKeepAlive", "resurrected")
        val client = RootGlyphClient()
        Thread {
            RootKeepAlive.ensureInstalled(this, client)
            client.destroy()
        }.start()
        return START_STICKY
    }
}
