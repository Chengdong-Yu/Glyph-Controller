package com.richard.glyphcontroller

import java.util.concurrent.TimeUnit

/** Short root operations; drain output concurrently to avoid filling the pipe. */
internal object RootCommand {
    class Result(val exitCode: Int?, val output: String, val timedOut: Boolean = false) {
        val success: Boolean get() = exitCode == 0 && !timedOut
    }

    fun run(command: String, seconds: Long): Result {
        var process: Process? = null
        return try {
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
            Result(if (finished) child.exitValue() else null,
                synchronized(output) { output.toString().trim() }, !finished)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            Result(null, "Interrupted")
        } catch (error: Exception) {
            Result(null, error.javaClass.simpleName)
        } finally {
            process?.let { child ->
                if (child.isAlive) child.destroyForcibly()
                runCatching { child.outputStream.close() }
                runCatching { child.inputStream.close() }
            }
        }
    }
}
