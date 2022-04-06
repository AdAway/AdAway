package org.adaway.db;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.junit.Assert.assertEquals;

import org.adaway.db.entity.HostsSource;
import org.junit.Test;

import java.util.List;

/**
 * This class tests {@link HostsSource} database manipulations.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class SourceDbTest extends DbTest {
    @Test
    public void testSourceCount() {
        // Test only external source is found
        List<HostsSource> sources = this.hostsSourceDao.getAll();
        assertEquals(1, sources.size());
        assertEquals("https://adaway.org/hosts.txt", sources.get(0).getUrl());
    }

    @Test
    public void testSourceDeletion() throws InterruptedException {
        // Insert blocked hosts
        insertBlockedHost("bingads.microsoft.com", EXTERNAL_SOURCE_ID);
        insertBlockedHost("ads.yahoo.com", EXTERNAL_SOURCE_ID);
        this.hostEntryDao.sync();
        // Test inserted blocked hosts
        assertEquals(2, getOrAwaitValue(this.blockedHostCount).intValue());
        assertEquals(2, this.hostEntryDao.getAll().size());
        // Delete source
        this.hostsSourceDao.delete(this.externalHostSource);
        this.hostEntryDao.sync();
        List<HostsSource> sources = this.hostsSourceDao.getAll();
        assertEquals(0, sources.size());
        // Check related hosts cleaning
        assertEquals(0, getOrAwaitValue(this.blockedHostCount).intValue());
        assertEquals(0, this.hostEntryDao.getAll().size());

    }

    @Test
    public void testBlockedHostsFromDisabledSource() throws InterruptedException {
        // Insert blocked hosts
        insertBlockedHost("advertising.apple.com", USER_SOURCE_ID);
        insertBlockedHost("an.facebook.com", USER_SOURCE_ID);
        insertBlockedHost("ads.google.com", USER_SOURCE_ID);
        insertBlockedHost("bingads.microsoft.com", EXTERNAL_SOURCE_ID);
        insertBlockedHost("ads.yahoo.com", EXTERNAL_SOURCE_ID);
        this.hostEntryDao.sync();
        // Test inserted blocked hosts
        assertEquals(5, getOrAwaitValue(this.blockedHostCount).intValue());
        assertEquals(5, this.hostEntryDao.getAll().size());
        // Disabled external source
        this.hostsSourceDao.toggleEnabled(this.externalHostSource);
        this.hostEntryDao.sync();
        assertEquals(3, getOrAwaitValue(this.blockedHostCount).intValue());
        assertEquals(3, this.hostEntryDao.getAll().size());
        // Re-enable external source
        this.hostsSourceDao.toggleEnabled(this.externalHostSource);
        this.hostEntryDao.sync();
        assertEquals(5, getOrAwaitValue(this.blockedHostCount).intValue());
        assertEquals(5, this.hostEntryDao.getAll().size());
    }

    @Test
    public void testAllowedHostsFromDisabledSource() throws InterruptedException {
        // Insert blocked and allowed host
        insertBlockedHost("adaway.org", USER_SOURCE_ID);
        insertAllowedHost("adaway.org", EXTERNAL_SOURCE_ID);
        this.hostEntryDao.sync();
        // Test inserted blocked hosts
        assertEquals(1, getOrAwaitValue(this.blockedHostCount).intValue());
        assertEquals(1, getOrAwaitValue(this.allowedHostCount).intValue());
        assertEquals(0, this.hostEntryDao.getAll().size());
        // Disabled a source
        this.hostsSourceDao.toggleEnabled(this.externalHostSource);
        this.hostEntryDao.sync();
        assertEquals(1, getOrAwaitValue(this.blockedHostCount).intValue());
        assertEquals(0, getOrAwaitValue(this.allowedHostCount).intValue());
        assertEquals(1, this.hostEntryDao.getAll().size());
        // Re-enable a source
        this.hostsSourceDao.toggleEnabled(this.externalHostSource);
        this.hostEntryDao.sync();
        assertEquals(1, getOrAwaitValue(this.blockedHostCount).intValue());
        assertEquals(1, getOrAwaitValue(this.allowedHostCount).intValue());
        assertEquals(0, this.hostEntryDao.getAll().size());
    }

    @Test
    public void testRedirectedHostsFromDisabledSource() throws InterruptedException {
        // Insert redirected hosts
        insertRedirectedHost("github.com", "1.1.1.1", USER_SOURCE_ID);
        insertRedirectedHost("github.com", "2.2.2.2", EXTERNAL_SOURCE_ID);
        this.hostEntryDao.sync();
        // Test inserted blocked hosts
        assertEquals(1, getOrAwaitValue(this.redirectedHostCount).intValue());
        assertEquals(1, this.hostEntryDao.getAll().size());
        // Disabled a source
        this.hostsSourceDao.toggleEnabled(this.externalHostSource);
        this.hostEntryDao.sync();
        assertEquals(1, getOrAwaitValue(this.redirectedHostCount).intValue());
        assertEquals(1, this.hostEntryDao.getAll().size());
        // Re-enable a source
        this.hostsSourceDao.toggleEnabled(this.externalHostSource);
        this.hostEntryDao.sync();
        assertEquals(1, getOrAwaitValue(this.redirectedHostCount).intValue());
        assertEquals(1, this.hostEntryDao.getAll().size());
    }
}
