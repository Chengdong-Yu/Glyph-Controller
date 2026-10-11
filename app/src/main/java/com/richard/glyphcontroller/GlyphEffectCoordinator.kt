package com.richard.glyphcontroller

import android.content.Context
import android.util.Log

/** One serialized owner of hardware writes and the persisted manual frame. */
object GlyphEffectCoordinator {
    private val lock = Any()
    private val client = RootGlyphClient()
    private val session = GlyphSession { frame, sync ->
        if (frame.any { it > 0 }) client.setZoneColors(frame, sync) else client.turnOff()
    }

    class Readiness(val hardware: GlyphHardware.Status, val restored: Boolean) {
        val ready: Boolean get() = hardware == GlyphHardware.Status.READY && restored
    }

    fun checkAndRestore(context: Context): Readiness = synchronized(lock) {
        val status = GlyphHardware.probe(client)
        Readiness(status, status == GlyphHardware.Status.READY && session.restore(GlyphPowerState.getFrame(context)))
    }

    fun toggleManual(context: Context): Boolean = synchronized(lock) {
        val on = GlyphPowerState.isOn(context)
        applyManualFrame(context, IntArray(GlyphFrame.ZONE_COUNT) { if (on) 0 else 255 })
    }

    fun applyManualFrame(context: Context, frame: IntArray): Boolean = synchronized(lock) {
        if (!GlyphFrame.valid(frame) || !hardwareReady()) return@synchronized false
        session.manual(frame, GlyphPowerState.getFrame(context)) { GlyphPowerState.setFrame(context, it) }
    }

    fun beginEffect(context: Context, effect: GlyphSession.Effect): GlyphSession.Lease? = synchronized(lock) {
        if (!hardwareReady()) return@synchronized null
        session.begin(effect, GlyphPowerState.getFrame(context))
    }

    fun isCurrentOwner(lease: GlyphSession.Lease?): Boolean = synchronized(lock) {
        session.isCurrentOwner(lease)
    }

    fun renderFrame(lease: GlyphSession.Lease, frame: IntArray, sync: Boolean = false): Boolean = synchronized(lock) {
        session.render(lease, frame, sync)
    }

    fun finishEffect(context: Context, lease: GlyphSession.Lease) = synchronized(lock) {
        if (!session.finish(lease, GlyphPowerState.getFrame(context))) {
            Log.e("GlyphEffects", "Unable to restore frame after ${lease.effect}")
        }
    }

    private fun hardwareReady() = GlyphHardware.probe(client) == GlyphHardware.Status.READY
}
