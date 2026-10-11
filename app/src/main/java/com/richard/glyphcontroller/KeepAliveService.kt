package com.richard.glyphcontroller

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** Root watchdog entry point; recovery is serialized and coalesced process-wide. */
class KeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        GlyphRuntime.ensure(applicationContext)
        return START_STICKY
    }
}
