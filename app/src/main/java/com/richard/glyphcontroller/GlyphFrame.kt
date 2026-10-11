package com.richard.glyphcontroller

/** Shared format for manual frames, named scenes and restored UI drafts. */
object GlyphFrame {
    const val ZONE_COUNT = 33
    fun valid(frame: IntArray?) = frame != null && frame.size == ZONE_COUNT && frame.all { it in 0..255 }
    fun decode(value: String?): IntArray? {
        val parts = value?.split(',') ?: return null
        if (parts.size != ZONE_COUNT) return null
        val frame = IntArray(ZONE_COUNT)
        for (index in parts.indices) frame[index] = parts[index].toIntOrNull() ?: return null
        return frame.takeIf(::valid)
    }
}
