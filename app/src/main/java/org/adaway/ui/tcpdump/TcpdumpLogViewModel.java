package org.adaway.ui.tcpdump;

import android.app.Application;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.adaway.AdAwayApplication;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.ListType;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.util.AppExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * This class is an {@link AndroidViewModel} for the {@link TcpdumpLogActivity}.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class TcpdumpLogViewModel extends AndroidViewModel {
    private final AdBlockModel adBlockModel;
    private final HostListItemDao hostListItemDao;
    private final MutableLiveData<List<LogEntry>> logEntries;
    private final MutableLiveData<Boolean> recording;
    private LogEntrySort sort;

    public TcpdumpLogViewModel(@NonNull Application application) {
        super(application);
        this.adBlockModel = ((AdAwayApplication) application).getAdBlockModel();
        this.hostListItemDao = AppDatabase.getInstance(this.getApplication()).hostsListItemDao();
        this.logEntries = new MutableLiveData<>();
        this.recording = new MutableLiveData<>(this.adBlockModel.isRecordingLogs());
        this.sort = LogEntrySort.TOP_LEVEL_DOMAIN;
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
                    List<String> logs = this.adBlockModel.getLogs();
                    // Create lookup table of host list item by host name
                    Map<String, HostListItem> hosts = Stream.of(this.hostListItemDao.getAll())
                            .collect(Collectors.toMap(HostListItem::getHost));
                    // Create log entry collection
                    List<LogEntry> logItems = Stream.of(logs)
                            .map(log -> {
                                ListType type = null;
                                HostListItem hostListItem = hosts.get(log);
                                if (hostListItem != null) {
                                    type = hostListItem.getType();
                                }
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
        item.setHost(host);
        item.setType(type);
        item.setRedirection(redirection);
        item.setEnabled(true);
        // Insert host list item
        AppExecutors.getInstance().diskIO().execute(() -> this.hostListItemDao.insert(item));
        // Update log entries
        updateLogEntryType(host, type);
    }

    public void removeListItem(@NonNull String host) {
        // Create new host list item to delete
        HostListItem item = new HostListItem();
        item.setHost(host);
        // Insert host list item
        AppExecutors.getInstance().diskIO().execute(() -> this.hostListItemDao.delete(item));
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
        List<LogEntry> updatedEntries = Stream.of(entries)
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
            Collections.sort(sortedEntries, this.sort.comparator());
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
