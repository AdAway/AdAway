package org.adaway.ui.lists.type;

import android.app.Application;
import android.database.sqlite.SQLiteConstraintException;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;

import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.ListType;
import org.adaway.ui.lists.ListsFilter;
import org.adaway.util.AppExecutors;
import org.adaway.util.Constants;
import org.adaway.util.Log;

import java.util.List;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;
import static org.adaway.ui.lists.ListsFilter.ALL;

/**
 * This class is an {@link AndroidViewModel} for the {@link AbstractListFragment} implementations.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class ListsViewModel extends AndroidViewModel {
    private final HostListItemDao hostListItemDao;
    private final MutableLiveData<ListsFilter> filter;
    private final LiveData<PagedList<HostListItem>> blackListItems;
    private final LiveData<PagedList<HostListItem>> whiteListItems;
    private final LiveData<PagedList<HostListItem>> redirectionListItems;
    private final LiveData<List<HostListItem>> userListItems;

    public ListsViewModel(@NonNull Application application) {
        super(application);
        this.hostListItemDao = AppDatabase.getInstance(getApplication()).hostsListItemDao();
        this.filter = new MutableLiveData<>(ALL);
        PagedList.Config pagingConfig = new PagedList.Config.Builder()
                .setPageSize(50)
                .setPrefetchDistance(150)
                .setEnablePlaceholders(false)
                .build();
        this.blackListItems = Transformations.switchMap(
                this.filter,
                filter -> new LivePagedListBuilder<>(this.hostListItemDao.loadList(BLOCKED.getValue(), filter.sourcesIncluded, filter.sqlQuery), pagingConfig).build()
        );
        this.whiteListItems = Transformations.switchMap(
                this.filter,
                filter -> new LivePagedListBuilder<>(this.hostListItemDao.loadList(ALLOWED.getValue(), filter.sourcesIncluded, filter.sqlQuery), pagingConfig).build()
        );
        this.redirectionListItems = Transformations.switchMap(
                this.filter,
                filter -> new LivePagedListBuilder<>(this.hostListItemDao.loadList(REDIRECTED.getValue(), filter.sourcesIncluded, filter.sqlQuery), pagingConfig).build()
        );
        this.userListItems = this.hostListItemDao.loadUserList();
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

    public LiveData<List<HostListItem>> getUserListItems() {
        return this.userListItems;
    }

    public void toggleItemEnabled(HostListItem item) {
        item.setEnabled(!item.isEnabled());
        AppExecutors.getInstance().diskIO().execute(() -> this.hostListItemDao.update(item));
    }

    public void addListItem(@NonNull ListType type, @NonNull String host, String redirection) {
        HostListItem item = new HostListItem();
        item.setType(type);
        item.setDisplayedHost(host);
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
        newItem.setDisplayedHost(host);
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

    public void search(String query) {
        ListsFilter currentFilter = getFilter();
        ListsFilter newFilter = new ListsFilter(currentFilter.sourcesIncluded, query);
        setFilter(newFilter);
    }

    public boolean isSearching() {
        return !getFilter().query.isEmpty();
    }

    public void clearSearch() {
        ListsFilter currentFilter = getFilter();
        ListsFilter newFilter = new ListsFilter(currentFilter.sourcesIncluded, "");
        setFilter(newFilter);
    }

    public void toggleSources() {
        ListsFilter currentFilter = getFilter();
        ListsFilter newFilter = new ListsFilter(!currentFilter.sourcesIncluded, currentFilter.query);
        setFilter(newFilter);
    }

    private ListsFilter getFilter() {
        ListsFilter filter = this.filter.getValue();
        return filter == null ? ALL : filter;
    }

    private void setFilter(ListsFilter filter) {
        this.filter.setValue(filter);
    }
}
