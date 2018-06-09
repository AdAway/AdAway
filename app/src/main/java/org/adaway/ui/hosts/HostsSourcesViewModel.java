package org.adaway.ui.hosts;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.support.annotation.NonNull;

import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.util.AppExecutors;

import java.util.List;

public class HostsSourcesViewModel extends AndroidViewModel {

    private final HostsSourceDao hostsSourceDao;

    public HostsSourcesViewModel(@NonNull Application application) {
        super(application);
        this.hostsSourceDao = AppDatabase.getInstance(this.getApplication()).hostsSourceDao();
    }

    public LiveData<List<HostsSource>> getHostsSources() {
        return this.hostsSourceDao.loadAll();
    }

    public void toggleSourceEnabled(HostsSource source) {
        source.setEnabled(!source.isEnabled());
        this.updateSource(source);
    }

    public void addSourceFromUrl(String url) {
        HostsSource source = new HostsSource();
        source.setUrl(url);
        source.setEnabled(true);
        AppExecutors.getInstance().diskIO().execute(() -> this.hostsSourceDao.insert(source));
    }

    public void updateSourceUrl(HostsSource source, String url) {
        source.setUrl(url);
        source.setLastLocalModification(null);
        source.setLastOnlineModification(null);
        this.updateSource(source);
    }

    public void removeSource(HostsSource source) {
        AppExecutors.getInstance().diskIO().execute(() -> this.hostsSourceDao.delete(source));
    }

    private void updateSource(HostsSource source) {
        AppExecutors.getInstance().diskIO().execute(() -> this.hostsSourceDao.update(source));
    }
}
