package org.adaway.ui.home;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.error.HostError;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.hostlist.HostListModel;
import org.adaway.model.source.SourceModel;
import org.adaway.util.AppExecutors;
import org.adaway.util.Constants;
import org.adaway.util.Log;

import static org.adaway.ui.home.HostsInstallStatus.INSTALLED;
import static org.adaway.ui.home.HostsInstallStatus.ORIGINAL;
import static org.adaway.ui.home.HostsInstallStatus.OUTDATED;
import static org.adaway.ui.home.HostsInstallStatus.WORK_IN_PROGRESS;

/**
 * This class is a {@link androidx.lifecycle.ViewModel} for home fragment UI.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class HostsInstallViewModel extends AndroidViewModel {
    private final SourceModel sourceModel;
    private final HostListModel hostListModel;
    private final MutableLiveData<HostsInstallStatus> status;
//    private final MutableLiveData<String> state;
    private final MutableLiveData<String> details;
    private final MutableLiveData<HostError> error;
    private boolean loaded;

    /**
     * Constructor.
     *
     * @param application The application context.
     */
    public HostsInstallViewModel(@NonNull Application application) {
        super(application);
        // Retrieve models
        this.sourceModel = ((AdAwayApplication) application).getSourceModel();
        this.hostListModel = ((AdAwayApplication) application).getHostsListModel();
        // Initialize live data
        this.status = new MutableLiveData<>();
//        this.state = new MutableLiveData<>();
        this.details = new MutableLiveData<>();
        this.error = new MutableLiveData<>();
        // Initialize model as not loaded
        this.loaded = false;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
    }

    LiveData<HostsInstallStatus> getStatus() {
        return this.status;
    }

    LiveData<String> getState() {
        return this.hostListModel.getState();
    }

    LiveData<String> getDetails() {
        return this.details;
    }

    LiveData<HostError> getError() {
        return this.error;
    }

    /**
     * Initialize the model with its current state.
     */
    void load() {
        // Check if model is already loaded
        if (this.loaded) {
            return;
        }
        this.loaded = true;
        // Check if hosts file is installed
        AppExecutors.getInstance().diskIO().execute(() -> {
            if (Boolean.TRUE == this.hostListModel.isApplied().getValue()) {
                this.status.postValue(INSTALLED);
                this.setStateAndDetails(R.string.status_enabled, R.string.status_enabled_subtitle);
                // Check for update if needed
                if (PreferenceHelper.getUpdateCheck(this.getApplication())) {
                    this.checkForUpdate();
                }
            } else {
                this.status.postValue(ORIGINAL);
                this.setStateAndDetails(R.string.status_disabled, R.string.status_disabled_subtitle);
            }
        });
    }

    /**
     * Update the hosts file.
     */
    void update() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            HostsInstallStatus previousStatus = this.status.getValue();
            this.status.postValue(WORK_IN_PROGRESS);
            try {
                this.sourceModel.retrieveHostsSources();
                this.hostListModel.apply();
                this.status.postValue(INSTALLED);
            } catch (HostErrorException exception) {
                Log.e(Constants.TAG, "Failed to update hosts file.", exception);
                this.status.postValue(previousStatus);
                this.error.postValue(exception.getError());
            }
        });
    }

    /**
     * Check if there is update available in hosts source.
     */
    void checkForUpdate() {
        AppExecutors.getInstance().networkIO().execute(() -> {
            // Update status
            this.status.postValue(WORK_IN_PROGRESS);
            try {
                // Check if update is available
                if (this.sourceModel.checkForUpdate()) {
                    this.status.postValue(OUTDATED);
                } else {
                    this.status.postValue(INSTALLED);
                }
            } catch (HostErrorException exception) {
                Log.e(Constants.TAG, "Failed to check for update.", exception);
                this.status.postValue(INSTALLED);
                this.error.postValue(exception.getError());
            }
        });
    }

    /**
     * Revert to the default hosts file.
     */
    void revert() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            this.status.postValue(WORK_IN_PROGRESS);
            try {
                this.hostListModel.revert();
                this.status.postValue(ORIGINAL);
            } catch (HostErrorException exception) {
                Log.e(Constants.TAG, "Failed to revert hosts file.", exception);
                this.status.postValue(INSTALLED);
                this.error.postValue(exception.getError());
            }
        });
    }

    private void setStateAndDetails(@StringRes int stateResId, @StringRes int detailsResId) {
//        this.state.postValue(this.getApplication().getString(stateResId));
//        this.details.postValue(this.getApplication().getString(detailsResId));
    }
}
