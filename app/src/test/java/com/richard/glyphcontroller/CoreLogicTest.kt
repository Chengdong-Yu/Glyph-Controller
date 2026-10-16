package com.richard.glyphcontroller

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
}
