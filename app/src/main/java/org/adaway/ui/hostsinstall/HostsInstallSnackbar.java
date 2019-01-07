package org.adaway.ui.hostsinstall;

import android.arch.lifecycle.Observer;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import org.adaway.R;
import org.adaway.model.hostsinstall.HostsInstallException;
import org.adaway.model.hostsinstall.HostsInstallModel;
import org.adaway.ui.AdAwayApplication;
import org.adaway.util.AppExecutors;

import java.util.Collection;

import static android.support.design.widget.Snackbar.LENGTH_INDEFINITE;
import static android.support.design.widget.Snackbar.LENGTH_LONG;

/**
 * This class is a {@link Snackbar} to notify about hosts install need.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class HostsInstallSnackbar {
    /**
     * The view to bind the snackbar to.
     */
    private View mView;
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
     * The notify snackbar when hosts update available ({@code null} if no hosts update).
     */
    private Snackbar notifySnackbar;
    /**
     * The wait snackbar during hosts install ({@code null} if no pending hosts install).
     */
    private Snackbar waitSnackbar;

    /**
     * Constructor.
     *
     * @param view The view to bind the snackbar to.
     */
    public HostsInstallSnackbar(@NonNull View view) {
        this.mView = view;
        this.update = false;
        this.skipUpdate = false;
        this.ignoreEventDuringInstall = false;
    }

    /**
     * Set whether or not ignore update events during the install.
     *
     * @param ignore {@code true} to ignore events, {@code false} otherwise.
     */
    public void setIgnoreEventDuringInstall(boolean ignore) {
        this.ignoreEventDuringInstall = ignore;
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
                if (t == null || (t instanceof Collection && ((Collection) t).isEmpty())) {
                    return;
                }
                // First update
                if (this.firstUpdate) {
                    this.firstUpdate = false;
                    return;
                }
                HostsInstallSnackbar.this.notifyUpdateAvailable();
            }
        };
    }

    /**
     * Notify update available.
     */
    public void notifyUpdateAvailable() {
        // Check if notify snackbar is already displayed
        if (this.notifySnackbar != null) {
            return;
        }
        // Check if wait snackbar is displayed
        if (this.waitSnackbar != null) {
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
        this.notifySnackbar = Snackbar.make(this.mView, R.string.notification_configuration_changed, LENGTH_INDEFINITE)
                .setAction(R.string.notification_configuration_changed_action, v -> install());
        this.notifySnackbar.show();
        // Mark update as notified
        this.update = false;
    }

    private void install() {
        this.showLoading();
        AppExecutors.getInstance().diskIO().execute(() -> {
            AdAwayApplication application = (AdAwayApplication) this.mView.getContext().getApplicationContext();
            HostsInstallModel model = application.getHostsInstallModel();
            try {
                model.retrieveHostsSources();
                model.applyHostsFile();
                this.endLoading(true);
            } catch (HostsInstallException exception) {
                this.endLoading(false);
            }
        });
    }

    private void showLoading() {
        // Clear notify snackbar
        if (this.notifySnackbar != null) {
            this.notifySnackbar.dismiss();
            this.notifySnackbar = null;
        }
        // Create and show wait snackbar
        this.waitSnackbar = Snackbar.make(this.mView, R.string.notification_configuration_installing, LENGTH_INDEFINITE);
        appendViewToSnackbar(this.waitSnackbar, new ProgressBar(this.mView.getContext()));
        this.waitSnackbar.show();
    }

    private void endLoading(boolean successfulInstall) {
        // Clear wait snackbar
        if (this.waitSnackbar != null) {
            this.waitSnackbar.dismiss();
            this.waitSnackbar = null;
        }
        // Check install failure
        if (!successfulInstall) {
            Snackbar failureSnackbar = Snackbar.make(this.mView, R.string.notification_configuration_failed, LENGTH_LONG);
            ImageView view = new ImageView(this.mView.getContext());
            view.setImageResource(R.drawable.status_fail);
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
                this.notifyUpdateAvailable();
            }
        }
    }

    private void appendViewToSnackbar(Snackbar snackbar, View view) {
        ViewGroup viewGroup = (ViewGroup) snackbar.getView().findViewById(android.support.design.R.id.snackbar_text).getParent();
        viewGroup.addView(view);
    }
}
