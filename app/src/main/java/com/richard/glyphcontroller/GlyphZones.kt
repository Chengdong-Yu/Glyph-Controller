package com.richard.glyphcontroller

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import kotlin.math.roundToInt

/** Anatomical filter groups for the 33-zone Nothing Phone (2) array. */
enum class GlyphZoneGroup(
    @param:StringRes val labelRes: Int,
    val indices: IntRange,
) {
    ALL(R.string.group_filter_all, 0..32),
    CAMERA(R.string.group_filter_camera, 0..1),
    DIAGONAL(R.string.group_filter_diagonal, 2..2),
    RING_ARC(R.string.group_filter_ring_arc, 3..18),
    RING_FRAME(R.string.group_filter_ring_frame, 19..23),
    SPINE_DOT(R.string.group_filter_spine, 24..32),
}

/** Optical telemetry derived from a 33-zone brightness frame. */
@Immutable
data class GlyphTelemetry(
    val activeZones: Int,
    val meanPercent: Int,
    val peakLevel: Int,
    val cameraRatio: Float,
    val ringRatio: Float,
    val spineRatio: Float,
)

/** Frame positions are 0..32; LED IDs are only shown as a hardware reference. */
object GlyphZones {
    val ledIds = intArrayOf(
        12, 0, 24, 2, 3, 4, 5, 6, 7, 8, 14, 15, 16, 17, 18, 19, 20,
        26, 27, 28, 29, 30, 31, 32, 9, 21, 33, 10, 22, 34, 11, 23, 35,
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

    @StringRes
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

    /** Returns the anatomical cluster range containing [index] for synchronized group editing. */
    fun clusterRange(index: Int): IntRange = when (index) {
        in 0..1 -> 0..1
        2 -> 2..2
        in 3..18 -> 3..18
        in 19..23 -> 19..23
        in 24..32 -> 24..32
        else -> throw IndexOutOfBoundsException("Glyph frame index: $index")
    }

    fun groupForZone(index: Int): GlyphZoneGroup = when (index) {
        in 0..1 -> GlyphZoneGroup.CAMERA
        2 -> GlyphZoneGroup.DIAGONAL
        in 3..18 -> GlyphZoneGroup.RING_ARC
        in 19..23 -> GlyphZoneGroup.RING_FRAME
        in 24..32 -> GlyphZoneGroup.SPINE_DOT
        else -> GlyphZoneGroup.ALL
    }

    fun presetAll(level: Int = 255) = IntArray(GlyphPowerState.ZONE_COUNT) { level.coerceIn(0, 255) }

    fun presetRing() = IntArray(GlyphPowerState.ZONE_COUNT) { if (it in 3..23) 200 else 0 }

    fun presetCameraAndLower() = IntArray(GlyphPowerState.ZONE_COUNT) {
        if (it in 0..1 || it in 24..32) 220 else 0
    }

    /** Linear gradient across the 16-segment C1 arc and bottom D1 spine. */
    fun presetStarlight(): IntArray = IntArray(GlyphPowerState.ZONE_COUNT) { idx ->
        when (idx) {
            0, 1 -> 150
            2 -> 210
            in 3..18 -> {
                val pos = idx - 3
                (70 + pos * 185 / 15).coerceIn(0, 255)
            }
            in 19..23 -> 95
            24 -> 255
            in 25..32 -> {
                val step = idx - 25
                (230 - step * 22).coerceIn(40, 255)
            }
            else -> 0
        }
    }

    /** Essential Nothing indicator: B1 diagonal + E1 dot + subtle A1 strip. */
    fun presetEssential(): IntArray = IntArray(GlyphPowerState.ZONE_COUNT) { idx ->
        when (idx) {
            0 -> 90
            2 -> 255
            24 -> 255
            else -> 0
        }
    }

    fun fillRange(source: IntArray, range: IntRange, level: Int): IntArray {
        val clamped = level.coerceIn(0, 255)
        return IntArray(GlyphFrame.ZONE_COUNT) { idx ->
            if (idx in range) clamped else source.getOrElse(idx) { 0 }.coerceIn(0, 255)
        }
    }

    fun invert(source: IntArray): IntArray = IntArray(GlyphFrame.ZONE_COUNT) { idx ->
        (255 - source.getOrElse(idx) { 0 }).coerceIn(0, 255)
    }

    fun scale(source: IntArray, factor: Float): IntArray = IntArray(GlyphFrame.ZONE_COUNT) { idx ->
        (source.getOrElse(idx) { 0 } * factor).roundToInt().coerceIn(0, 255)
    }

    fun telemetry(snapshot: GlyphSnapshot): GlyphTelemetry = telemetry(snapshot.toIntArray())

    fun telemetry(frame: IntArray): GlyphTelemetry {
        if (frame.isEmpty()) {
            return GlyphTelemetry(0, 0, 0, 0f, 0f, 0f)
        }
        var active = 0
        var sum = 0
        var peak = 0
        for (v in frame) {
            val c = v.coerceIn(0, 255)
            if (c > 0) active++
            sum += c
            if (c > peak) peak = c
        }
        val meanPercent = ((sum.toFloat() / (frame.size * 255f)) * 100f).roundToInt().coerceIn(0, 100)

        fun avgRatio(range: IntRange): Float {
            var s = 0
            var count = 0
            for (i in range) {
                if (i in frame.indices) {
                    s += frame[i].coerceIn(0, 255)
                    count++
                }
            }
            return if (count == 0) 0f else (s.toFloat() / (count * 255f)).coerceIn(0f, 1f)
        }

        return GlyphTelemetry(
            activeZones = active,
            meanPercent = meanPercent,
            peakLevel = peak,
            cameraRatio = avgRatio(0..2),
            ringRatio = avgRatio(3..23),
            spineRatio = avgRatio(24..32),
        )
    }
}

/**
 * Editable scene vault separate from the frame currently applied to the LEDs.
 * Slot 0 maps directly to the legacy "custom_frame" key so existing installs keep their saved scene.
 */
object GlyphSceneStore {
    private const val PREFS = "glyph_scenes"
    private const val KEY_CUSTOM = "custom_frame"
    const val SLOT_COUNT = 4

    private fun slotKey(slot: Int): String {
        require(slot in 0 until SLOT_COUNT) { "Invalid scene slot: $slot" }
        return if (slot == 0) KEY_CUSTOM else "${KEY_CUSTOM}_$slot"
    }

    @SuppressLint("UseKtx") // The Boolean commit result is surfaced to the UI; edit{} returns Unit.
    fun save(context: Context, frame: IntArray): Boolean = saveSlot(context, 0, frame)

    fun load(context: Context): IntArray? = loadSlot(context, 0)

    @SuppressLint("UseKtx")
    fun saveSlot(context: Context, slot: Int, frame: IntArray): Boolean {
        if (slot !in 0 until SLOT_COUNT || !GlyphFrame.valid(frame)) return false
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(slotKey(slot), frame.joinToString(","))
            .commit()
    }

    @SuppressLint("UseKtx")
    fun clearSlot(context: Context, slot: Int): Boolean {
        if (slot !in 0 until SLOT_COUNT) return false
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(slotKey(slot))
            .commit()
    }

    fun loadSlot(context: Context, slot: Int): IntArray? {
        if (slot !in 0 until SLOT_COUNT) return null
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(slotKey(slot), null) ?: return null
        return GlyphFrame.decode(saved)
    }

    fun loadAllSnapshots(context: Context): List<GlyphSnapshot?> =
        List(SLOT_COUNT) { slot -> loadSlot(context, slot)?.let(GlyphSnapshot::from) }
}
