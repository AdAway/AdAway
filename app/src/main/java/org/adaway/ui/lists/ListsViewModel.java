package org.adaway.ui.lists;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.Pager;
import androidx.paging.PagingConfig;
import androidx.paging.PagingData;

import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.ListType;
import org.adaway.ui.lists.type.AbstractListFragment;
import org.adaway.util.AppExecutors;

import java.util.Optional;
import java.util.concurrent.Executor;

import static androidx.lifecycle.Transformations.switchMap;
import static androidx.paging.PagingLiveData.getLiveData;
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
    private static final Executor EXECUTOR = AppExecutors.getInstance().diskIO();
    private final HostListItemDao hostListItemDao;
    private final MutableLiveData<ListsFilter> filter;
    private final LiveData<PagingData<HostListItem>> blockedListItems;
    private final LiveData<PagingData<HostListItem>> allowedListItems;
    private final LiveData<PagingData<HostListItem>> redirectedListItems;
    private final MutableLiveData<Boolean> modelChanged;

    public ListsViewModel(@NonNull Application application) {
        super(application);
        this.hostListItemDao = AppDatabase.getInstance(application).hostsListItemDao();
        this.filter = new MutableLiveData<>(ALL);
        PagingConfig pagingConfig = new PagingConfig(50, 150, true);
        this.blockedListItems = switchMap(
                this.filter,
                filter -> getLiveData(new Pager<>(pagingConfig, () ->
                        this.hostListItemDao.loadList(BLOCKED.getValue(), filter.sourcesIncluded, filter.sqlQuery)
                ))
        );
        this.allowedListItems = switchMap(
                this.filter,
                filter -> getLiveData(new Pager<>(pagingConfig, () ->
                        this.hostListItemDao.loadList(ALLOWED.getValue(), filter.sourcesIncluded, filter.sqlQuery)
                ))
        );
        this.redirectedListItems = switchMap(
                this.filter,
                filter -> getLiveData(new Pager<>(pagingConfig, () ->
                        this.hostListItemDao.loadList(REDIRECTED.getValue(), filter.sourcesIncluded, filter.sqlQuery)
                ))
        );
        this.modelChanged = new MutableLiveData<>(false);
    }

    public LiveData<PagingData<HostListItem>> getBlockedListItems() {
        return this.blockedListItems;
    }

    public LiveData<PagingData<HostListItem>> getAllowedListItems() {
        return this.allowedListItems;
    }

    public LiveData<PagingData<HostListItem>> getRedirectedListItems() {
        return this.redirectedListItems;
    }

    public LiveData<Boolean> getModelChanged() {
        return this.modelChanged;
    }

    public void toggleItemEnabled(HostListItem item) {
        item.setEnabled(!item.isEnabled());
        EXECUTOR.execute(() -> {
            this.hostListItemDao.update(item);
            this.modelChanged.postValue(true);
        });
    }

    public void addListItem(@NonNull ListType type, @NonNull String host, String redirection) {
        HostListItem item = new HostListItem();
        item.setType(type);
        item.setHost(host);
        item.setRedirection(redirection);
        item.setEnabled(true);
        item.setSourceId(USER_SOURCE_ID);
        EXECUTOR.execute(() -> {
            Optional<Integer> id = this.hostListItemDao.getHostId(host);
            if (id.isPresent()) {
                item.setId(id.get());
                this.hostListItemDao.update(item);
            } else {
                this.hostListItemDao.insert(item);
            }
            this.modelChanged.postValue(true);
        });
    }

    public void updateListItem(@NonNull HostListItem item, @NonNull String host, String redirection) {
        item.setHost(host);
        item.setRedirection(redirection);
        EXECUTOR.execute(() -> {
            this.hostListItemDao.update(item);
            this.modelChanged.postValue(true);
        });
    }

    public void removeListItem(HostListItem list) {
        EXECUTOR.execute(() -> {
            this.hostListItemDao.delete(list);
            this.modelChanged.postValue(true);
        });
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
