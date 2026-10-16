package com.richard.glyphcontroller

import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit

/** Short root operations; drain output concurrently to avoid filling the pipe. */
internal object RootCommand {
    private val rootAttemptWindow = AtomicLong(0L)
    @Volatile private var suppressedWindow = Long.MIN_VALUE

    class Result(
        val exitCode: Int?,
        val output: String,
        val timedOut: Boolean = false,
        internal val attemptWindow: Long,
    ) {
        val success: Boolean get() = exitCode == 0 && !timedOut
    }

    fun beginRootAttemptWindow() {
        rootAttemptWindow.incrementAndGet()
    }

    fun areRootAttemptsSuppressed(): Boolean = suppressedWindow == rootAttemptWindow.get()

    fun suppressRootAttempts(result: Result? = null) {
        val currentWindow = rootAttemptWindow.get()
        if (result == null || result.attemptWindow == currentWindow) {
            suppressedWindow = currentWindow
        }
    }

    fun run(command: String, seconds: Long, suppressOnFailure: Boolean = false): Result {
        val attemptWindow = rootAttemptWindow.get()
        if (suppressedWindow == attemptWindow) {
            return Result(null, "Root unavailable for this app-open window", attemptWindow = attemptWindow)
        }
        var process: Process? = null
        val result = try {
            val child = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            process = child
            val output = StringBuilder()
            val reader = Thread {
                try {
                    child.inputStream.reader().use { stream ->
                        val buffer = CharArray(1024)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            synchronized(output) {
                                val keep = minOf(count, (4096 - output.length).coerceAtLeast(0))
                                output.append(buffer, 0, keep)
                            }
                        }
                    }
                } catch (_: Exception) { }
            }.apply { name = "GlyphCommandOutput"; isDaemon = true; start() }
            val finished = child.waitFor(seconds, TimeUnit.SECONDS)
            if (!finished) child.destroyForcibly()
            reader.join(250)
            Result(
                if (finished) child.exitValue() else null,
                synchronized(output) { output.toString().trim() },
                !finished,
                attemptWindow,
            )
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            Result(null, "Interrupted", attemptWindow = attemptWindow)
        } catch (error: IOException) {
            if (rootAttemptWindow.get() == attemptWindow) suppressedWindow = attemptWindow
            Result(null, error.javaClass.simpleName, attemptWindow = attemptWindow)
        } catch (error: Exception) {
            Result(null, error.javaClass.simpleName, attemptWindow = attemptWindow)
        } finally {
            process?.let { child ->
                if (child.isAlive) child.destroyForcibly()
                runCatching { child.outputStream.close() }
                runCatching { child.inputStream.close() }
            }
        }
        if (suppressOnFailure && !result.success && !result.timedOut && result.output != "Interrupted") {
            suppressRootAttempts(result)
        }
        return result
    }
}
