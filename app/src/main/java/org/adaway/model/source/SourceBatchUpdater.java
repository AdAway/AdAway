package org.adaway.model.source;

import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;

import java.util.Map;

import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;

/**
 * This class is a tool to do batch update into the database.<br>
 * It allows faster insertion by using only one transaction for a batch insert.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class SourceBatchUpdater {
    private static final int BATCH_SIZE = 100;
    private final HostListItemDao hostListItemDao;

    SourceBatchUpdater(HostListItemDao hostListItemDao) {
        this.hostListItemDao = hostListItemDao;
    }

    void updateSource(HostsSource source, SourceParser parser) {
        // Clear current hosts
        int sourceId = source.getId();
        this.hostListItemDao.clearSourceHosts(sourceId);
        // Create batch
        HostListItem[] batch = new HostListItem[BATCH_SIZE];
        int cacheSize = 0;
        // Insert blocked hosts
        for (String host : parser.getBlockedHosts()) {
            batch[cacheSize++] = getBlockedHostListItem(sourceId, host);
            if (cacheSize >= batch.length) {
                this.hostListItemDao.insert(batch);
                cacheSize = 0;
            }
        }
        // Insert redirected hosts
        for (Map.Entry<String, String> redirectedHost : parser.getRedirectedHosts().entrySet()) {
            String host = redirectedHost.getKey();
            String redirection = redirectedHost.getValue();
            batch[cacheSize++] = getRedirectedHostListItem(sourceId, host, redirection);
            if (cacheSize >= batch.length) {
                this.hostListItemDao.insert(batch);
                cacheSize = 0;
            }
        }
        // Flush current batch
        HostListItem[] remaining = new HostListItem[cacheSize];
        System.arraycopy(batch, 0, remaining, 0, remaining.length);
        this.hostListItemDao.insert(remaining);
    }

    private HostListItem getBlockedHostListItem(int sourceId, String host) {
        HostListItem item = new HostListItem();
        item.setHost(host);
        item.setType(BLOCKED);
        item.setEnabled(true);
        item.setSourceId(sourceId);
        return item;
    }

    private HostListItem getRedirectedHostListItem(int sourceId, String host, String redirection) {
        HostListItem item = new HostListItem();
        item.setHost(host);
        item.setType(REDIRECTED);
        item.setEnabled(true);
        item.setRedirection(redirection);
        item.setSourceId(sourceId);
        return item;
    }
}
