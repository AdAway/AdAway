package org.adaway.ui.lists;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import android.database.sqlite.SQLiteConstraintException;

import androidx.annotation.NonNull;

import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.ListType;
import org.adaway.util.AppExecutors;
import org.adaway.util.Constants;
import org.adaway.util.Log;

import java.util.List;

/**
 * This class is an {@link AndroidViewModel} for the {@link AbstractListFragment} implementations.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class ListsViewModel extends AndroidViewModel {

    private final HostListItemDao hostListItemDao;

    public ListsViewModel(@NonNull Application application) {
        super(application);
        this.hostListItemDao = AppDatabase.getInstance(this.getApplication()).hostsListItemDao();
    }

    public LiveData<List<HostListItem>> getBlackListItems() {
        return this.hostListItemDao.loadBlackList();
    }

    public LiveData<List<HostListItem>> getWhiteListItems() {
        return this.hostListItemDao.loadWhiteList();
    }

    public LiveData<List<HostListItem>> getRedirectionListItems() {
        return this.hostListItemDao.loadRedirectionList();
    }

    public void toggleItemEnabled(HostListItem item) {
        item.setEnabled(!item.isEnabled());
        AppExecutors.getInstance().diskIO().execute(() -> this.hostListItemDao.update(item));
    }

    public void addListItem(@NonNull ListType type, @NonNull String host, String redirection) {
        HostListItem item = new HostListItem();
        item.setType(type);
        item.setHost(host);
        item.setRedirection(redirection);
        item.setEnabled(true);
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                this.hostListItemDao.insert(item);
            } catch (SQLiteConstraintException exception) {
                Log.w(Constants.TAG, "Unable to add duplicate list item: " + item + ".", exception);
            }
        });
    }

    public void updateListItem(@NonNull HostListItem item, @NonNull String host, String redirection) {
        HostListItem newItem = new HostListItem();
        newItem.setType(item.getType());
        newItem.setHost(host);
        newItem.setRedirection(redirection);
        newItem.setEnabled(item.isEnabled());
        AppExecutors.getInstance().diskIO().execute(() -> {
            this.hostListItemDao.delete(item);
            this.hostListItemDao.insert(newItem);
        });
    }

    public void removeListItem(HostListItem list) {
        AppExecutors.getInstance().diskIO().execute(() -> this.hostListItemDao.delete(list));
    }
}
