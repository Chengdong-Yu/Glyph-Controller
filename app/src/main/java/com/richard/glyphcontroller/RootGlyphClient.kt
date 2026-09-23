package com.richard.glyphcontroller

import android.util.Log
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Writes Glyph frames through a persistent root shell and waits for each result. */
class RootGlyphClient {
    private var active = false
    private var suProcess: Process? = null
    private var suInput: OutputStream? = null
    private var outputLines: LinkedBlockingQueue<String>? = null
    private var ready = false
    private var commandCounter = 0L

    private data class CommandResult(val success: Boolean, val output: String = "")

    @Synchronized
    private fun ensureRoot(): Boolean {
        if (ready && suProcess?.isAlive == true) return true
        closeShell()

        return try {
            val probe = ProcessBuilder("su", "-c", "id -u")
                .redirectErrorStream(true)
                .start()
            if (!probe.waitFor(15, TimeUnit.SECONDS)) {
                probe.destroyForcibly()
                Log.e(TAG, "Root check timed out")
                return false
            }
            val uid = probe.inputStream.bufferedReader().use { it.readText().trim() }
            if (probe.exitValue() != 0 || uid != "0") {
                Log.e(TAG, "Root check failed: uid=$uid")
                return false
            }

            val process = ProcessBuilder("su").redirectErrorStream(true).start()
            val lines = LinkedBlockingQueue<String>()
            suProcess = process
            suInput = process.outputStream
            outputLines = lines
            Thread {
                try {
                    process.inputStream.bufferedReader().use { reader ->
                        while (true) lines.offer(reader.readLine() ?: break)
                    }
                } catch (_: Exception) {
                    // Closing the shell also closes its output pipe.
                } finally {
                    lines.offer(END_OF_STREAM)
                }
            }.apply {
                name = "GlyphRootOutput"
                isDaemon = true
                start()
            }

            ready = process.isAlive
            if (!ready) {
                Log.e(TAG, "Root shell exited before it was ready")
                closeShell()
            }
            ready
        } catch (e: Exception) {
            closeShell()
            Log.e(TAG, "Failed to get root", e)
            false
        }
    }

    @Synchronized
    private fun execute(command: String): CommandResult {
        if (!ensureRoot()) return CommandResult(false)
        val lines = outputLines ?: return CommandResult(false)
        val marker = "__GLYPH_RESULT_${++commandCounter}__"

        return try {
            val tagged = "$command\n__glyph_status=\$?\n" +
                "printf '\\n%s:%s\\n' '$marker' \"\$__glyph_status\"\n"
            suInput!!.write(tagged.toByteArray())
            suInput!!.flush()

            val output = StringBuilder()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(COMMAND_TIMEOUT_SECONDS)
            while (true) {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) break
                val line = lines.poll(remaining, TimeUnit.NANOSECONDS) ?: break
                if (line == END_OF_STREAM) break
                if (line.startsWith("$marker:")) {
                    val status = line.substring(marker.length + 1).trim().toIntOrNull()
                    val result = CommandResult(status == 0, output.toString().trim())
                    if (!result.success) {
                        Log.e(TAG, "Root command failed ($status): ${result.output}")
                    }
                    return result
                }
                if (output.length < MAX_OUTPUT_CHARS) output.append(line).append('\n')
            }

            Log.e(TAG, "Root command timed out or shell exited: $command")
            closeShell()
            CommandResult(false, output.toString().trim())
        } catch (e: Exception) {
            Log.e(TAG, "Root command failed: $command", e)
            closeShell()
            CommandResult(false)
        }
    }

    private fun closeShell() {
        try {
            suInput?.close()
        } catch (_: Exception) { }
        suProcess?.destroyForcibly()
        suInput = null
        suProcess = null
        outputLines = null
        ready = false
        active = false
    }

    fun isRootAvailable(): Boolean = ensureRoot()

    @Synchronized
    fun activate(): Boolean {
        val success = execute("echo 1 > $OPERATING_MODE").success
        active = success
        return success
    }

    @Synchronized
    fun standby(): Boolean {
        val success = execute("echo 2 > $OPERATING_MODE").success
        if (success) active = false
        return success
    }

    /** Values are frame indices 0..32, each with brightness 0..255. */
    @Synchronized
    fun setZoneColors(values: IntArray?): Boolean {
        if (values == null || values.size != ZONE_COUNT) return false
        if (!active && !activate()) return false
        val frame = values.joinToString(" ") { it.coerceIn(0, 255).toString() }
        val success = execute("echo '$frame' > $FRAME_BRIGHTNESS").success
        if (!success) active = false
        return success
    }

    @Synchronized
    fun setSingleLed(ledId: Int, brightness: Int): Boolean {
        if (!active && !activate()) return false
        val id = ledId.coerceIn(0, 35)
        val level = brightness.coerceIn(0, 255)
        return execute("echo '$id $level' > $SINGLE_BRIGHTNESS").success
    }

    @Synchronized
    fun setAllBrightness(brightness: Int): Boolean {
        if (!active && !activate()) return false
        val level = brightness.coerceIn(0, 255)
        return execute("echo $level > $ALL_BRIGHTNESS").success
    }

    @Synchronized
    fun turnOnAll(): Boolean = setZoneColors(IntArray(ZONE_COUNT) { 255 })

    @Synchronized
    fun turnOff(): Boolean {
        val frameWritten = setZoneColors(IntArray(ZONE_COUNT))
        val standbySet = standby()
        return frameWritten && standbySet
    }

    /** Kept for the driver's five-zone frame mode. */
    @Synchronized
    fun setZonePattern5(values: IntArray?): Boolean {
        if (values == null || values.size < 5) return false
        if (!active && !activate()) return false
        val frame = values.take(5).joinToString(" ") { it.coerceIn(0, 255).toString() }
        return execute("echo '$frame' > $FRAME_BRIGHTNESS").success
    }

    @Synchronized
    fun shell(command: String): String? {
        val result = execute(command)
        return if (result.success) result.output else null
    }

    @Synchronized
    fun destroy() {
        try {
            suInput?.write("exit\n".toByteArray())
            suInput?.flush()
            suProcess?.waitFor(2, TimeUnit.SECONDS)
        } catch (_: Exception) { }
        closeShell()
    }

    companion object {
        private const val TAG = "RootGlyphClient"
        private const val END_OF_STREAM = "__GLYPH_ROOT_EOF__"
        private const val COMMAND_TIMEOUT_SECONDS = 5L
        private const val MAX_OUTPUT_CHARS = 4096

        private const val SYSFS_BASE = "/sys/class/leds/led_strips"
        private const val OPERATING_MODE = "$SYSFS_BASE/operating_mode"
        private const val FRAME_BRIGHTNESS = "$SYSFS_BASE/frame_brightness"
        private const val SINGLE_BRIGHTNESS = "$SYSFS_BASE/single_brightness"
        private const val ALL_BRIGHTNESS = "$SYSFS_BASE/all_brightness"
        private const val ZONE_COUNT = 33
    }
}
