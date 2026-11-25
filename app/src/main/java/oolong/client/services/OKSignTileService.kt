package oolong.client.services

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import oolong.client.util.TiebaUtil

/**
 * The quick settings tile that starts signing in to every followed forum.
 *
 * The system remembers an added tile by this class's name, so it stays in this package: moving or
 * renaming it would remove the tile from every user's panel.
 */
class OKSignTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        showInactive()
    }

    override fun onStopListening() {
        super.onStopListening()
        showInactive()
    }

    override fun onClick() {
        super.onClick()
        TiebaUtil.startSign(this)
    }

    private fun showInactive() {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
