package com.richard.glyphcontroller

/** A UI draft has its own revision; an older apply cannot confirm newer edits. */
class GlyphDraft(initial: IntArray, initialRevision: Long = 0) {
    var frame = initial.copyOf()
        private set
    var revision = initialRevision
        private set
    class Snapshot(val frame: IntArray, val revision: Long)

    fun setLevel(index: Int, value: Int) {
        val level = value.coerceIn(0, 255)
        if (frame[index] == level) return
        frame[index] = level
        revision++
    }

    fun replace(values: IntArray) {
        require(values.size == frame.size && values.all { it in 0..255 })
        if (frame.contentEquals(values)) return
        frame = values.copyOf()
        revision++
    }

    fun snapshot() = Snapshot(frame.copyOf(), revision)
    fun matches(snapshot: Snapshot) = revision == snapshot.revision && frame.contentEquals(snapshot.frame)
}
