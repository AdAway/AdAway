package org.adaway.tile

import android.service.quicksettings.Tile.STATE_ACTIVE
import android.service.quicksettings.Tile.STATE_INACTIVE
import android.service.quicksettings.TileService
import org.adaway.AdAwayApplication
import org.adaway.model.adblocking.AdBlockModel
import org.adaway.model.error.HostErrorException
import org.adaway.util.CoroutineDispatchers
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

class AdBlockingTileService : TileService() {
    private val toggling = AtomicBoolean(false)

    override fun onTileAdded() {
        updateTile(model.isApplied.value == true)
    }

    override fun onStartListening() {
        model.isApplied.observeForever(::updateTile)
    }

    override fun onStopListening() {
        model.isApplied.removeObserver(::updateTile)
    }

    override fun onClick() {
        CoroutineDispatchers.ioExecutor().execute(::toggleAdBlocking)
    }

    private fun updateTile(adBlocked: Boolean) {
        qsTile?.let { tile ->
            tile.state = if (adBlocked) STATE_ACTIVE else STATE_INACTIVE
            tile.updateTile()
        }
    }

    private fun toggleAdBlocking() {
        if (toggling.get()) {
            return
        }
        val model = model
        try {
            toggling.set(true)
            if (model.isApplied.value == true) {
                model.revert()
            } else {
                model.apply()
            }
        } catch (exception: HostErrorException) {
            Timber.w(exception, "Failed to toggle ad-blocking.")
        } finally {
            toggling.set(false)
        }
    }

    private val model: AdBlockModel
        get() = (application as AdAwayApplication).adBlockModel
}
