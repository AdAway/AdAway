package org.adaway.ui.adblocking;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.lifecycle.Observer;

import com.google.android.material.snackbar.Snackbar;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.source.SourceModel;
import org.adaway.ui.hosts.HostsSourcesViewModel;
import org.adaway.ui.hosts.HostsSourcesViewModel.ApplyStatus;
import org.adaway.util.AppExecutors;

import timber.log.Timber;

import static com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE;
import static com.google.android.material.snackbar.Snackbar.LENGTH_LONG;

/**
 * This class is a {@link Snackbar} to notify about adblock model new
 * configuration to apply.
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
     * The current hosts update available status ({@code true} if update available,
     * {@code false} otherwise).
     */
    private boolean update;
    /**
     * Whether or not ignore the next update event ({@code true} to ignore,
     * {@code false} otherwise).
     */
    private boolean skipUpdate;
    /**
     * Whether or not ignore update events during the install ({@code true} to
     * ignore, {@code false} otherwise).
     */
    private boolean ignoreEventDuringInstall;
    private boolean applying;
    @Nullable
    private final HostsSourcesViewModel mSourceViewModel;
    @Nullable
    private ApplyStatus mLastSourceStatus;
    @Nullable
    private Snackbar mFailureSnackbar;

    /**
     * Constructor.
     *
     * @param view                     The view to bind the snackbar to.
     * @param syncSources              To synchronize sources before installing or
     *                                 not.
     * @param ignoreEventDuringInstall {@code true} to ignore events, {@code false}
     *                                 otherwise.
     */
    public ApplyConfigurationSnackbar(@NonNull View view, boolean syncSources, boolean ignoreEventDuringInstall) {
        this(view, syncSources, ignoreEventDuringInstall, null);
    }

    public ApplyConfigurationSnackbar(@NonNull View view, @NonNull HostsSourcesViewModel sourceViewModel) {
        this(view, true, false, sourceViewModel);
    }

    private ApplyConfigurationSnackbar(@NonNull View view, boolean syncSources,
            boolean ignoreEventDuringInstall, @Nullable HostsSourcesViewModel sourceViewModel) {
        this.view = view;
        this.notifySnackbar = Snackbar.make(this.view, R.string.notification_configuration_changed, LENGTH_INDEFINITE)
                .setAction(R.string.notification_configuration_changed_action, v -> apply());
        this.waitSnackbar = Snackbar.make(this.view, R.string.notification_configuration_installing, LENGTH_INDEFINITE);
        appendViewToSnackbar(this.waitSnackbar, new ProgressBar(this.view.getContext()));
        this.syncSources = syncSources;
        this.ignoreEventDuringInstall = ignoreEventDuringInstall;
        this.update = false;
        this.skipUpdate = false;
        this.mSourceViewModel = sourceViewModel;
    }

    @UiThread
    public void refreshSourceState() {
        if (this.mSourceViewModel == null) return;
        ApplyStatus status = this.mSourceViewModel.getApplyStatus().getValue();
        boolean changed = status != this.mLastSourceStatus;
        this.mLastSourceStatus = status;
        if (status == ApplyStatus.RUNNING) {
            if (changed) showLoading();
            return;
        }
        this.waitSnackbar.dismiss();
        if (status == ApplyStatus.FAILED) {
            if (this.mFailureSnackbar == null) showFailure(this.mSourceViewModel::retry);
            return;
        }
        if (status == ApplyStatus.SUCCEEDED) dismissFailure();
        if (this.mSourceViewModel.getApplyState().isPending()) {
            notifyUpdateAvailable();
        } else {
            this.notifySnackbar.dismiss();
            if (changed && status == ApplyStatus.SUCCEEDED) showApplied();
        }
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
                // Check new data
                if (t == null) {
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
        if (this.notifySnackbar.isShownOrQueued()) {
            return;
        }
        // Check if wait snackbar is displayed
        if (this.applying) {
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

    @UiThread
    void apply() {
        if (this.mSourceViewModel != null) {
            this.mSourceViewModel.updateSources(null);
            return;
        }
        if (this.applying) {
            return;
        }
        this.applying = true;
        showLoading();
        AdAwayApplication application =
                (AdAwayApplication) this.view.getContext().getApplicationContext();
        AppExecutors.getInstance().diskIO().execute(() -> {
            boolean successful = false;
            try {
                SourceModel sourceModel = application.getSourceModel();
                AdBlockModel adBlockModel = application.getAdBlockModel();
                boolean shouldApply = true;
                if (this.syncSources) {
                    shouldApply = sourceModel.checkAndRetrieveHostsSources();
                } else {
                    sourceModel.syncHostEntries();
                }
                if (shouldApply) {
                    adBlockModel.apply();
                }
                successful = shouldApply;
            } catch (HostErrorException | RuntimeException exception) {
                successful = false;
                Timber.w(exception, "Failed to apply configuration.");
            }
            boolean result = successful;
            AppExecutors.getInstance().mainThread().execute(() -> endLoading(result));
        });
    }

    private void showLoading() {
        // Clear notify snackbar
        dismissFailure();
        this.notifySnackbar.dismiss();
        // Show wait snackbar
        if (this.mSourceViewModel != null) {
            this.waitSnackbar.setText(R.string.sources_apply_installing);
        }
        this.waitSnackbar.show();
    }

    @UiThread
    void endLoading(boolean successfulInstall) {
        this.applying = false;
        // Clear snackbars
        this.waitSnackbar.dismiss();
        if (!this.view.isAttachedToWindow()) {
            return;
        }
        if (!successfulInstall) {
            showFailure(null);
            return;
        }
        if (this.update) {
            // Ignore next update event if events should be ignored
            if (this.ignoreEventDuringInstall) {
                this.skipUpdate = true;
                showApplied();
            } else {
                // Otherwise display update notification
                notifyUpdateAvailable();
            }
            return;
        }
        showApplied();
    }

    private void showApplied() {
        Snackbar.make(this.view, R.string.notification_configuration_applied, LENGTH_LONG).show();
    }

    @UiThread
    public void showFailure(@Nullable Runnable retry) {
        dismissFailure();
        this.notifySnackbar.dismiss();
        this.mFailureSnackbar = Snackbar.make(this.view,
                R.string.notification_configuration_failed,
                retry == null ? LENGTH_LONG : LENGTH_INDEFINITE);
        if (retry != null) {
            this.mFailureSnackbar.setAction(R.string.notification_configuration_changed_action,
                    v -> retry.run());
        }
        ImageView icon = new ImageView(this.view.getContext());
        icon.setImageResource(R.drawable.ic_error_outline_24dp);
        appendViewToSnackbar(this.mFailureSnackbar, icon);
        this.mFailureSnackbar.show();
    }

    private void dismissFailure() {
        if (this.mFailureSnackbar != null) {
            this.mFailureSnackbar.dismiss();
            this.mFailureSnackbar = null;
        }
    }

    private void appendViewToSnackbar(Snackbar snackbar, View view) {
        ViewGroup viewGroup = (ViewGroup) snackbar.getView()
                .findViewById(com.google.android.material.R.id.snackbar_text).getParent();
        viewGroup.addView(view);
    }
}
