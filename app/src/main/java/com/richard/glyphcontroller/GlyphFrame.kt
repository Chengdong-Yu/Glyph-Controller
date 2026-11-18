package com.richard.glyphcontroller

import androidx.compose.runtime.Immutable

/**
 * Compose-stable, structurally-equatable snapshot of a 33-zone brightness frame (0..255).
 * Eliminates unnecessary recompositions caused by JVM IntArray reference equality.
 */
@Immutable
class GlyphSnapshot private constructor(
    private val values: IntArray,
    val activeCount: Int,
    val allMax: Boolean,
) {
    val size: Int get() = values.size

    operator fun get(index: Int): Int = values.getOrElse(index) { 0 }

    val anyLit: Boolean get() = activeCount > 0
    val allOff: Boolean get() = activeCount == 0

    /**
     * Fast single-zone immutable update that avoids cloning or rescanning when [level] is unchanged.
     */
    fun withLevel(index: Int, level: Int): GlyphSnapshot {
        if (index !in 0 until GlyphFrame.ZONE_COUNT) return this
        val clamped = level.coerceIn(0, 255)
        if (values[index] == clamped) return this
        val copy = values.copyOf()
        copy[index] = clamped
        return buildOwned(copy)
    }

    /**
     * Fast cluster-range immutable update that avoids cloning when every zone in [range] already equals [level].
     */
    fun withRange(range: IntRange, level: Int): GlyphSnapshot {
        val clamped = level.coerceIn(0, 255)
        val first = range.first.coerceAtLeast(0)
        val last = range.last.coerceAtMost(GlyphFrame.ZONE_COUNT - 1)
        if (first > last) return this
        var changed = false
        for (idx in first..last) {
            if (values[idx] != clamped) {
                changed = true
                break
            }
        }
        if (!changed) return this
        val copy = values.copyOf()
        for (idx in first..last) {
            copy[idx] = clamped
        }
        return buildOwned(copy)
    }

    fun toIntArray(): IntArray = values.copyOf()

    fun contentEquals(other: IntArray): Boolean = values.contentEquals(other)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GlyphSnapshot) return false
        if (activeCount != other.activeCount || allMax != other.allMax) return false
        return values.contentEquals(other.values)
    }

    override fun hashCode(): Int = values.contentHashCode()

    companion object {
        val Zero: GlyphSnapshot = GlyphSnapshot(
            values = IntArray(GlyphFrame.ZONE_COUNT),
            activeCount = 0,
            allMax = false,
        )
        val Full: GlyphSnapshot = GlyphSnapshot(
            values = IntArray(GlyphFrame.ZONE_COUNT) { 255 },
            activeCount = GlyphFrame.ZONE_COUNT,
            allMax = true,
        )

        fun from(source: IntArray): GlyphSnapshot {
            val copy = IntArray(GlyphFrame.ZONE_COUNT)
            var active = 0
            var maxCount = 0
            for (idx in 0 until GlyphFrame.ZONE_COUNT) {
                val clamped = source.getOrElse(idx) { 0 }.coerceIn(0, 255)
                copy[idx] = clamped
                if (clamped > 0) {
                    active++
                    if (clamped == 255) maxCount++
                }
            }
            return when {
                active == 0 -> Zero
                maxCount == GlyphFrame.ZONE_COUNT -> Full
                else -> GlyphSnapshot(copy, activeCount = active, allMax = false)
            }
        }

        private fun buildOwned(owned: IntArray): GlyphSnapshot {
            var active = 0
            var maxCount = 0
            for (v in owned) {
                if (v > 0) {
                    active++
                    if (v == 255) maxCount++
                }
            }
            return when {
                active == 0 -> Zero
                maxCount == GlyphFrame.ZONE_COUNT -> Full
                else -> GlyphSnapshot(owned, activeCount = active, allMax = false)
            }
        }
    }
}

/** Shared format for manual frames, named scenes and restored UI drafts. */
object GlyphFrame {
    const val ZONE_COUNT = 33

    fun valid(frame: IntArray?): Boolean =
        frame != null && frame.size == ZONE_COUNT && frame.all { it in 0..255 }

    fun decode(value: String?): IntArray? {
        val parts = value?.split(',') ?: return null
        if (parts.size != ZONE_COUNT) return null
        val frame = IntArray(ZONE_COUNT)
        for (index in parts.indices) {
            frame[index] = parts[index].toIntOrNull() ?: return null
        }
        return frame.takeIf(::valid)
    }
}
