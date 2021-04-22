package org.adaway.ui.log;

import android.app.Application;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.adaway.AdAwayApplication;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostEntryDao;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.ListType;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.util.AppExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;

/**
 * This class is an {@link AndroidViewModel} for the {@link LogActivity}.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class LogViewModel extends AndroidViewModel {
    private final AdBlockModel adBlockModel;
    private final HostListItemDao hostListItemDao;
    private final HostEntryDao hostEntryDao;
    private final MutableLiveData<List<LogEntry>> logEntries;
    private final MutableLiveData<Boolean> recording;
    private LogEntrySort sort;

    public LogViewModel(@NonNull Application application) {
        super(application);
        this.adBlockModel = ((AdAwayApplication) application).getAdBlockModel();
        this.hostListItemDao = AppDatabase.getInstance(application).hostsListItemDao();
        this.hostEntryDao = AppDatabase.getInstance(application).hostEntryDao();
        this.logEntries = new MutableLiveData<>();
        this.recording = new MutableLiveData<>(this.adBlockModel.isRecordingLogs());
        this.sort = LogEntrySort.TOP_LEVEL_DOMAIN;
    }

    public boolean areBlockedRequestsIgnored() {
        return this.adBlockModel.getMethod() == AdBlockMethod.ROOT;
    }

    public LiveData<List<LogEntry>> getLogs() {
        return this.logEntries;
    }

    public void clearLogs() {
        this.adBlockModel.clearLogs();
        this.logEntries.postValue(Collections.emptyList());
    }

    public void updateLogs() {
        AppExecutors.getInstance().diskIO().execute(
                () -> {
                    // Get tcpdump logs
                    List<LogEntry> logItems = this.adBlockModel.getLogs()
                            .parallelStream()
                            .map(log -> {
                                ListType type = this.hostEntryDao.getTypeOfHost(log);
                                return new LogEntry(log, type);
                            })
                            .sorted(this.sort.comparator())
                            .collect(Collectors.toList());
                    // Post result
                    this.logEntries.postValue(logItems);
                }
        );
    }

    public void toggleSort() {
        this.sortDnsRequests(this.sort == LogEntrySort.ALPHABETICAL ?
                LogEntrySort.TOP_LEVEL_DOMAIN :
                LogEntrySort.ALPHABETICAL
        );
    }

    public LiveData<Boolean> isRecording() {
        return this.recording;
    }

    public void toggleRecording() {
        boolean recording = !this.adBlockModel.isRecordingLogs();
        this.adBlockModel.setRecordingLogs(recording);
        this.recording.postValue(recording);
    }

    public void addListItem(@NonNull String host, @NonNull ListType type, String redirection) {
        // Create new host list item
        HostListItem item = new HostListItem();
        item.setType(type);
        item.setHost(host);
        item.setRedirection(redirection);
        item.setEnabled(true);
        item.setSourceId(USER_SOURCE_ID);
        // Insert host list item
        AppExecutors.getInstance().diskIO().execute(() -> this.hostListItemDao.insert(item));
        // Update log entries
        updateLogEntryType(host, type);
    }

    public void removeListItem(@NonNull String host) {
        // Delete host list item
        AppExecutors.getInstance().diskIO().execute(() -> this.hostListItemDao.deleteUserFromHost(host));
        // Update log entries
        updateLogEntryType(host, null);
    }

    private void updateLogEntryType(@NonNull String host, ListType type) {
        // Get current values
        List<LogEntry> entries = this.logEntries.getValue();
        if (entries == null) {
            return;
        }
        // Update entry type
        List<LogEntry> updatedEntries = entries.stream()
                .map(entry -> entry.getHost().equals(host) ? new LogEntry(host, type) : entry)
                .collect(Collectors.toList());
        // Post new values
        this.logEntries.postValue(updatedEntries);
    }

    private void sortDnsRequests(LogEntrySort sort) {
        // Save current sort
        this.sort = sort;
        // Apply sort to values
        List<LogEntry> entries = this.logEntries.getValue();
        if (entries != null) {
            List<LogEntry> sortedEntries = new ArrayList<>(entries);
            sortedEntries.sort(this.sort.comparator());
            this.logEntries.postValue(sortedEntries);
        }
        // Notify user
        Toast.makeText(
                getApplication(),
                this.sort.getName(),
                Toast.LENGTH_SHORT
        ).show();
    }
}
