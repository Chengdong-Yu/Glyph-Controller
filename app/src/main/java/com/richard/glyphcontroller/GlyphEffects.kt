package com.richard.glyphcontroller

import android.content.Context
import android.util.Log
import java.util.Arrays
import kotlinx.coroutines.delay

/**
 * Shared zero-extra-allocation hardware animation sequences used by both background services
 * and interactive in-app effect test buttons.
 */
object GlyphEffects {
    private const val TAG = "GlyphEffects"
    private const val FLASH_ON_MS = 90L
    private const val FLASH_GAP_MS = 40L

    private const val SWEEP_STEP_MS = 15L
    private const val SWEEP_HOLD_MS = 400L
    private const val SWEEP_FADE_MS = 30L

    /** Three-stage sequential notification flash: bottom spine + dot -> upper C1 arc -> camera strips. */
    private val NOTIFICATION_GROUPS = arrayOf(
        intArrayOf(25, 26, 27, 28, 29, 30, 31, 32, 24),
        intArrayOf(13, 14, 15, 16, 17, 18),
        intArrayOf(0, 1),
    )

    /** Bottom-to-top 29-zone charging sweep trajectory. */
    private val CHARGING_SEQUENCE = intArrayOf(
        25, 26, 27, 28, 29, 30, 31, 32,
        24,
        21, 22,
        20, 23,
        19,
        3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18,
    )

    suspend fun playNotificationFlash(
        context: Context,
        shouldAbort: () -> Boolean = { false },
    ): Boolean {
        if (shouldAbort()) return false
        val app = context.applicationContext
        val lease = GlyphEffectCoordinator.beginEffect(app, GlyphSession.Effect.NOTIFICATION)
        if (lease == null) {
            Log.d(TAG, "Notification flash skipped: hardware unavailable or higher-priority lease active")
            return false
        }
        try {
            val buffer = IntArray(GlyphFrame.ZONE_COUNT)
            for (group in NOTIFICATION_GROUPS) {
                if (shouldAbort()) return true
                Arrays.fill(buffer, 0)
                for (idx in group) buffer[idx] = 255
                if (!GlyphEffectCoordinator.renderFrame(lease, buffer, sync = false)) return false
                delay(FLASH_ON_MS)
                Arrays.fill(buffer, 0)
                if (!GlyphEffectCoordinator.renderFrame(lease, buffer, sync = false)) return false
                delay(FLASH_GAP_MS)
            }
            return true
        } finally {
            GlyphEffectCoordinator.finishEffect(app, lease)
        }
    }

    suspend fun playChargingSweep(context: Context): Boolean {
        val app = context.applicationContext
        val lease = GlyphEffectCoordinator.beginEffect(app, GlyphSession.Effect.CHARGING)
        if (lease == null) {
            Log.w(TAG, "Charging sweep skipped: hardware unavailable or higher-priority lease active")
            return false
        }
        try {
            val buffer = IntArray(GlyphFrame.ZONE_COUNT)
            for (i in CHARGING_SEQUENCE.indices) {
                for (j in 0..i) {
                    buffer[CHARGING_SEQUENCE[j]] = 255
                }
                if (i + 1 < CHARGING_SEQUENCE.size) buffer[CHARGING_SEQUENCE[i + 1]] = 130
                if (i + 2 < CHARGING_SEQUENCE.size) buffer[CHARGING_SEQUENCE[i + 2]] = 60
                if (!GlyphEffectCoordinator.renderFrame(lease, buffer, sync = false)) return false
                delay(SWEEP_STEP_MS)
            }
            delay(SWEEP_HOLD_MS)
            var brightness = 170
            while (brightness > 0) {
                for (idx in CHARGING_SEQUENCE) buffer[idx] = brightness
                if (!GlyphEffectCoordinator.renderFrame(lease, buffer, sync = false)) return false
                delay(SWEEP_FADE_MS)
                brightness -= 85
            }
            return true
        } finally {
            GlyphEffectCoordinator.finishEffect(app, lease)
        }
    }
}
