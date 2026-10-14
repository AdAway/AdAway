package org.adaway.ui.hosts;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.adaway.AdAwayApplication;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.source.SourceApplyState;
import org.adaway.model.source.SourceModel;
import org.adaway.util.AppExecutors;

import java.util.List;

import timber.log.Timber;

/**
 * This class is an {@link AndroidViewModel} for the {@link HostsSourcesFragment}.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class HostsSourcesViewModel extends AndroidViewModel {
    public enum ApplyStatus { IDLE, RUNNING, FAILED, SUCCEEDED }

    private final HostsSourceDao hostsSourceDao;
    private final SourceApplyState mApplyState = new SourceApplyState();
    private final MutableLiveData<ApplyStatus> mApplyStatus =
            new MutableLiveData<>(ApplyStatus.IDLE);
    @Nullable
    private Integer mUpdateSourceId;
    private String mObservedConfiguration;
    private long mConfigurationRevision;

    public HostsSourcesViewModel(@NonNull Application application) {
        super(application);
        this.hostsSourceDao = AppDatabase.getInstance(application).hostsSourceDao();
    }

    public LiveData<List<HostsSource>> getHostsSources() {
        return this.hostsSourceDao.loadAll();
    }

    public SourceApplyState getApplyState() {
        return mApplyState;
    }

    public LiveData<ApplyStatus> getApplyStatus() {
        return mApplyStatus;
    }

    @UiThread
    public void onSourcesChanged(List<HostsSource> sources) {
        if (sources != null) {
            String configuration = SourceApplyState.fingerprint(sources);
            if (!configuration.equals(mObservedConfiguration)) {
                if (mObservedConfiguration != null) {
                    mConfigurationRevision++;
                }
                mObservedConfiguration = configuration;
            }
        }
        mApplyState.updateSources(sources);
        mApplyStatus.setValue(mApplyStatus.getValue());
    }

    @UiThread
    public void observeAutomaticWork(String workId, boolean successful, String configuration) {
        boolean completed = mApplyState.observeAutomaticWork(workId, successful, configuration);
        mApplyStatus.setValue(completed && mApplyStatus.getValue() != ApplyStatus.RUNNING
                ? ApplyStatus.SUCCEEDED : mApplyStatus.getValue());
    }

    @UiThread
    public void retry() {
        updateSources(mUpdateSourceId);
    }

    @UiThread
    public void updateSources(@Nullable Integer sourceId) {
        if (mApplyStatus.getValue() == ApplyStatus.RUNNING) {
            return;
        }
        mUpdateSourceId = sourceId;
        long revision = mConfigurationRevision;
        mApplyStatus.setValue(ApplyStatus.RUNNING);
        AdAwayApplication application = getApplication();
        AppExecutors.getInstance().networkIO().execute(() -> {
            boolean successful = false;
            List<HostsSource> appliedSources = null;
            try {
                List<HostsSource> snapshot = hostsSourceDao.getAll();
                String configuration = SourceApplyState.fingerprint(snapshot);
                SourceModel sourceModel = application.getSourceModel();
                AdBlockModel adBlockModel = application.getAdBlockModel();
                boolean shouldApply = true;
                if (sourceId == null) {
                    shouldApply = sourceModel.checkAndRetrieveHostsSources();
                } else {
                    sourceModel.retrieveHostsSource(sourceId);
                }
                if (shouldApply) {
                    adBlockModel.apply();
                    if (configuration.equals(SourceApplyState.fingerprint(hostsSourceDao.getAll()))) {
                        appliedSources = snapshot;
                    }
                }
                successful = shouldApply;
            } catch (HostErrorException | RuntimeException exception) {
                Timber.w(exception, "Failed to update and apply sources.");
            }
            boolean result = successful;
            List<HostsSource> snapshot = appliedSources;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (snapshot != null && revision == mConfigurationRevision) {
                    mApplyState.appliedSources(snapshot, sourceId);
                } else if (result) {
                    mApplyState.invalidate();
                }
                mApplyStatus.setValue(result ? ApplyStatus.SUCCEEDED : ApplyStatus.FAILED);
            });
        });
    }

    public void toggleSourceEnabled(HostsSource source) {
        mConfigurationRevision++;
        AppExecutors.getInstance().diskIO().execute(() -> this.hostsSourceDao.toggleEnabled(source));
    }

    public void setSourceEnabled(HostsSource source, boolean enabled) {
        mConfigurationRevision++;
        AppExecutors.getInstance().diskIO().execute(() -> {
            source.setEnabled(enabled);
            this.hostsSourceDao.applySourceSelection(source.getId(), enabled);
        });
    }
}
