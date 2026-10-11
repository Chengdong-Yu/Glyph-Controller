package com.richard.glyphcontroller

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick Settings tile: one tap toggles all Glyph lights.
 * State is persisted so the tile reflects the last action across restarts.
 */
class GlyphTileService : TileService() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var toggling = false

    override fun onStartListening() {
        updateTile()
    }

    override fun onClick() {
        if (toggling) return
        toggling = true
        scope.launch {
            try {
                val success = withContext(Dispatchers.Default) {
                    GlyphEffectCoordinator.toggleManual(applicationContext)
                }
                if (!success) {
                    Log.w("GlyphTile", "Glyph toggle was not applied")
                }
            } catch (e: Exception) {
                Log.e("GlyphTile", "Failed to toggle glyphs", e)
            } finally {
                updateTile()
                toggling = false
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
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
