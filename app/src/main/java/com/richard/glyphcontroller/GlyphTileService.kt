package com.richard.glyphcontroller

import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

/**
 * Quick Settings tile: one tap toggles all Glyph lights.
 * State is persisted so the tile reflects the last action across restarts.
 */
class GlyphTileService : TileService() {
    private var mToggling = false

    override fun onStartListening() {
        updateTile()
    }

    override fun onClick() {
        if (mToggling) return
        mToggling = true
        Thread {
            try {
                if (!GlyphEffectCoordinator.toggleManual(applicationContext)) {
                    Log.w("GlyphTile", "Glyph toggle was not applied")
                }
            } catch (e: Exception) {
                Log.e("GlyphTile", "Failed to toggle glyphs", e)
            }
            Handler(Looper.getMainLooper()).post {
                updateTile()
                mToggling = false
            }
        }.start()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val frame = GlyphPowerState.getFrame(this)
        val on = frame.any { it > 0 }
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = getString(
            when {
                !on -> R.string.tile_subtitle_off
                frame.all { it == 255 } -> R.string.tile_subtitle_on
                else -> R.string.tile_subtitle_custom
            }
        )
        tile.updateTile()
    }
}
