package org.adaway.tile;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.lifecycle.LiveData;

import org.adaway.AdAwayApplication;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.error.HostErrorException;
import org.adaway.util.AppExecutors;
import org.adaway.util.Log;

import static android.service.quicksettings.Tile.STATE_ACTIVE;
import static android.service.quicksettings.Tile.STATE_INACTIVE;

/**
 * This class is a {@link TileService} to toggle ad-blocking.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class AdBlockingTileService extends TileService {
    private static final String TAG = "TileService";

    @Override
    public void onTileAdded() {
        boolean adBlocked = getModel().isApplied().getValue() == Boolean.TRUE;
        updateTile(adBlocked);
    }

    @Override
    public void onStartListening() {
        LiveData<Boolean> applied = getModel().isApplied();
        applied.observeForever(this::updateTile);
    }

    @Override
    public void onStopListening() {
        LiveData<Boolean> applied = getModel().isApplied();
        applied.removeObserver(this::updateTile);
    }

    @Override
    public void onClick() {
        AppExecutors.getInstance()
                .diskIO()
                .execute(this::toggleAdBlocking);
    }

    private void updateTile(boolean adBlocked) {
        Tile tile = getQsTile();
        tile.setState(adBlocked ? STATE_ACTIVE : STATE_INACTIVE);
        tile.updateTile();
    }

    private void toggleAdBlocking() {
        AdBlockModel model = getModel();
        try {
            if (model.isApplied().getValue() == Boolean.TRUE) {
                model.revert();
            } else {
                model.apply();
            }
        } catch (HostErrorException e) {
            Log.w(TAG, "Failed to toggle ad-blocking.", e);
        }
    }

    private AdBlockModel getModel() {
        return ((AdAwayApplication) getApplication()).getAdBlockModel();
    }
}
