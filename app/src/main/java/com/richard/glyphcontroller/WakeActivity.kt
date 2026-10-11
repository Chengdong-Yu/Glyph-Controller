package com.richard.glyphcontroller

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

/** Invisible entry point used by the root watchdog after MIUI force-stops the app. */
class WakeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            startService(Intent(this, KeepAliveService::class.java))
            GlyphRuntime.ensure(applicationContext)
        } catch (e: Exception) {
            Log.w("GlyphKeepAlive", "wake activity could not restore services", e)
        } finally {
            finish()
        }
    }
}
