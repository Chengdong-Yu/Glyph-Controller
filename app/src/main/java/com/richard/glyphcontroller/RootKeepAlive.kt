package com.richard.glyphcontroller

import android.content.Context
import android.util.Log
import java.util.concurrent.TimeUnit

/** Installs and supervises the root watchdog in KernelSU/Magisk service.d. */
object RootKeepAlive {
    private const val TAG = "GlyphKeepAlive"
    private const val PKG = "com.richard.glyphcontroller"
    private const val SCRIPT = "/data/adb/service.d/glyph_keepalive.sh"
    private const val TMP_NAME = "glyph_keepalive.sh"

    private var sInstalled = false

    @Synchronized
    fun ensureInstalled(context: Context, client: RootGlyphClient?) {
        if (client == null || !client.isRootAvailable()) return
        if (GlyphHardware.isSupported(rootAvailable = true) == false) return
        if (sInstalled && daemonRunning()) return

        try {
            writeScript(context)
            val source = quote(context.getFileStreamPath(TMP_NAME).absolutePath)
            val target = quote(SCRIPT)
            val staged = quote("$SCRIPT.new")
            if (daemonRunning() && runRoot("cmp -s $source $target", logFailure = false)) {
                sInstalled = true
                return
            }
            val command = listOf(
                "set -e",
                "mkdir -p /data/adb/service.d",
                "cp $source $staged",
                "chmod 700 $staged",
                "mv -f $staged $target",
                "sh $target --stop",
                "pkill -f '[G]KA_LOOP' >/dev/null 2>&1 || :",
                "rm -f /data/adb/glyphcontroller/keepalive.lock",
                "sh $target",
                "attempt=0",
                "while [ \$attempt -lt 5 ]; do",
                "  if sh $target --status; then exit 0; fi",
                "  attempt=\$((attempt + 1))",
                "  sleep 1",
                "done",
                "exit 1"
            ).joinToString("\n")

            sInstalled = runRoot(command)
            if (sInstalled) {
                Log.d(TAG, "watchdog installed and running")
            } else {
                Log.w(TAG, "watchdog installation did not start a daemon")
            }
        } catch (e: Exception) {
            sInstalled = false
            Log.e(TAG, "watchdog installation failed", e)
        }
    }

    /** Keep notification-listener registration outside the watchdog script. */
    fun ensureListenerRegistered() {
        runRoot(
            "cmd notification allow_listener " +
                quote("$PKG/.GlyphNotificationService") +
                " >/dev/null 2>&1"
        )
    }

    private fun writeScript(context: Context) {
        context.assets.open(TMP_NAME).use { source ->
            context.openFileOutput(TMP_NAME, Context.MODE_PRIVATE).use { target ->
                source.copyTo(target)
            }
        }
    }

    private fun daemonRunning(): Boolean {
        return runRoot("sh ${quote(SCRIPT)} --status", logFailure = false)
    }

    private fun runRoot(command: String, logFailure: Boolean = true): Boolean {
        try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                Log.e(TAG, "root command timed out")
                return false
            }
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            if (process.exitValue() != 0) {
                if (logFailure) Log.e(TAG, "root command failed (${process.exitValue()}): $output")
                return false
            }
            return true
        } catch (e: Exception) {
            if (logFailure) Log.e(TAG, "root command could not run", e)
            return false
        }
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
