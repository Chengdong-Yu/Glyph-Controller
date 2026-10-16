package com.richard.glyphcontroller

import org.junit.Assert.assertEquals
import org.junit.Test

class GlyphVolumeCoordinatorTest {
    @Test
    fun parsesVolumeKeyNodeFrom64BitKernelBitmap() {
        val devices = """
            I: Bus=0019 Vendor=0000 Product=0001 Version=0000
            H: Handlers=kbd event2
            B: KEY=8000000000000 0

            I: Bus=0019 Vendor=0000 Product=0002 Version=0000
            H: Handlers=kbd event7
            B: KEY=1 0
        """.trimIndent()

        assertEquals(
            listOf("/dev/input/event2"),
            GlyphVolumeCoordinator.parseVolumeKeyNodes(devices, wordBits = 64),
        )
    }

    @Test
    fun parsesVolumeKeyNodeFrom32BitKernelBitmap() {
        val devices = """
            I: Bus=0019 Vendor=0000 Product=0001 Version=0000
            H: Handlers=event4
            B: KEY=80000 00000000 00000000 00000000
        """.trimIndent()

        assertEquals(
            listOf("/dev/input/event4"),
            GlyphVolumeCoordinator.parseVolumeKeyNodes(devices, wordBits = 32),
        )
    }
}
