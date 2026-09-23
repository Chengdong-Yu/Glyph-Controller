package com.richard.glyphcontroller

import android.content.Context

/** Frame positions are 0..32; LED IDs are only shown as a hardware reference. */
object GlyphZones {
    val ledIds = intArrayOf(
        12, 0, 24, 2, 3, 4, 5, 6, 7, 8, 14, 15, 16, 17, 18, 19, 20,
        26, 27, 28, 29, 30, 31, 32, 9, 21, 33, 10, 22, 34, 11, 23, 35
    )

    /** Names and frame positions follow the Phone (2) map in Glyph Developer Kit. */
    fun name(index: Int): String = when (index) {
        0 -> "A1"
        1 -> "A2"
        2 -> "B1"
        in 3..18 -> "C1_${index - 2}"
        in 19..23 -> "C${index - 17}"
        24 -> "E1"
        in 25..32 -> "D1_${index - 24}"
        else -> throw IndexOutOfBoundsException("Glyph frame index: $index")
    }

    fun groupName(index: Int): Int = when (index) {
        in 0..1 -> R.string.zone_group_camera
        2 -> R.string.zone_group_diagonal
        in 3..18 -> R.string.zone_group_ring_top
        19 -> R.string.zone_group_ring_upper_left
        20 -> R.string.zone_group_ring_left
        21 -> R.string.zone_group_ring_lower_left
        22 -> R.string.zone_group_ring_lower_right
        23 -> R.string.zone_group_ring_right
        24 -> R.string.zone_group_dot
        in 25..32 -> R.string.zone_group_lower
        else -> throw IndexOutOfBoundsException("Glyph frame index: $index")
    }

    fun presetAll(level: Int = 255) = IntArray(GlyphPowerState.ZONE_COUNT) { level }

    fun presetRing() = IntArray(GlyphPowerState.ZONE_COUNT) { if (it in 3..23) 200 else 0 }

    fun presetCameraAndLower() = IntArray(GlyphPowerState.ZONE_COUNT) {
        if (it in 0..1 || it in 24..32) 220 else 0
    }
}

/** One editable scene, separate from the frame currently applied to the LEDs. */
object GlyphSceneStore {
    private const val PREFS = "glyph_scenes"
    private const val KEY_CUSTOM = "custom_frame"

    fun save(context: Context, frame: IntArray): Boolean {
        if (frame.size != GlyphPowerState.ZONE_COUNT || frame.any { it !in 0..255 }) return false
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_CUSTOM, frame.joinToString(","))
            .commit()
    }

    fun load(context: Context): IntArray? {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM, null) ?: return null
        val values = saved.split(',').map { it.toIntOrNull() }
        if (values.size != GlyphPowerState.ZONE_COUNT || values.any { it == null || it !in 0..255 }) {
            return null
        }
        return IntArray(GlyphPowerState.ZONE_COUNT) { values[it]!! }
    }
}
