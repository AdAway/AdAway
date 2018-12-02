package org.adaway.ui.hostsinstall;

import android.arch.lifecycle.Observer;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import org.adaway.R;
import org.adaway.model.hostsinstall.HostsInstallException;
import org.adaway.model.hostsinstall.HostsInstallModel;
import org.adaway.ui.AdAwayApplication;
import org.adaway.util.AppExecutors;

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
                if (this.firstUpdate) {
                    this.firstUpdate = false;
                    return;
                }
                HostsInstallSnackbar.this.notifyUpdateAvailable();
            }
        };
    }

    private void notifyUpdateAvailable() {
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
        // Show notify snackbar
        this.waitSnackbar = Snackbar.make(this.mView, R.string.notification_configuration_changed, LENGTH_INDEFINITE)
                .setAction(R.string.notification_configuration_changed_action, v -> install());
        this.waitSnackbar.show();
        // Mark update as notified
        this.update = false;
    }

    private void install() {
        this.showLoading();
        AppExecutors.getInstance().diskIO().execute(() -> {
            AdAwayApplication application = (AdAwayApplication) this.mView.getContext().getApplicationContext();
            HostsInstallModel model = application.getHostsInstallModel();
            try {
                model.downloadHostsSources();
                model.applyHostsFile();
            } catch (HostsInstallException exception) {
                Snackbar.make(this.mView, "install failed", LENGTH_LONG).show();
            } finally {
                this.endLoading();
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
        ViewGroup contentLay = (ViewGroup) this.waitSnackbar.getView().findViewById(android.support.design.R.id.snackbar_text).getParent();
        ProgressBar item = new ProgressBar(this.mView.getContext());
        contentLay.addView(item);
        this.waitSnackbar.show();
    }

    private void endLoading() {
        // Clear wait snackbar
        if (this.waitSnackbar != null) {
            this.waitSnackbar.dismiss();
            this.waitSnackbar = null;
        }
        // Check pending update notification
        if (this.update) {
            this.notifyUpdateAvailable();
        }
    }
}
