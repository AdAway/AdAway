package org.adaway.tile;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

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
    private final Observer<Boolean> appliedObserver = applied -> updateTile(Boolean.TRUE.equals(applied));
    private LiveData<Boolean> observedApplied;

    @Override
    public void onTileAdded() {
        boolean adBlocked = getModel().isApplied().getValue() == Boolean.TRUE;
        updateTile(adBlocked);
    }

    @Override
    public void onStartListening() {
        stopObserving();
        this.observedApplied = getModel().isApplied();
        this.observedApplied.observeForever(this.appliedObserver);
    }

    @Override
    public void onStopListening() {
        stopObserving();
    }

    @Override
    public void onDestroy() {
        stopObserving();
        super.onDestroy();
    }

    private void stopObserving() {
        if (this.observedApplied != null) {
            this.observedApplied.removeObserver(this.appliedObserver);
            this.observedApplied = null;
        }
    }

    @Override
    public void onClick() {
        AppExecutors.getInstance()
                .diskIO()
                .execute(this::toggleAdBlocking);
    }

    private void updateTile(boolean adBlocked) {
        Tile tile = getQsTile();
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
        try {
            this.toggling.set(true);
            if (model.isApplied().getValue() == Boolean.TRUE) {
                model.revert();
            } else {
                model.apply();
            }
        } catch (HostErrorException e) {
            Timber.w(e, "Failed to toggle ad-blocking.");
        } finally {
            this.toggling.set(false);
        }
    }

    AdBlockModel getModel() {
        return ((AdAwayApplication) getApplication()).getAdBlockModel();
    }
}
