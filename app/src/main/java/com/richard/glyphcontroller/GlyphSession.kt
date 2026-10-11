package com.richard.glyphcontroller

/** Pure ownership/recovery logic. The coordinator serializes all access. */
class GlyphSession(private val write: (frame: IntArray, sync: Boolean) -> Boolean) {
    enum class Effect(val priority: Int) {
        NOTIFICATION(1),
        CHARGING(2),
        VOLUME(3)
    }

    // Each lease is identified by object identity; no separate sequence number is needed.
    class Lease internal constructor(val effect: Effect)
    private var owner: Lease? = null
    private var restored = false

    fun restore(frame: IntArray): Boolean {
        if (!restored && owner == null) restored = write(frame, true)
        return restored
    }

    fun manual(frame: IntArray, previous: IntArray, save: (IntArray) -> Boolean): Boolean {
        if (!GlyphFrame.valid(frame)) return false
        owner = null
        if (write(frame, true) && save(frame)) {
            restored = true
            return true
        }
        restored = write(previous, true)
        return false
    }

    fun begin(effect: Effect, fallback: IntArray): Lease? {
        if (owner?.effect?.priority?.let { it >= effect.priority } == true) return null
        if (!restore(fallback)) return null
        return Lease(effect).also { owner = it }
    }

    fun isCurrentOwner(lease: Lease?): Boolean = lease != null && owner === lease

    fun render(lease: Lease, frame: IntArray, sync: Boolean = false): Boolean {
        if (owner !== lease || !GlyphFrame.valid(frame)) return false
        return write(frame, sync).also { if (!it) restored = false }
    }

    fun finish(lease: Lease, fallback: IntArray): Boolean {
        if (owner !== lease) return true
        owner = null
        restored = write(fallback, true)
        return restored
    }
}
