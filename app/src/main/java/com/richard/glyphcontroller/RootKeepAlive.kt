package com.richard.glyphcontroller

import android.content.Context
import android.util.Log
import androidx.core.content.edit

/** Installs and supervises the root watchdog in KernelSU/Magisk service.d. */
object RootKeepAlive {
    private const val TAG = "GlyphKeepAlive"
    private const val PKG = "com.richard.glyphcontroller"
    private const val SCRIPT = "/data/adb/service.d/glyph_keepalive.sh"
    private const val TMP_NAME = "glyph_keepalive.sh"
    private const val PREFS = "settings"
    private const val PREF_ENABLED = "keepalive_enabled"

    private var sInstalled = false

    enum class Status { RUNNING, INSTALLED, NOT_INSTALLED, UNKNOWN }

    fun autoInstallEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(PREF_ENABLED, true)

    @Synchronized
    fun ensureInstalled(context: Context): Boolean {
        if (!autoInstallEnabled(context)) return false
        return installInternal(context)
    }

    @Synchronized
    fun install(context: Context): Boolean {
        val installed = installInternal(context)
        if (installed) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putBoolean(PREF_ENABLED, true)
            }
        }
        return installed
    }

    @Synchronized
    fun remove(context: Context): Boolean {
        val target = quote(SCRIPT)
        val command = listOf(
            "if [ -f $target ]; then sh $target --stop || :; fi",
            "pkill -f '[G]KA_LOOP' >/dev/null 2>&1 || :",
            "rm -f /data/adb/glyphcontroller/keepalive.lock",
            "rm -f /data/adb/glyphcontroller/keepalive.lockdir/pid",
            "rmdir /data/adb/glyphcontroller/keepalive.lockdir 2>/dev/null || :",
            "rm -f ${quote("$SCRIPT.new")} $target",
            "test ! -e $target"
        ).joinToString("\n")
        val removed = runRoot(command)
        sInstalled = false
        if (removed) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putBoolean(PREF_ENABLED, false)
            }
            Log.d(TAG, "watchdog removed")
        }
        return removed
    }

    @Synchronized
    fun status(): Status {
        val target = quote(SCRIPT)
        val result = RootCommand.run(
            "if [ ! -f $target ]; then echo NOT_INSTALLED; " +
                "elif sh $target --status; then echo RUNNING; else echo INSTALLED; fi",
            15
        )
        if (!result.success) return Status.UNKNOWN
        return when (result.output.lineSequence().lastOrNull()?.trim()) {
            "RUNNING" -> Status.RUNNING
            "INSTALLED" -> Status.INSTALLED
            "NOT_INSTALLED" -> Status.NOT_INSTALLED
            else -> Status.UNKNOWN
        }
    }

    private fun installInternal(context: Context): Boolean {
        if (sInstalled && daemonRunning()) return true

        try {
            writeScript(context)
            val source = quote(context.getFileStreamPath(TMP_NAME).absolutePath)
            val target = quote(SCRIPT)
            val staged = quote("$SCRIPT.new")
            if (daemonRunning() && runRoot("cmp -s $source $target", logFailure = false)) {
                sInstalled = true
                return true
            }
            val command = listOf(
                "set -e",
                "mkdir -p /data/adb/service.d",
                "cp $source $staged",
                "chmod 700 $staged",
                "mv -f $staged $target",
                "sh $target --stop",
                "pkill -f '[G]KA_LOOP' >/dev/null 2>&1 || :",
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
        return sInstalled
    }

    /** Keep notification-listener registration outside the watchdog script. */
    fun ensureListenerRegistered(): Boolean =
        runRoot(
            "timeout -k 1 6 cmd notification allow_listener " +
                quote("$PKG/.GlyphNotificationService") +
                " >/dev/null 2>&1"
        )

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
        val result = RootCommand.run(command, 30)
        if (!result.success && logFailure) {
            Log.e(TAG, "Root command failed (timeout=${result.timedOut}): ${result.output}")
        }
        return result.success
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
