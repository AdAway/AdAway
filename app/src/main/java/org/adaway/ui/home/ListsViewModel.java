package org.adaway.ui.home;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.ui.next.NextActivity;

/**
 * This class is an {@link AndroidViewModel} for the {@link NextActivity} cards.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class ListsViewModel extends AndroidViewModel {

    private final HostListItemDao hostListItemDao;

    public ListsViewModel(@NonNull Application application) {
        super(application);
        this.hostListItemDao = AppDatabase.getInstance(this.getApplication()).hostsListItemDao();
    }

    public LiveData<Integer> getBlockedHostCount() {
        return this.hostListItemDao.getBlockedHostCount();
    }

    public LiveData<Integer> getAllowedHostCount() {
        return this.hostListItemDao.getAllowedHostCount();
    }

    public LiveData<Integer> getRedirectHostCount() {
        return this.hostListItemDao.getRedirectHostCount();
    }
}
