package com.stfc.overlay.service

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.stfc.overlay.MainActivity

/**
 * Android Quick Settings Tile ("STFC DB") that lets players toggle the floating
 * STFC Database bubble directly from the pull-down notification shade while in-game.
 */
class StfcTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        syncTileState()
    }

    override fun onClick() {
        super.onClick()
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivityAndCollapse(intent)
            return
        }

        if (OverlayService.isRunning) {
            stopService(Intent(this, OverlayService::class.java))
        } else {
            val serviceIntent = Intent(this, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
        syncTileState()
    }

    private fun syncTileState() {
        val tile = qsTile ?: return
        tile.state = if (OverlayService.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "STFC DB"
        tile.updateTile()
    }
}
