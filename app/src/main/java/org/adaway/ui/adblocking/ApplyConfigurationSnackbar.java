package org.adaway.ui.adblocking;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;

import com.google.android.material.snackbar.Snackbar;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.source.SourceModel;
import org.adaway.util.AppExecutors;

import java.util.Collection;

import static com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE;
import static com.google.android.material.snackbar.Snackbar.LENGTH_LONG;

/**
 * This class is a {@link Snackbar} to notify about adblock model new configuration to apply.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class ApplyConfigurationSnackbar {
    /**
     * The view to bind the snackbar to.
     */
    private final View view;
    /**
     * The notify snackbar when hosts update available.
     */
    private final Snackbar notifySnackbar;
    /**
     * The wait snackbar during hosts install.
     */
    private final Snackbar waitSnackbar;
    /**
     * To synchronize sources before installing or not.
     */
    private final boolean syncSources;
    /**
     * The current hosts update available status ({@code true} if update available, {@code false} otherwise).
     */
    private boolean update;
    /**
     * Whether or not ignore the next update event ({@code true} to ignore, {@code false} otherwise).
     */
    private boolean skipUpdate;
    /**
     * Whether or not ignore update events during the install ({@code true} to ignore, {@code false} otherwise).
     */
    private boolean ignoreEventDuringInstall;

    /**
     * Constructor.
     *
     * @param view                     The view to bind the snackbar to.
     * @param syncSources              To synchronize sources before installing or not.
     * @param ignoreEventDuringInstall {@code true} to ignore events, {@code false} otherwise.
     */
    public ApplyConfigurationSnackbar(@NonNull View view, boolean syncSources, boolean ignoreEventDuringInstall) {
        this.view = view;
        this.notifySnackbar = Snackbar.make(this.view, R.string.notification_configuration_changed, LENGTH_INDEFINITE)
                .setAction(R.string.notification_configuration_changed_action, v -> apply());
        this.waitSnackbar = Snackbar.make(this.view, R.string.notification_configuration_installing, LENGTH_INDEFINITE);
        appendViewToSnackbar(this.waitSnackbar, new ProgressBar(this.view.getContext()));
        this.syncSources = syncSources;
        this.ignoreEventDuringInstall = ignoreEventDuringInstall;
        this.update = false;
        this.skipUpdate = false;
    }

    /**
     * Create {@link Observer} which ignores first (initialization) event.
     *
     * @param <T> The type of data to observe.
     * @return The observer instance.
     */
    public <T> Observer<T> createObserver() {
        return new Observer<T>() {
            boolean firstUpdate = true;

            @Override
            public void onChanged(@Nullable T t) {
                // Check new data
                if (t == null || (t instanceof Collection && ((Collection<?>) t).isEmpty())) {
                    return;
                }
                // First update
                if (this.firstUpdate) {
                    this.firstUpdate = false;
                    return;
                }
                ApplyConfigurationSnackbar.this.notifyUpdateAvailable();
            }
        };
    }

    /**
     * Notify update available.
     */
    public void notifyUpdateAvailable() {
        // Check if notify snackbar is already displayed
        if (this.notifySnackbar.isShown()) {
            return;
        }
        // Check if wait snackbar is displayed
        if (this.waitSnackbar.isShown()) {
            // Mark update available
            this.update = true;
            return;
        }
        // Check if update event should be skipped
        if (this.skipUpdate) {
            this.skipUpdate = false;
            return;
        }
        // Show notify snackbar
        this.notifySnackbar.show();
        // Mark update as notified
        this.update = false;
    }

    private void apply() {
        showLoading();
        AppExecutors.getInstance().diskIO().execute(() -> {
            AdAwayApplication application = (AdAwayApplication) this.view.getContext().getApplicationContext();
            SourceModel sourceModel = application.getSourceModel();
            AdBlockModel adBlockModel = application.getAdBlockModel();
            try {
                if (this.syncSources) {
                    sourceModel.retrieveHostsSources();
                } else {
                    sourceModel.syncHostEntries();
                }
                adBlockModel.apply();
                endLoading(true);
            } catch (HostErrorException exception) {
                endLoading(false);
            }
        });
    }

    private void showLoading() {
        // Clear notify snackbar
        this.notifySnackbar.dismiss();
        // Show wait snackbar
        this.waitSnackbar.show();
    }

    private void endLoading(boolean successfulInstall) {
        // Ensure the snackbar has time to display
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Clear snackbars
        this.waitSnackbar.dismiss();
        // Check install failure
        if (!successfulInstall) {
            Snackbar failureSnackbar = Snackbar.make(this.view, R.string.notification_configuration_failed, LENGTH_LONG);
            ImageView view = new ImageView(this.view.getContext());
            view.setImageResource(R.drawable.ic_error_outline_24dp);
            appendViewToSnackbar(failureSnackbar, view);
            failureSnackbar.show();
        }
        // Check pending update notification
        else if (this.update) {
            // Ignore next update event if events should be ignored
            if (this.ignoreEventDuringInstall) {
                this.skipUpdate = true;
            } else {
                // Otherwise display update notification
                notifyUpdateAvailable();
            }
        }
    }

    private void appendViewToSnackbar(Snackbar snackbar, View view) {
        ViewGroup viewGroup = (ViewGroup) snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text).getParent();
        viewGroup.addView(view);
    }
}
