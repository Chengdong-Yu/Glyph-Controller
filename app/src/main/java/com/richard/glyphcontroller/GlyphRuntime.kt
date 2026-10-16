package com.richard.glyphcontroller

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Coalesce recovery requests from activities, receivers and services using Kotlin Coroutines. */
object GlyphRuntime {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val pending = AtomicBoolean(false)

    fun ensure(context: Context) {
        if (!pending.compareAndSet(false, true)) return
        val app = context.applicationContext
        scope.launch {
            try {
                val readiness = GlyphEffectCoordinator.checkAndRestore(app)
                if (readiness.hardware == GlyphHardware.Status.READY) {
                    if (RootKeepAlive.autoInstallEnabled(app)) {
                        RootKeepAlive.ensureInstalled(app)
                    }
                    GlyphListenerState.repair(app)
                    GlyphVolumeCoordinator.start(app)
                }
            } catch (error: Exception) {
                Log.e("GlyphRecovery", "Recovery failed", error)
            } finally {
                pending.set(false)
            }
        }
    }
}
