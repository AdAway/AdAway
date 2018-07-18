package org.adaway.ui.tcpdump;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.support.annotation.NonNull;
import android.widget.Toast;

import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.ListType;
import org.adaway.util.AppExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is an {@link AndroidViewModel} for the {@link TcpdumpLogActivity}.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class TcpdumpLogViewModel extends AndroidViewModel {
    /**
     * The {@link HostListItem} DAO.
     */
    private final HostListItemDao hostListItemDao;
    /**
     * The tcpdump log entries (wrapped into {@link LiveData}.
     */
    private final MutableLiveData<List<LogEntry>> logEntries;
    /**
     * The current log entry sort.
     */
    private LogEntrySort sort;

    public TcpdumpLogViewModel(@NonNull Application application) {
        super(application);
        this.hostListItemDao = AppDatabase.getInstance(this.getApplication()).hostsListItemDao();
        this.logEntries = new MutableLiveData<>();
        this.sort = LogEntrySort.TOP_LEVEL_DOMAIN;
    }

    public LiveData<List<LogEntry>> getLogEntries() {
        return this.logEntries;
    }

    public void toggleSort() {
        this.sortDnsRequests(this.sort == LogEntrySort.ALPHABETICAL ?
                LogEntrySort.TOP_LEVEL_DOMAIN :
                LogEntrySort.ALPHABETICAL
        );
    }

    public void updateDnsRequests() {
        AppExecutors.getInstance().diskIO().execute(
                () -> {
                    // Get tcpdump logs
                    List<String> logs = TcpdumpUtils.getLogs(this.getApplication());
                    // Create lookup table of host list item by host name
                    List<HostListItem> hostListItems = this.hostListItemDao.getAll();
                    Map<String, HostListItem> hosts = new HashMap<>();
                    for (HostListItem hostListItem : hostListItems) {
                        hosts.put(hostListItem.getHost(), hostListItem);
                    }
                    // Create log entry collection
                    List<LogEntry> logItems = new ArrayList<>(logs.size());
                    for (String log : logs) {
                        ListType type = null;
                        HostListItem hostListItem = hosts.get(log);
                        if (hostListItem != null) {
                            type = hostListItem.getType();
                        }
                        logItems.add(new LogEntry(log, type));
                    }
                    // Sort entries
                    Collections.sort(logItems, this.sort.comparator());
                    // Post result
                    this.logEntries.postValue(logItems);
                }
        );
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
        // Create new collection
        List<LogEntry> updatedEntries = new ArrayList<>(entries);
        // Update entry type
        for (int i = 0; i < entries.size(); i++) {
            LogEntry entry = entries.get(i);
            if (entry.getHost().equals(host)) {
                updatedEntries.set(i, new LogEntry(host, type));
            }
        }
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
                this.getApplication(),
                this.sort.getName(),
                Toast.LENGTH_SHORT
        ).show();
    }
}
