package org.adaway.ui.lists.type;

import android.app.Application;

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

import java.util.List;
import java.util.Optional;

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
    private final LiveData<PagedList<HostListItem>> blockedListItems;
    private final LiveData<PagedList<HostListItem>> allowedListItems;
    private final LiveData<PagedList<HostListItem>> redirectedListItems;
    private final LiveData<List<HostListItem>> userListItems;

    public ListsViewModel(@NonNull Application application) {
        super(application);
        this.hostListItemDao = AppDatabase.getInstance(application).hostsListItemDao();
        this.filter = new MutableLiveData<>(ALL);
        PagedList.Config pagingConfig = new PagedList.Config.Builder()
                .setPageSize(50)
                .setPrefetchDistance(150)
                .setEnablePlaceholders(true)
                .build();
        this.blockedListItems = Transformations.switchMap(
                this.filter,
                filter -> new LivePagedListBuilder<>(this.hostListItemDao.loadList(BLOCKED.getValue(), filter.sourcesIncluded, filter.sqlQuery), pagingConfig).build()
        );
        this.allowedListItems = Transformations.switchMap(
                this.filter,
                filter -> new LivePagedListBuilder<>(this.hostListItemDao.loadList(ALLOWED.getValue(), filter.sourcesIncluded, filter.sqlQuery), pagingConfig).build()
        );
        this.redirectedListItems = Transformations.switchMap(
                this.filter,
                filter -> new LivePagedListBuilder<>(this.hostListItemDao.loadList(REDIRECTED.getValue(), filter.sourcesIncluded, filter.sqlQuery), pagingConfig).build()
        );
        this.userListItems = this.hostListItemDao.loadUserList();
    }

    public LiveData<PagedList<HostListItem>> getBlockedListItems() {
        return this.blockedListItems;
    }

    public LiveData<PagedList<HostListItem>> getAllowedListItems() {
        return this.allowedListItems;
    }

    public LiveData<PagedList<HostListItem>> getRedirectedListItems() {
        return this.redirectedListItems;
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
        item.setHost(host);
        item.setRedirection(redirection);
        item.setEnabled(true);
        item.setSourceId(USER_SOURCE_ID);
        AppExecutors.getInstance().diskIO().execute(() -> {
            Optional<Integer> id = this.hostListItemDao.getHostId(host);
            if (id.isPresent()) {
                item.setId(id.get());
                this.hostListItemDao.update(item);
            } else {
                this.hostListItemDao.insert(item);
            }
        });
    }

    public void updateListItem(@NonNull HostListItem item, @NonNull String host, String redirection) {
        item.setHost(host);
        item.setRedirection(redirection);
        AppExecutors.getInstance().diskIO().execute(() -> this.hostListItemDao.update(item));
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
