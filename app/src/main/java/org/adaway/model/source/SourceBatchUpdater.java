package org.adaway.model.source;

import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;

import java.util.Collection;

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

    void updateSource(HostsSource source, Collection<HostListItem> items) {
        // Clear current hosts
        int sourceId = source.getId();
        this.hostListItemDao.clearSourceHosts(sourceId);
        // Create batch
        HostListItem[] batch = new HostListItem[BATCH_SIZE];
        int cacheSize = 0;
        // Insert parsed items
        for (HostListItem item : items) {
            batch[cacheSize++] = item;
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
}
