package org.adaway.db;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.adaway.db.entity.HostEntry;
import org.junit.Test;

import java.util.List;

/**
 * This class tests {@link org.adaway.db.entity.HostListItem} database manipulations.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class HostDbTest extends DbTest {
    @Test
    public void testEmptyByDefault() throws InterruptedException {
        assertEquals(0, getOrAwaitValue(this.blockedHostCount).intValue());
        assertEquals(0, getOrAwaitValue(this.allowedHostCount).intValue());
        assertEquals(0, getOrAwaitValue(this.redirectedHostCount).intValue());
        assertEquals(0, this.hostEntryDao.getAll().size());
    }

    @Test
    public void testInsertThenDeleteHosts() throws InterruptedException {
        // Insert blocked hosts
        insertBlockedHost("advertising.apple.com", USER_SOURCE_ID);
        insertBlockedHost("an.facebook.com", USER_SOURCE_ID);
        this.hostEntryDao.sync();
        // Check inserting
        assertEquals(2, getOrAwaitValue(this.blockedHostCount).intValue());
        assertEquals(0, getOrAwaitValue(this.allowedHostCount).intValue());
        assertEquals(0, getOrAwaitValue(this.redirectedHostCount).intValue());
        assertEquals(2, this.hostEntryDao.getAll().size());
        // Remove block hosts
        this.hostListItemDao.deleteUserFromHost("advertising.apple.com");
        this.hostEntryDao.sync();
        // Check deletion
        assertEquals(1, getOrAwaitValue(this.blockedHostCount).intValue());
        assertEquals(0, getOrAwaitValue(this.allowedHostCount).intValue());
        assertEquals(0, getOrAwaitValue(this.redirectedHostCount).intValue());
        assertEquals(1, this.hostEntryDao.getAll().size());
    }

    @Test
    public void testDuplicateBlockedHosts() throws InterruptedException {
        insertBlockedHost("advertising.apple.com", USER_SOURCE_ID);
        insertBlockedHost("advertising.apple.com", EXTERNAL_SOURCE_ID);
        this.hostEntryDao.sync();
        assertEquals(1, getOrAwaitValue(this.blockedHostCount).intValue());
        assertEquals(1, this.hostEntryDao.getAll().size());
    }

    @Test
    public void testDuplicateAllowedHosts() throws InterruptedException {
        insertAllowedHost("adaway.org", USER_SOURCE_ID);
        insertAllowedHost("adaway.org", EXTERNAL_SOURCE_ID);
        this.hostEntryDao.sync();
        assertEquals(1, getOrAwaitValue(this.allowedHostCount).intValue());
        assertEquals(0, this.hostEntryDao.getAll().size());
    }

    @Test
    public void testDuplicateRedirectedHosts() throws InterruptedException {
        insertRedirectedHost("github.com", "1.1.1.1", USER_SOURCE_ID);
        insertRedirectedHost("github.com", "2.2.2.2", EXTERNAL_SOURCE_ID);
        this.hostEntryDao.sync();
        assertEquals(1, getOrAwaitValue(this.redirectedHostCount).intValue());
        assertEquals(1, this.hostEntryDao.getAll().size());
    }

    @Test
    public void testRedirectionPriority() throws InterruptedException {
        // Insert two redirects for the same host
        insertRedirectedHost("adaway.org", "1.1.1.1", USER_SOURCE_ID);
        insertRedirectedHost("adaway.org", "2.2.2.2", EXTERNAL_SOURCE_ID);
        this.hostEntryDao.sync();
        // Test inserted redirected hosts
        assertEquals(1, getOrAwaitValue(this.redirectedHostCount).intValue());
        // Test inserted redirect
        List<HostEntry> entries = this.hostEntryDao.getAll();
        assertEquals(1, entries.size());
        // Test user redirect is applied in priority
        HostEntry entry = this.hostEntryDao.getEntry("adaway.org");
        assertNotNull(entry);
        assertEquals("1.1.1.1", entry.getRedirection());
    }
}
