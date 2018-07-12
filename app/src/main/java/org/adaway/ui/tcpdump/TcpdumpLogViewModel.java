package org.adaway.ui.tcpdump;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.database.sqlite.SQLiteConstraintException;
import android.support.annotation.NonNull;

import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.ListType;
import org.adaway.util.AppExecutors;
import org.adaway.util.Constants;
import org.adaway.util.Log;

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
        this.sortDnsRequests(this.sort == LogEntrySort.ALPHA_NUMERIC ?
                LogEntrySort.TOP_LEVEL_DOMAIN :
                LogEntrySort.ALPHA_NUMERIC
        );
        // TODO Display toast?
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

    public void addListItem(@NonNull ListType type, @NonNull String host, String redirection) {
        // Create new host list item
        HostListItem item = new HostListItem();
        item.setType(type);
        item.setHost(host);
        item.setRedirection(redirection);
        item.setEnabled(true);
        // Insert host list item
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                this.hostListItemDao.insert(item);
            } catch (SQLiteConstraintException exception) {
                Log.w(Constants.TAG, "Unable to add duplicate list item: " + item + ".", exception);
            }
        });
        // Update log entries
        List<LogEntry> entries = this.logEntries.getValue();
        if (entries != null) {
            List<LogEntry> updatedEntries = new ArrayList<>(entries.size());
            for (int i = 0; i < entries.size(); i++) {
                LogEntry entry = entries.get(i);
                if (entry.getHost().equals(host)) {
                    updatedEntries.add(i, new LogEntry(host + "test", type));
                } else {
                    updatedEntries.add(entry);
                }
            }
            this.logEntries.postValue(updatedEntries);
        }
    }

    private void sortDnsRequests(LogEntrySort sort) {
        this.sort = sort;
        List<LogEntry> entries = this.logEntries.getValue();
        if (entries != null) {
            List<LogEntry> sortedEntries = new ArrayList<>(entries);
            Collections.sort(sortedEntries, this.sort.comparator());
            this.logEntries.postValue(sortedEntries);
        }
    }
}
