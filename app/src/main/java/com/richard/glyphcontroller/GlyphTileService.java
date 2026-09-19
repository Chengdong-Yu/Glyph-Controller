package com.richard.glyphcontroller;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * Quick Settings tile: one tap toggles all Glyph lights.
 * State is persisted so the tile reflects the last action across restarts.
 */
public class GlyphTileService extends TileService {
    private static final String PREFS = "glyph_state";
    private static final String KEY_ON = "all_on";

    @Override
    public void onStartListening() {
        updateTile();
    }

    @Override
    public void onClick() {
        boolean on = isOn();
        boolean success = false;
        RootGlyphClient client = new RootGlyphClient();
        try {
            if (client.isRootAvailable()) {
                if (on) {
                    client.turnOff();
                } else {
                    client.turnOnAll();
                }
                success = true;
            }
        } finally {
            client.destroy();
        }
        if (success) {
            setOn(!on);
        }
        updateTile();
    }

    private boolean isOn() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ON, false);
    }

    private void setOn(boolean on) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ON, on).apply();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean on = isOn();
        tile.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setSubtitle(getString(on ? R.string.tile_subtitle_on : R.string.tile_subtitle_off));
        tile.updateTile();
    }
}
