package com.richard.glyphcontroller

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream

/**
 * Root-based Glyph controller that directly writes to AW20036 sysfs nodes.
 *
 * Hardware: AW20036 LED driver on I2C bus (address 0x3a)
 * Matrix: 3x12 = 36 LEDs, 33 controllable zones on Nothing Phone (2)
 *
 * Sysfs paths:
 *   /sys/class/leds/led_strips/operating_mode  (0=off, 1=active, 2=standby)
 *   /sys/class/leds/led_strips/single_brightness  (format: "LED_ID BRIGHTNESS")
 *   /sys/class/leds/led_strips/frame_brightness   (space-separated brightness values)
 *   /sys/class/leds/led_strips/all_brightness      (single value for all LEDs)
 *
 * frame_brightness 33-LED mapping (input index -> AW20036 register):
 *   {12, 0, 24, 2, 3, 4, 5, 6, 7, 8, 14, 15, 16, 17, 18, 19, 20, 26, 27, 28, 29, 30, 31, 32, 9, 21, 33, 10, 22, 34, 11, 23, 35}
 */
class RootGlyphClient {

    private var mActive = false

    // Root shell
    private var mSuProcess: Process? = null
    private var mSuInput: OutputStream? = null
    private var mSuReady = false

    @Synchronized
    private fun ensureRoot(): Boolean {
        if (mSuReady) return true
        return try {
            mSuProcess = Runtime.getRuntime().exec("su")
            mSuInput = mSuProcess!!.outputStream
            // Test root
            exec("id")
            mSuReady = true
            Log.d(TAG, "Root access obtained")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get root: ${e.message}")
            false
        }
    }

    @Synchronized
    private fun exec(cmd: String): String? {
        if (!mSuReady) return null
        return try {
            // Write command and capture output
            val taggedCmd = "$cmd 2>&1 && echo __ROOT_OK__ || echo __ROOT_FAIL__"
            mSuInput!!.write("$taggedCmd\n".toByteArray())
            mSuInput!!.flush()

            // Read output
            val reader = BufferedReader(InputStreamReader(mSuProcess!!.inputStream))
            val sb = StringBuilder()
            val start = System.currentTimeMillis()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.contains("__ROOT_OK__")) break
                if (line.contains("__ROOT_FAIL__")) break
                if (System.currentTimeMillis() - start > 5000) break
                sb.append(line).append("\n")
            }
            sb.toString().trim()
        } catch (e: Exception) {
            Log.e(TAG, "exec failed: ${e.message}")
            null
        }
    }

    @Synchronized
    private fun execVoid(cmd: String) {
        if (!mSuReady) return
        try {
            mSuInput!!.write("$cmd\n".toByteArray())
            mSuInput!!.flush()
        } catch (e: Exception) {
            Log.e(TAG, "execVoid failed: ${e.message}")
        }
    }

    fun isRootAvailable(): Boolean = ensureRoot()

    /**
     * Activate the AW20036 chip (operating_mode = 1)
     */
    @Synchronized
    fun activate() {
        if (!ensureRoot()) {
            Log.e(TAG, "No root access")
            return
        }
        execVoid("echo 1 > $SYSFS_OPERATING_MODE")
        mActive = true
        Log.d(TAG, "AW20036 activated (mode=1)")
    }

    /**
     * Put AW20036 into standby (operating_mode = 2)
     */
    @Synchronized
    fun standby() {
        if (!ensureRoot()) return
        execVoid("echo 2 > $SYSFS_OPERATING_MODE")
        mActive = false
        Log.d(TAG, "AW20036 standby (mode=2)")
    }

    /**
     * Set brightness for all 33 glyph zones.
     * values[i] = 0-255 brightness for zone i
     *
     * Zone mapping (Nothing Phone 2, 33 zones):
     *   Zone 0: A1 (top right)
     *   Zone 1: A2 (top left)
     *   Zone 2: B1 (center top)
     *   Zone 3-18: C1 (outer ring, 16 zones)
     *   Zone 19-23: C2-C6 (inner elements)
     *   Zone 24: E1 (center)
     *   Zone 25-32: D1 (bottom vertical bar, 8 zones)
     */
    @Synchronized
    fun setZoneColors(values: IntArray?) {
        if (!ensureRoot()) {
            Log.e(TAG, "No root access")
            return
        }
        if (values == null || values.isEmpty()) return

        // Ensure chip is active
        if (!mActive) activate()

        // Build frame_brightness string; the kernel driver parses space-separated integers
        val sb = StringBuilder()
        for (i in 0 until minOf(values.size, ZONE_COUNT)) {
            if (i > 0) sb.append(" ")
            sb.append(values[i].coerceIn(0, 255))
        }

        execVoid("echo '$sb' > $SYSFS_FRAME_BRIGHTNESS")
        Log.d(TAG, "setZoneColors: ${values.size} zones")
    }

    /**
     * Set brightness for a single LED by AW20036 register ID (0-35)
     */
    @Synchronized
    fun setSingleLed(ledId: Int, brightness: Int) {
        if (!ensureRoot()) return
        if (!mActive) activate()

        val id = ledId.coerceIn(0, 35)
        val level = brightness.coerceIn(0, 255)

        execVoid("echo '$id $level' > $SYSFS_SINGLE_BRIGHTNESS")
    }

    /**
     * Set all LEDs to the same brightness
     */
    @Synchronized
    fun setAllBrightness(brightness: Int) {
        if (!ensureRoot()) return
        val level = brightness.coerceIn(0, 255)
        execVoid("echo $level > $SYSFS_ALL_BRIGHTNESS")
    }

    /**
     * Turn on all 33 glyph zones at max brightness
     */
    @Synchronized
    fun turnOnAll() {
        activate()
        val allOn = IntArray(ZONE_COUNT) { 255 }
        setZoneColors(allOn)
        Log.d(TAG, "All Glyph ON (33 zones)")
    }

    /**
     * Turn off all glyphs
     */
    @Synchronized
    fun turnOff() {
        val allOff = IntArray(ZONE_COUNT)
        setZoneColors(allOff)
        standby()
        Log.d(TAG, "All Glyph OFF")
    }

    /**
     * Set a specific zone pattern (for testing)
     * Uses the kernel's 5-zone mode (frame_num == 5)
     * Zones: 0=ring top, 1=center, 2=ring main, 3=vertical bars, 4=bottom
     */
    @Synchronized
    fun setZonePattern5(values: IntArray?) {
        if (!ensureRoot()) return
        if (!mActive) activate()

        if (values == null || values.size < 5) return

        val sb = StringBuilder()
        for (i in 0 until 5) {
            if (i > 0) sb.append(" ")
            sb.append(values[i].coerceIn(0, 255))
        }

        execVoid("echo '$sb' > $SYSFS_FRAME_BRIGHTNESS")
        Log.d(TAG, "setZonePattern5: $sb")
    }

    /**
     * Execute a raw shell command as root (for advanced usage)
     */
    fun shell(cmd: String): String? {
        if (!ensureRoot()) return null
        return exec(cmd)
    }

    /**
     * Cleanup: release root shell
     */
    @Synchronized
    fun destroy() {
        try {
            mSuInput?.write("exit\n".toByteArray())
            mSuInput?.flush()
            mSuInput?.close()
            mSuProcess?.waitFor()
        } catch (e: Exception) {
            // ignore
        }
        mSuReady = false
        Log.d(TAG, "Root shell released")
    }

    companion object {
        private const val TAG = "RootGlyphClient"

        private const val SYSFS_BASE = "/sys/class/leds/led_strips"
        private const val SYSFS_OPERATING_MODE = "$SYSFS_BASE/operating_mode"
        private const val SYSFS_FRAME_BRIGHTNESS = "$SYSFS_BASE/frame_brightness"
        private const val SYSFS_SINGLE_BRIGHTNESS = "$SYSFS_BASE/single_brightness"
        private const val SYSFS_ALL_BRIGHTNESS = "$SYSFS_BASE/all_brightness"

        // Nothing Phone (2) has 33 addressable glyph zones
        private const val ZONE_COUNT = 33
    }
}
