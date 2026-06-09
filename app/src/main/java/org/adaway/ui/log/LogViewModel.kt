package org.adaway.ui.log

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.adaway.AdAwayApplication
import org.adaway.db.AppDatabase
import org.adaway.db.dao.HostEntryDao
import org.adaway.db.dao.HostListItemDao
import org.adaway.db.entity.HostListItem
import org.adaway.db.entity.HostsSource.USER_SOURCE_ID
import org.adaway.db.entity.ListType
import org.adaway.model.adblocking.AdBlockMethod
import org.adaway.model.adblocking.AdBlockModel
import org.adaway.util.ExpressiveToast

class LogViewModel(application: Application) : AndroidViewModel(application) {
    private val adBlockModel: AdBlockModel = (application as AdAwayApplication).adBlockModel
    private val hostListItemDao: HostListItemDao = AppDatabase.getInstance(application).hostsListItemDao()
    private val hostEntryDao: HostEntryDao = AppDatabase.getInstance(application).hostEntryDao()
    private var sort = LogEntrySort.TOP_LEVEL_DOMAIN

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs

    private val _recording = MutableStateFlow(adBlockModel.isRecordingLogs)
    val recording: StateFlow<Boolean> = _recording

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    fun areBlockedRequestsIgnored(): Boolean = adBlockModel.method == AdBlockMethod.ROOT

    fun clearLogs() {
        adBlockModel.clearLogs()
        _logs.value = emptyList()
        _refreshing.value = false
    }

    fun updateLogs() {
        viewModelScope.launch {
            _refreshing.value = true
            val logItems = withContext(Dispatchers.IO) {
                adBlockModel.logs
                    .parallelStream()
                    .map { log -> LogEntry(log, hostEntryDao.getTypeOfHost(log)) }
                    .sorted(sort.comparator())
                    .toList()
            }
            _logs.value = logItems
            _refreshing.value = false
        }
    }

    fun toggleSort() {
        sortDnsRequests(
            if (sort == LogEntrySort.ALPHABETICAL) {
                LogEntrySort.TOP_LEVEL_DOMAIN
            } else {
                LogEntrySort.ALPHABETICAL
            }
        )
    }

    fun toggleRecording() {
        val recording = !adBlockModel.isRecordingLogs
        adBlockModel.setRecordingLogs(recording)
        _recording.value = recording
    }

    fun addListItem(host: String, type: ListType, redirection: String?) {
        val item = HostListItem().apply {
            this.type = type
            this.host = host
            this.redirection = redirection
            isEnabled = true
            sourceId = USER_SOURCE_ID
        }
        viewModelScope.launch(Dispatchers.IO) {
            hostListItemDao.insert(item)
        }
        updateLogEntryType(host, type)
    }

    fun removeListItem(host: String) {
        viewModelScope.launch(Dispatchers.IO) {
            hostListItemDao.deleteUserFromHost(host)
        }
        updateLogEntryType(host, null)
    }

    private fun updateLogEntryType(host: String, type: ListType?) {
        _logs.value = _logs.value.map { entry ->
            if (entry.host == host) LogEntry(host, type) else entry
        }
    }

    private fun sortDnsRequests(sort: LogEntrySort) {
        this.sort = sort
        _logs.value = _logs.value.sortedWith(sort.comparator())
        ExpressiveToast.makeText(
            getApplication(),
            sort.getName(),
            Toast.LENGTH_SHORT
        ).show()
    }
}
