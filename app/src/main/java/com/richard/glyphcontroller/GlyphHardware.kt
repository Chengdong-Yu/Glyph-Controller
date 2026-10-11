package com.richard.glyphcontroller

/** Only a completed root probe can establish that hardware is absent. */
object GlyphHardware {
    enum class Status { READY, NO_ROOT, UNSUPPORTED, CHECK_FAILED }
    private const val BASE = "/sys/class/leds/led_strips"

    fun probe(client: RootGlyphClient): Status {
        if (!client.isRootAvailable()) {
            return if (client.rootCheckTimedOut) Status.CHECK_FAILED else Status.NO_ROOT
        }
        return parseProbe(client.shell(
            "if [ ! -e '$BASE/operating_mode' ] || [ ! -e '$BASE/frame_brightness' ]; then " +
                "echo GLYPH_MISSING; " +
                "elif [ -w '$BASE/operating_mode' ] && [ -w '$BASE/frame_brightness' ]; then " +
                "echo GLYPH_READY; else echo GLYPH_UNWRITABLE; fi"
        ))
    }

    private fun parseProbe(output: String?): Status = when (output?.trim()) {
        "GLYPH_READY" -> Status.READY
        "GLYPH_MISSING" -> Status.UNSUPPORTED
        else -> Status.CHECK_FAILED
    }
}
