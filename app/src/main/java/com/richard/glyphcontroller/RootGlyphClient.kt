package com.richard.glyphcontroller

import android.util.Log
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Writes Glyph frames through a persistent root shell with synchronous and asynchronous options. */
class RootGlyphClient {
    private var active = false
    var rootCheckTimedOut = false
        private set
    private var suProcess: Process? = null
    private var suInput: OutputStream? = null
    private var outputLines: LinkedBlockingQueue<String>? = null
    private var ready = false
    private var commandCounter = 0L

    private class CommandResult(val success: Boolean, val output: String = "")

    @Synchronized
    private fun ensureRoot(): Boolean {
        if (ready && suProcess?.isAlive == true) return true
        closeShell()

        return try {
            val probe = RootCommand.run("id -u", 15, suppressOnFailure = true)
            rootCheckTimedOut = probe.timedOut
            if (!probe.success || probe.output != "0") {
                Log.e(TAG, "Root check failed (timeout=${probe.timedOut})")
                return false
            }

            val process = ProcessBuilder("su").redirectErrorStream(true).start()
            val lines = LinkedBlockingQueue<String>(128)
            suProcess = process
            suInput = process.outputStream
            outputLines = lines
            Thread {
                try {
                    process.inputStream.bufferedReader().use { reader ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (!lines.offer(line)) {
                                lines.poll()
                                lines.offer(line)
                            }
                        }
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
                if (output.length < MAX_OUTPUT_CHARS) output.append(line.take(MAX_OUTPUT_CHARS - output.length)).append('\n')
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

    @Synchronized
    private fun writeAsync(commandWithNewline: String): Boolean {
        if (!ensureRoot()) return false
        val input = suInput ?: return false
        return try {
            input.write(commandWithNewline.toByteArray(Charsets.US_ASCII))
            input.flush()
            true
        } catch (e: Exception) {
            Log.e(TAG, "writeAsync failed", e)
            closeShell()
            false
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
    private fun activate(): Boolean {
        val success = execute("echo 1 > $OPERATING_MODE").success
        active = success
        return success
    }

    @Synchronized
    private fun standby(): Boolean {
        val success = execute("echo 2 > $OPERATING_MODE").success
        if (success) active = false
        return success
    }

    /** Values are frame indices 0..32, each with brightness 0..255. */
    @Synchronized
    fun setZoneColors(values: IntArray?, sync: Boolean = true): Boolean {
        if (values == null || values.size != ZONE_COUNT) return false
        if (!ensureRoot()) return false
        if (!active && !activate()) return false

        val sb = StringBuilder(132)
        for (i in 0 until ZONE_COUNT) {
            if (i > 0) sb.append(' ')
            sb.append(values[i].coerceIn(0, 255))
        }
        val frame = sb.toString()

        return if (sync) {
            val success = execute("echo '$frame' > $FRAME_BRIGHTNESS").success
            if (!success) active = false
            success
        } else {
            writeAsync("echo '$frame' > $FRAME_BRIGHTNESS\n")
        }
    }

    @Synchronized
    fun turnOff(): Boolean {
        val frameWritten = setZoneColors(IntArray(ZONE_COUNT), sync = true)
        val standbySet = standby()
        return frameWritten && standbySet
    }

    @Synchronized
    fun shell(command: String): String? {
        val result = execute(command)
        return if (result.success) result.output else null
    }

    companion object {
        private const val TAG = "RootGlyphClient"
        private const val END_OF_STREAM = "__GLYPH_ROOT_EOF__"
        private const val COMMAND_TIMEOUT_SECONDS = 5L
        private const val MAX_OUTPUT_CHARS = 4096

        private const val SYSFS_BASE = "/sys/class/leds/led_strips"
        private const val OPERATING_MODE = "$SYSFS_BASE/operating_mode"
        private const val FRAME_BRIGHTNESS = "$SYSFS_BASE/frame_brightness"
        private const val ZONE_COUNT = GlyphPowerState.ZONE_COUNT
    }
}
