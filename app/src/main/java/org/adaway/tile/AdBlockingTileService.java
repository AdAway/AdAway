package org.adaway.tile;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.lifecycle.LiveData;

import org.adaway.AdAwayApplication;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.error.HostErrorException;
import org.adaway.util.AppExecutors;

import java.util.concurrent.atomic.AtomicBoolean;

import static android.service.quicksettings.Tile.STATE_ACTIVE;
import static android.service.quicksettings.Tile.STATE_INACTIVE;

import timber.log.Timber;

/**
 * This class is a {@link TileService} to toggle ad-blocking.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class AdBlockingTileService extends TileService {
    private final AtomicBoolean toggling = new AtomicBoolean(false);

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
        // The tile is null when the system has unbound the service (it may happen between
        // a click that triggered an async toggle and the toggle completing).
        if (tile == null) {
            return;
        }
        tile.setState(adBlocked ? STATE_ACTIVE : STATE_INACTIVE);
        tile.updateTile();
    }

    private void toggleAdBlocking() {
        if (this.toggling.get()) {
            return;
        }
        AdBlockModel model = getModel();
        boolean wasApplied = model.isApplied().getValue() == Boolean.TRUE;
        try {
            this.toggling.set(true);
            Timber.i("Tile: user toggle (currently %s).", wasApplied ? "ACTIVE" : "INACTIVE");
            if (wasApplied) {
                model.revert();
            } else {
                model.apply();
            }
        } catch (HostErrorException e) {
            Timber.w(e, "Failed to toggle ad-blocking.");
        } finally {
            this.toggling.set(false);
            // Whatever happened (success, failure, exception), force the tile back in
            // sync with the LiveData so a failed start doesn't leave the tile stuck on
            // an optimistic "ACTIVE" state.
            updateTile(model.isApplied().getValue() == Boolean.TRUE);
        }
    }

    private AdBlockModel getModel() {
        return ((AdAwayApplication) getApplication()).getAdBlockModel();
    }
}
