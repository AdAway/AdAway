package org.adaway.db;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.adaway.db.entity.HostEntry;
import org.junit.Test;

/**
 * This class the user lists use case where user can freely add blocked, allowed and redirected hosts.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class UserListTest extends DbTest {
    @Test
    public void testUserList() throws InterruptedException {
        testUserBlockedHosts();
        testUserAllowedHosts();
        testUserRedirectedHosts();
    }

    protected void testUserBlockedHosts() throws InterruptedException {
        // Insert blocked hosts
        insertBlockedHost("advertising.apple.com", USER_SOURCE_ID);
        insertBlockedHost("an.facebook.com", USER_SOURCE_ID);
        insertBlockedHost("ads.google.com", USER_SOURCE_ID);
        insertBlockedHost("bingads.microsoft.com", USER_SOURCE_ID);
        insertBlockedHost("ads.yahoo.com", USER_SOURCE_ID);
        this.hostEntryDao.sync();
        // Test inserted blocked hosts
        assertEquals(5, getOrAwaitValue(this.blockedHostCount).intValue());
        assertEquals(5, this.hostEntryDao.getAll().size());
        // Test each host type
        assertEquals(BLOCKED, this.hostEntryDao.getTypeForHost("advertising.apple.com"));
        HostEntry entry = this.hostEntryDao.getEntry("advertising.apple.com");
        assertNotNull(entry);
        assertEquals("advertising.apple.com", entry.getHost());
        assertEquals(BLOCKED, entry.getType());
        assertEquals(BLOCKED, this.hostEntryDao.getTypeForHost("an.facebook.com"));
        entry = this.hostEntryDao.getEntry("an.facebook.com");
        assertNotNull(entry);
        assertEquals("an.facebook.com", entry.getHost());
        assertEquals(BLOCKED, entry.getType());
        assertEquals(BLOCKED, this.hostEntryDao.getTypeForHost("ads.google.com"));
        entry = this.hostEntryDao.getEntry("ads.google.com");
        assertNotNull(entry);
        assertEquals("ads.google.com", entry.getHost());
        assertEquals(BLOCKED, entry.getType());
        assertEquals(BLOCKED, this.hostEntryDao.getTypeForHost("bingads.microsoft.com"));
        entry = this.hostEntryDao.getEntry("bingads.microsoft.com");
        assertNotNull(entry);
        assertEquals("bingads.microsoft.com", entry.getHost());
        assertEquals(BLOCKED, entry.getType());
        assertEquals(BLOCKED, this.hostEntryDao.getTypeForHost("ads.yahoo.com"));
        entry = this.hostEntryDao.getEntry("ads.yahoo.com");
        assertNotNull(entry);
        assertEquals("ads.yahoo.com", entry.getHost());
        assertEquals(BLOCKED, entry.getType());
    }

    protected void testUserAllowedHosts() throws InterruptedException {
        // Insert allowed hosts
        insertAllowedHost("*.google.com", USER_SOURCE_ID);
        insertAllowedHost("ads.yahoo.com", USER_SOURCE_ID);
        insertAllowedHost("adaway.org", USER_SOURCE_ID);
        this.hostEntryDao.sync();
        // Test inserted allowed hosts
        assertEquals(3, getOrAwaitValue(this.allowedHostCount).intValue());
        // Test overall list
        assertEquals(5, getOrAwaitValue(this.blockedHostCount).intValue());
        assertEquals(3, this.hostEntryDao.getAll().size());
        // Test each host type
        assertEquals(ALLOWED, this.hostEntryDao.getTypeForHost("adaway.org"));
        HostEntry entry = this.hostEntryDao.getEntry("adaway.org");
        assertNull(entry);
        assertEquals(BLOCKED, this.hostEntryDao.getTypeForHost("advertising.apple.com"));
        entry = this.hostEntryDao.getEntry("advertising.apple.com");
        assertNotNull(entry);
        assertEquals("advertising.apple.com", entry.getHost());
        assertEquals(BLOCKED, entry.getType());
        assertEquals(BLOCKED, this.hostEntryDao.getTypeForHost("an.facebook.com"));
        entry = this.hostEntryDao.getEntry("an.facebook.com");
        assertNotNull(entry);
        assertEquals("an.facebook.com", entry.getHost());
        assertEquals(BLOCKED, entry.getType());
        assertEquals(ALLOWED, this.hostEntryDao.getTypeForHost("ads.google.com"));
        entry = this.hostEntryDao.getEntry("ads.google.com");
        assertNull(entry);
        assertEquals(BLOCKED, this.hostEntryDao.getTypeForHost("bingads.microsoft.com"));
        entry = this.hostEntryDao.getEntry("bingads.microsoft.com");
        assertNotNull(entry);
        assertEquals("bingads.microsoft.com", entry.getHost());
        assertEquals(BLOCKED, entry.getType());
        assertEquals(ALLOWED, this.hostEntryDao.getTypeForHost("ads.yahoo.com"));
        entry = this.hostEntryDao.getEntry("ads.yahoo.com");
        assertNull(entry);

    }

    protected void testUserRedirectedHosts() throws InterruptedException {
        // Insert redirected hosts
        insertRedirectedHost("ads.yahoo.com", "1.2.3.4", USER_SOURCE_ID);
        insertRedirectedHost("github.com", "1.2.3.4", USER_SOURCE_ID);
        this.hostEntryDao.sync();
        // Test inserted redirected hosts
        assertEquals(2, getOrAwaitValue(this.redirectedHostCount).intValue());
        // Test overall list
        assertEquals(5, getOrAwaitValue(this.blockedHostCount).intValue());
        assertEquals(3, getOrAwaitValue(this.allowedHostCount).intValue());
        assertEquals(5, this.hostEntryDao.getAll().size()); // 3 blocked, 2 redirected
        // Test each host type
        assertEquals(ALLOWED, this.hostEntryDao.getTypeForHost("adaway.org"));
        HostEntry entry = this.hostEntryDao.getEntry("adaway.org");
        assertNull(entry);

        assertEquals(BLOCKED, this.hostEntryDao.getTypeForHost("advertising.apple.com"));
        entry = this.hostEntryDao.getEntry("advertising.apple.com");
        assertNotNull(entry);
        assertEquals("advertising.apple.com", entry.getHost());
        assertEquals(BLOCKED, entry.getType());

        assertEquals(BLOCKED, this.hostEntryDao.getTypeForHost("an.facebook.com"));
        entry = this.hostEntryDao.getEntry("an.facebook.com");
        assertNotNull(entry);
        assertEquals("an.facebook.com", entry.getHost());
        assertEquals(BLOCKED, entry.getType());

        assertEquals(REDIRECTED, this.hostEntryDao.getTypeForHost("github.com"));
        entry = this.hostEntryDao.getEntry("github.com");
        assertNotNull(entry);
        assertEquals("github.com", entry.getHost());
        assertEquals(REDIRECTED, entry.getType());
        assertEquals("1.2.3.4", entry.getRedirection());

        assertEquals(ALLOWED, this.hostEntryDao.getTypeForHost("ads.google.com"));
        entry = this.hostEntryDao.getEntry("ads.google.com");
        assertNull(entry);

        assertEquals(BLOCKED, this.hostEntryDao.getTypeForHost("bingads.microsoft.com"));
        entry = this.hostEntryDao.getEntry("bingads.microsoft.com");
        assertNotNull(entry);
        assertEquals("bingads.microsoft.com", entry.getHost());
        assertEquals(BLOCKED, entry.getType());

        assertEquals(REDIRECTED, this.hostEntryDao.getTypeForHost("ads.yahoo.com"));
        entry = this.hostEntryDao.getEntry("ads.yahoo.com");
        assertNotNull(entry);
        assertEquals("ads.yahoo.com", entry.getHost());
        assertEquals(REDIRECTED, entry.getType());
        assertEquals("1.2.3.4", entry.getRedirection());
    }
}
