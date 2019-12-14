package org.adaway.ui.lists;

import android.app.Application;
import android.database.sqlite.SQLiteConstraintException;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;

import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.ListType;
import org.adaway.util.AppExecutors;
import org.adaway.util.Constants;
import org.adaway.util.Log;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;

/**
 * This class is an {@link AndroidViewModel} for the {@link AbstractListFragment} implementations.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class ListsViewModel extends AndroidViewModel {

    private final HostListItemDao hostListItemDao;
    private final LiveData<PagedList<HostListItem>> blackListItems;
    private final LiveData<PagedList<HostListItem>> whiteListItems;
    private final LiveData<PagedList<HostListItem>> redirectionListItems;

    public ListsViewModel(@NonNull Application application) {
        super(application);
        this.hostListItemDao = AppDatabase.getInstance(getApplication()).hostsListItemDao();
        PagedList.Config pagingConfig = new PagedList.Config.Builder()
                .setPageSize(50)
                .setPrefetchDistance(150)
                .setEnablePlaceholders(true)
                .build();
        this.blackListItems = new LivePagedListBuilder<>(this.hostListItemDao.loadBlackList(), pagingConfig).build();
        this.whiteListItems = new LivePagedListBuilder<>(this.hostListItemDao.loadWhiteList(), pagingConfig).build();
        this.redirectionListItems = new LivePagedListBuilder<>(this.hostListItemDao.loadWhiteList(), pagingConfig).build();
    }

    public LiveData<PagedList<HostListItem>> getBlackListItems() {
        return this.blackListItems;
    }

    public LiveData<PagedList<HostListItem>> getWhiteListItems() {
        return this.whiteListItems;
    }

    public LiveData<PagedList<HostListItem>> getRedirectionListItems() {
        return this.redirectionListItems;
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
        item.setSourceId(USER_SOURCE_ID);
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
        newItem.setSourceId(USER_SOURCE_ID);
        AppExecutors.getInstance().diskIO().execute(() -> {
            this.hostListItemDao.delete(item);
            this.hostListItemDao.insert(newItem);
        });
    }

    public void removeListItem(HostListItem list) {
        AppExecutors.getInstance().diskIO().execute(() -> this.hostListItemDao.delete(list));
    }
}
