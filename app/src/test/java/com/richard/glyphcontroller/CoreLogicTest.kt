package com.richard.glyphcontroller

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLogicTest {
    @Test
    fun frameDecodeRejectsMalformedOrOutOfRangeValues() {
        val valid = IntArray(GlyphFrame.ZONE_COUNT) { it.coerceAtMost(255) }
        assertArrayEquals(valid, GlyphFrame.decode(valid.joinToString(",")))
        assertNull(GlyphFrame.decode("1,2,3"))
        assertNull(GlyphFrame.decode(IntArray(GlyphFrame.ZONE_COUNT) { 256 }.joinToString(",")))
    }

    @Test
    fun glyphSnapshotProvidesStructuralEqualityAndDefensiveImmutability() {
        val raw = IntArray(GlyphFrame.ZONE_COUNT) { if (it % 2 == 0) 140 else 0 }
        val first = GlyphSnapshot.from(raw)
        val second = GlyphSnapshot.from(raw.copyOf())

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertTrue(first.anyLit)
        assertFalse(first.allMax)
        assertFalse(first.allOff)
        assertEquals(17, first.activeCount)

        // Mutating the original or exported array must never mutate the snapshot.
        raw[0] = 255
        assertEquals(140, first[0])
        assertNotEquals(first, GlyphSnapshot.from(raw))

        val exported = first.toIntArray()
        exported[0] = 0
        assertEquals(140, first[0])

        assertSame(GlyphSnapshot.Zero, GlyphSnapshot.from(IntArray(GlyphFrame.ZONE_COUNT)))
        assertSame(GlyphSnapshot.Full, GlyphSnapshot.from(IntArray(GlyphFrame.ZONE_COUNT) { 255 }))

        // No-op updates must return the exact same instance without cloning.
        assertSame(first, first.withLevel(0, 140))
        assertSame(GlyphSnapshot.Zero, first.withRange(0 until GlyphFrame.ZONE_COUNT, 0))
        assertSame(GlyphSnapshot.Full, first.withRange(0 until GlyphFrame.ZONE_COUNT, 255))

        val singleUpdated = GlyphSnapshot.Zero.withLevel(7, 210)
        assertEquals(210, singleUpdated[7])
        assertEquals(1, singleUpdated.activeCount)
        assertTrue(singleUpdated.anyLit)
        assertFalse(singleUpdated.allMax)
        assertSame(GlyphSnapshot.Zero, singleUpdated.withLevel(7, 0))
    }

    @Test
    fun draftSnapshotDoesNotConfirmNewerEdits() {
        val draft = GlyphDraft(IntArray(GlyphFrame.ZONE_COUNT))
        val snapshot = draft.snapshot()
        assertTrue(draft.matches(snapshot))

        draft.setLevel(4, 180)

        assertFalse(draft.matches(snapshot))
        assertEquals(180, draft.frame[4])
    }

    @Test
    fun higherPriorityEffectPreemptsLowerPriorityLease() {
        val writes = mutableListOf<IntArray>()
        val session = GlyphSession { frame, _ ->
            writes += frame.copyOf()
            true
        }
        val fallback = IntArray(GlyphFrame.ZONE_COUNT) { 12 }
        assertTrue(session.restore(fallback))

        val notification = session.begin(GlyphSession.Effect.NOTIFICATION, fallback)
        assertNotNull(notification)
        assertNull(session.begin(GlyphSession.Effect.NOTIFICATION, fallback))

        val volume = session.begin(GlyphSession.Effect.VOLUME, fallback)
        assertNotNull(volume)
        assertFalse(session.render(notification!!, IntArray(GlyphFrame.ZONE_COUNT), false))
        assertTrue(session.render(volume!!, IntArray(GlyphFrame.ZONE_COUNT) { 255 }, false))
        assertTrue(session.finish(volume, fallback))
        assertArrayEquals(fallback, writes.last())
    }

    @Test
    fun failedManualSaveRestoresPreviousFrame() {
        val writes = mutableListOf<IntArray>()
        val session = GlyphSession { frame, _ ->
            writes += frame.copyOf()
            true
        }
        val previous = IntArray(GlyphFrame.ZONE_COUNT) { 25 }
        val requested = IntArray(GlyphFrame.ZONE_COUNT) { 200 }

        assertFalse(session.manual(requested, previous) { false })
        assertArrayEquals(requested, writes.first())
        assertArrayEquals(previous, writes.last())
    }

    @Test
    fun curatedPresetsAndSculptTransformsRemainValidFrames() {
        val starlight = GlyphZones.presetStarlight()
        val essential = GlyphZones.presetEssential()
        assertTrue(GlyphFrame.valid(starlight))
        assertTrue(GlyphFrame.valid(essential))

        val syncedArc = GlyphZones.fillRange(IntArray(GlyphFrame.ZONE_COUNT), GlyphZones.clusterRange(8), 200)
        assertTrue((3..18).all { syncedArc[it] == 200 })
        assertEquals(0, syncedArc[0])
        assertEquals(0, syncedArc[24])

        val inverted = GlyphZones.invert(syncedArc)
        assertEquals(55, inverted[8])
        assertEquals(255, inverted[0])

        val dimmed = GlyphZones.scale(syncedArc, 0.5f)
        assertEquals(100, dimmed[8])
    }

    @Test
    fun telemetryComputesActiveCountMeanAndPeakAccurately() {
        val fullTelemetry = GlyphZones.telemetry(GlyphSnapshot.Full)
        assertEquals(33, fullTelemetry.activeZones)
        assertEquals(100, fullTelemetry.meanPercent)
        assertEquals(255, fullTelemetry.peakLevel)
        assertEquals(1f, fullTelemetry.ringRatio, 0.001f)

        val offTelemetry = GlyphZones.telemetry(GlyphSnapshot.Zero)
        assertEquals(0, offTelemetry.activeZones)
        assertEquals(0, offTelemetry.meanPercent)
        assertEquals(0, offTelemetry.peakLevel)
    }
}
