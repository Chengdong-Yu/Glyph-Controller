package com.richard.glyphcontroller

import androidx.compose.runtime.Immutable

/**
 * Compose-stable, structurally-equatable snapshot of a 33-zone brightness frame (0..255).
 * Eliminates unnecessary recompositions caused by JVM IntArray reference equality.
 */
@Immutable
class GlyphSnapshot private constructor(private val values: IntArray) {
    val size: Int get() = values.size

    operator fun get(index: Int): Int = values.getOrElse(index) { 0 }

    val anyLit: Boolean = values.any { it > 0 }
    val allMax: Boolean = values.all { it == 255 }
    val allOff: Boolean = !anyLit
    val activeCount: Int = values.count { it > 0 }

    fun toIntArray(): IntArray = values.copyOf()

    fun contentEquals(other: IntArray): Boolean = values.contentEquals(other)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GlyphSnapshot) return false
        return values.contentEquals(other.values)
    }

    override fun hashCode(): Int = values.contentHashCode()

    companion object {
        val Zero: GlyphSnapshot = GlyphSnapshot(IntArray(GlyphFrame.ZONE_COUNT))
        val Full: GlyphSnapshot = GlyphSnapshot(IntArray(GlyphFrame.ZONE_COUNT) { 255 })

        fun from(source: IntArray): GlyphSnapshot {
            val copy = IntArray(GlyphFrame.ZONE_COUNT) { idx ->
                source.getOrElse(idx) { 0 }.coerceIn(0, 255)
            }
            return when {
                copy.all { it == 0 } -> Zero
                copy.all { it == 255 } -> Full
                else -> GlyphSnapshot(copy)
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
