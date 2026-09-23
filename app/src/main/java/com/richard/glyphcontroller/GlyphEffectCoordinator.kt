package com.richard.glyphcontroller

import android.content.Context
import android.util.Log

/** One owner for LED writes, shared by the UI, Quick Settings, and transient effects. */
object GlyphEffectCoordinator {
    enum class Effect(val priority: Int) {
        NOTIFICATION(1),
        CHARGING(2)
    }

    class Lease internal constructor(val id: Long, val effect: Effect)

    private val lock = Any()
    private val client = RootGlyphClient()
    private var nextLeaseId = 0L
    private var activeLease: Lease? = null

    /** A manual tap takes precedence over every animation. Persist only confirmed writes. */
    fun toggleManual(context: Context): Boolean = synchronized(lock) {
        val wasOn = GlyphPowerState.isOn(context)
        applyManualFrame(context, IntArray(GlyphPowerState.ZONE_COUNT) { if (wasOn) 0 else 255 })
    }

    /** A detailed frame takes precedence over transient effects and becomes their restore target. */
    fun applyManualFrame(context: Context, frame: IntArray): Boolean = synchronized(lock) {
        if (frame.size != GlyphPowerState.ZONE_COUNT || frame.any { it !in 0..255 }) return@synchronized false
        if (!hardwareReady()) return@synchronized false

        val previous = GlyphPowerState.getFrame(context)
        activeLease = null
        if (applyFrame(frame) && GlyphPowerState.setFrame(context, frame)) {
            return@synchronized true
        }
        // A failed write or state save must not leave a cancelled effect visible.
        if (!applyFrame(previous)) Log.e(TAG, "Unable to restore the previous Glyph frame")
        false
    }

    fun beginEffect(effect: Effect): Lease? = synchronized(lock) {
        val current = activeLease
        if (current != null && current.effect.priority >= effect.priority) return@synchronized null
        if (!hardwareReady()) return@synchronized null

        Lease(++nextLeaseId, effect).also { activeLease = it }
    }

    /** False means the effect lost ownership or the hardware rejected the frame. */
    fun renderFrame(lease: Lease, frame: IntArray): Boolean = synchronized(lock) {
        if (activeLease?.id != lease.id) return@synchronized false
        client.setZoneColors(frame)
    }

    /** Restore the user's manual state only if this effect still owns the LEDs. */
    fun finishEffect(context: Context, lease: Lease) = synchronized(lock) {
        if (activeLease?.id != lease.id) return@synchronized
        activeLease = null
        if (!applyFrame(GlyphPowerState.getFrame(context))) {
            Log.e(TAG, "Unable to restore Glyph frame after ${lease.effect}")
        }
    }

    private fun hardwareReady(): Boolean =
        client.isRootAvailable() && GlyphHardware.isSupported(rootAvailable = true) == true

    private fun applyFrame(frame: IntArray): Boolean =
        if (frame.any { it > 0 }) client.setZoneColors(frame) else client.turnOff()

    private const val TAG = "GlyphEffects"
}
