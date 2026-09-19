package com.richard.glyphcontroller

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile: one tap toggles all Glyph lights.
 * State is persisted so the tile reflects the last action across restarts.
 */
class GlyphTileService : TileService() {

    override fun onStartListening() {
        updateTile()
    }

    override fun onClick() {
        val on = isOn()
        var success = false
        val client = RootGlyphClient()
        try {
            if (client.isRootAvailable()) {
                if (on) {
                    client.turnOff()
                } else {
                    client.turnOnAll()
                }
                success = true
            }
        } finally {
            client.destroy()
        }
        if (success) {
            setOn(!on)
        }
        updateTile()
    }

    private fun isOn(): Boolean =
        getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ON, false)

    private fun setOn(on: Boolean) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ON, on).apply()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val on = isOn()
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = getString(if (on) R.string.tile_subtitle_on else R.string.tile_subtitle_off)
        tile.updateTile()
    }

    companion object {
        private const val PREFS = "glyph_state"
        private const val KEY_ON = "all_on"
    }
}
