package com.richard.glyphcontroller

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import android.util.Log

/** Shared power state for the activity and Quick Settings tile. */
object GlyphPowerState {
    private const val PREFS = "glyph_state"
    private const val KEY_ON = "all_on"
    private const val KEY_FRAME = "manual_frame"
    const val ZONE_COUNT = GlyphFrame.ZONE_COUNT

    fun isOn(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, false)

    fun getFrame(context: Context): IntArray {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = preferences.getString(KEY_FRAME, null)
        GlyphFrame.decode(saved)?.let { return it }
        // Existing installs only have the all-on flag; keep their previous state.
        val level = if (preferences.getBoolean(KEY_ON, false)) 255 else 0
        return IntArray(ZONE_COUNT) { level }
    }

    /** Called off the main thread after the driver confirms the new state. */
    fun setFrame(context: Context, frame: IntArray): Boolean {
        if (!GlyphFrame.valid(frame)) return false
        val on = frame.any { it > 0 }
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ON, on)
            .putString(KEY_FRAME, frame.joinToString(","))
            .commit()
        if (!saved) return false
        try {
            TileService.requestListeningState(
                context,
                ComponentName(context, GlyphTileService::class.java)
            )
        } catch (e: Exception) {
            Log.w("GlyphPowerState", "Unable to refresh Quick Settings tile", e)
        }
        return true
    }

}
