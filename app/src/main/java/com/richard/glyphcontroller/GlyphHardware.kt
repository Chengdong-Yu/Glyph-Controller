package com.richard.glyphcontroller

import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit

/** Detects the sysfs nodes needed to control this device's Glyph LEDs. */
object GlyphHardware {
    private const val TAG = "GlyphHardware"
    private const val BASE = "/sys/class/leds/led_strips"
    private val requiredNodes = arrayOf("$BASE/operating_mode", "$BASE/frame_brightness")

    /** Returns null when root is available but the probe could not complete. */
    fun isSupported(rootAvailable: Boolean): Boolean? {
        if (requiredNodes.all { File(it).exists() }) return true
        if (!rootAvailable) return false

        return try {
            val command = requiredNodes.joinToString(" && ") { "[ -e '$it' ]" } +
                " && echo GLYPH_PRESENT || echo GLYPH_MISSING"
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                Log.w(TAG, "Glyph node check timed out")
                return null
            }
            if (process.exitValue() != 0) return null
            val lines = process.inputStream.bufferedReader().use { it.readLines() }
            when {
                lines.any { it.trim() == "GLYPH_PRESENT" } -> true
                lines.any { it.trim() == "GLYPH_MISSING" } -> false
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to check Glyph nodes", e)
            null
        }
    }
}
