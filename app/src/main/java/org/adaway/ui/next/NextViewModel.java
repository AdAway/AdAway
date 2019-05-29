package org.adaway.ui.next;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.adaway.AdAwayApplication;
import org.adaway.model.error.HostError;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.hostlist.HostListModel;
import org.adaway.model.source.SourceModel;
import org.adaway.util.AppExecutors;
import org.adaway.util.Log;

public class NextViewModel extends AndroidViewModel {
    private static final String TAG = "NextViewModel";

    private SourceModel sourceModel;
    private HostListModel hostListModel;

    private MutableLiveData<HostError> error;

    public NextViewModel(@NonNull Application application) {
        super(application);
        this.sourceModel = ((AdAwayApplication) application).getSourceModel();
        this.hostListModel = ((AdAwayApplication) application).getHostsListModel();


        this.error = new MutableLiveData<>();
    }

    public LiveData<Boolean> isAdBlocked() {
        return this.hostListModel.isApplied();
    }

    public LiveData<Boolean> isUpdateAvailable() {
        return this.sourceModel.isUpdateAvailable();
    }

    public LiveData<HostError> getError() {
        return this.error;
    }

    public void toggleAdBlocking() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                if (Boolean.TRUE == this.hostListModel.isApplied().getValue()) {
                    this.hostListModel.revert();
                } else {
                    this.hostListModel.apply();
                }
            } catch (HostErrorException exception) {
                Log.w(TAG, "Failed to toggle ad blocking.", exception);
                this.error.postValue(exception.getError());
            }
        });
    }

    public void sync() {
        AppExecutors.getInstance().networkIO().execute(() -> {
            try {
                this.sourceModel.retrieveHostsSources();
                this.hostListModel.apply();
            } catch (HostErrorException exception) {
                Log.w(TAG, "Failed to sync.", exception);
                this.error.postValue(exception.getError());
            }
        });
    }
}
