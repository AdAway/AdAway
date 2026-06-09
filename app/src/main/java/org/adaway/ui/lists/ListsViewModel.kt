package org.adaway.ui.lists

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import org.adaway.db.AppDatabase
import org.adaway.db.dao.HostListItemDao
import org.adaway.db.entity.HostListItem
import org.adaway.db.entity.HostsSource.USER_SOURCE_ID
import org.adaway.db.entity.ListType
import org.adaway.db.entity.ListType.ALLOWED
import org.adaway.db.entity.ListType.BLOCKED
import org.adaway.db.entity.ListType.REDIRECTED
import org.adaway.ui.lists.ListsFilter.ALL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ListsViewModel(application: Application) : AndroidViewModel(application) {
    private val hostListItemDao: HostListItemDao = AppDatabase.getInstance(application).hostsListItemDao()
    private val filter = MutableStateFlow(ALL)
    private val pagingConfig = PagingConfig(50, 150, true)

    val blockedListItems: Flow<PagingData<HostListItem>> = filter.flatMapLatest { currentFilter ->
        Pager(pagingConfig) {
            hostListItemDao.loadList(BLOCKED.value, currentFilter.sourcesIncluded, currentFilter.sqlQuery)
        }.flow
    }.cachedIn(viewModelScope)

    val allowedListItems: Flow<PagingData<HostListItem>> = filter.flatMapLatest { currentFilter ->
        Pager(pagingConfig) {
            hostListItemDao.loadList(ALLOWED.value, currentFilter.sourcesIncluded, currentFilter.sqlQuery)
        }.flow
    }.cachedIn(viewModelScope)

    val redirectedListItems: Flow<PagingData<HostListItem>> = filter.flatMapLatest { currentFilter ->
        Pager(pagingConfig) {
            hostListItemDao.loadList(REDIRECTED.value, currentFilter.sourcesIncluded, currentFilter.sqlQuery)
        }.flow
    }.cachedIn(viewModelScope)

    private val _modelChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val modelChanged: SharedFlow<Unit> = _modelChanged.asSharedFlow()

    fun toggleItemEnabled(item: HostListItem) {
        item.isEnabled = !item.isEnabled
        viewModelScope.launch(Dispatchers.IO) {
            hostListItemDao.update(item)
            _modelChanged.emit(Unit)
        }
    }

    fun addListItem(type: ListType, host: String, redirection: String?) {
        val item = HostListItem().apply {
            this.type = type
            this.host = host
            this.redirection = redirection
            isEnabled = true
            sourceId = USER_SOURCE_ID
        }
        viewModelScope.launch(Dispatchers.IO) {
            val id = hostListItemDao.getHostId(host)
            if (id.isPresent) {
                item.id = id.get()
                hostListItemDao.update(item)
            } else {
                hostListItemDao.insert(item)
            }
            _modelChanged.emit(Unit)
        }
    }

    fun updateListItem(item: HostListItem, host: String, redirection: String?) {
        item.host = host
        item.redirection = redirection
        viewModelScope.launch(Dispatchers.IO) {
            hostListItemDao.update(item)
            _modelChanged.emit(Unit)
        }
    }

    fun removeListItem(list: HostListItem) {
        viewModelScope.launch(Dispatchers.IO) {
            hostListItemDao.delete(list)
            _modelChanged.emit(Unit)
        }
    }

    fun search(query: String) {
        val currentFilter = getFilter()
        setFilter(ListsFilter(currentFilter.sourcesIncluded, query))
    }

    fun isSearching(): Boolean = getFilter().query.isNotEmpty()

    fun clearSearch() {
        val currentFilter = getFilter()
        setFilter(ListsFilter(currentFilter.sourcesIncluded, ""))
    }

    fun toggleSources() {
        val currentFilter = getFilter()
        setFilter(ListsFilter(!currentFilter.sourcesIncluded, currentFilter.query))
    }

    private fun getFilter(): ListsFilter = filter.value

    private fun setFilter(filter: ListsFilter) {
        this.filter.value = filter
    }
}
