package org.adaway.db;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.adaway.db.dao.HostEntryDao;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.view.HostEntry;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;
import static org.junit.Assert.assertEquals;

/**
 * This class tests the hosts database feature.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
@RunWith(AndroidJUnit4.class)
public class DbTest {
    private static final int EXTERNAL_SOURCE_ID = 2;
    @Rule
    public TestRule rule = new InstantTaskExecutorRule();
    private AppDatabase db;
    private HostsSourceDao hostsSourceDao;
    private HostListItemDao hostListItemDao;
    private HostEntryDao hostEntryDao;

    private static <T> T getOrAwaitValue(final LiveData<T> liveData) throws InterruptedException {
        final Object[] data = new Object[1];
        final CountDownLatch latch = new CountDownLatch(1);
        Observer<T> observer = new Observer<T>() {
            @Override
            public void onChanged(@Nullable T o) {
                data[0] = o;
                latch.countDown();
                liveData.removeObserver(this);
            }
        };
        liveData.observeForever(observer);
        latch.await(2, TimeUnit.SECONDS);
        //noinspection unchecked
        return (T) data[0];
    }

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        this.db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        this.hostsSourceDao = this.db.hostsSourceDao();
        this.hostListItemDao = this.db.hostsListItemDao();
        this.hostEntryDao = this.db.hostEntryDao();
    }

    @After
    public void closeDb() {
        this.db.close();
    }

    @Test
    public void testUserList() throws InterruptedException {
        /*
         * Create sources.
         */
        // Insert user source and external source
        insertSource(USER_SOURCE_ID, "userSource");
        insertSource(EXTERNAL_SOURCE_ID, "sourceUrl");
        // Test only external source is found
        List<HostsSource> sources = this.hostsSourceDao.getAll();
        assertEquals(1, sources.size());
        assertEquals("sourceUrl", sources.get(0).getUrl());

        /*
         * Test blocked hosts.
         */
        // Insert blocked hosts
        insertBlockedHost("advertising.apple.com", USER_SOURCE_ID);
        insertBlockedHost("an.facebook.com", USER_SOURCE_ID);
        insertBlockedHost("ads.google.com", USER_SOURCE_ID);
        insertBlockedHost("bingads.microsoft.com", USER_SOURCE_ID);
        insertBlockedHost("ads.yahoo.com", USER_SOURCE_ID);
        // Test inserted blocked hosts
        LiveData<Integer> blockedHostCount = this.hostListItemDao.getBlockedHostCount();
        assertEquals(5, getOrAwaitValue(blockedHostCount).intValue());
        assertEquals(5, this.hostEntryDao.getAll().size());
        // Test each host type
        assertEquals(BLOCKED, this.hostEntryDao.getTypeOfHost("advertising.apple.com"));
        assertEquals(BLOCKED, this.hostEntryDao.getTypeOfHost("an.facebook.com"));
        assertEquals(BLOCKED, this.hostEntryDao.getTypeOfHost("ads.google.com"));
        assertEquals(BLOCKED, this.hostEntryDao.getTypeOfHost("bingads.microsoft.com"));
        assertEquals(BLOCKED, this.hostEntryDao.getTypeOfHost("ads.yahoo.com"));

        /*
         * Test allowed hosts.
         */
        // Insert allowed hosts
        insertAllowedHost("*.google.com", USER_SOURCE_ID);
        insertAllowedHost("ads.yahoo.com", USER_SOURCE_ID);
        insertAllowedHost("adaway.org", USER_SOURCE_ID);
        // Test inserted allowed hosts
        LiveData<Integer> allowedHostCount = this.hostListItemDao.getAllowedHostCount();
        assertEquals(3, getOrAwaitValue(allowedHostCount).intValue());
        // Test overall list
        assertEquals(5, getOrAwaitValue(blockedHostCount).intValue());
        assertEquals(3, this.hostEntryDao.getAll().size());
        // Test each host type
        assertEquals(ALLOWED, this.hostEntryDao.getTypeOfHost("adaway.org"));
        assertEquals(BLOCKED, this.hostEntryDao.getTypeOfHost("advertising.apple.com"));
        assertEquals(BLOCKED, this.hostEntryDao.getTypeOfHost("an.facebook.com"));
        assertEquals(ALLOWED, this.hostEntryDao.getTypeOfHost("ads.google.com"));
        assertEquals(BLOCKED, this.hostEntryDao.getTypeOfHost("bingads.microsoft.com"));
        assertEquals(ALLOWED, this.hostEntryDao.getTypeOfHost("ads.yahoo.com"));

        /*
         * Test redirected hosts.
         */
        // Insert redirected hosts
        insertRedirectedHost("ads.yahoo.com", "1.2.3.4", USER_SOURCE_ID);
        insertRedirectedHost("github.com", "1.2.3.4", USER_SOURCE_ID);
        // Test inserted redirected hosts
        LiveData<Integer> redirectedHostCount = this.hostListItemDao.getRedirectHostCount();
        assertEquals(2, getOrAwaitValue(redirectedHostCount).intValue());
        // Test overall list
        assertEquals(5, getOrAwaitValue(blockedHostCount).intValue());
        assertEquals(3, getOrAwaitValue(allowedHostCount).intValue());
        assertEquals(5, this.hostEntryDao.getAll().size()); // 3 blocked, 2 redirected
        // Test each host type
        assertEquals(ALLOWED, this.hostEntryDao.getTypeOfHost("adaway.org"));
        assertEquals(BLOCKED, this.hostEntryDao.getTypeOfHost("advertising.apple.com"));
        assertEquals(BLOCKED, this.hostEntryDao.getTypeOfHost("an.facebook.com"));
        assertEquals(REDIRECTED, this.hostEntryDao.getTypeOfHost("github.com"));
        assertEquals(ALLOWED, this.hostEntryDao.getTypeOfHost("ads.google.com"));
        assertEquals(BLOCKED, this.hostEntryDao.getTypeOfHost("bingads.microsoft.com"));
        assertEquals(REDIRECTED, this.hostEntryDao.getTypeOfHost("ads.yahoo.com"));
    }

    @Test
    public void testDuplicateEntries() throws InterruptedException {
        /*
         * Create sources.
         */
        // Insert user source and external source
        insertSource(USER_SOURCE_ID, "userSource");
        insertSource(EXTERNAL_SOURCE_ID, "sourceUrl");
        // Test only external source is found
        List<HostsSource> sources = this.hostsSourceDao.getAll();
        assertEquals(1, sources.size());
        assertEquals("sourceUrl", sources.get(0).getUrl());

        /*
         * Test duplicate blocked hosts.
         */
        insertBlockedHost("advertising.apple.com", USER_SOURCE_ID);
        insertBlockedHost("advertising.apple.com", EXTERNAL_SOURCE_ID);
        LiveData<Integer> blockedHostCount = this.hostListItemDao.getBlockedHostCount();
        assertEquals(1, getOrAwaitValue(blockedHostCount).intValue());
        assertEquals(1, this.hostEntryDao.getAll().size());

        /*
         * Test duplicate allowed hosts.
         */
        insertAllowedHost("adaway.org", USER_SOURCE_ID);
        insertAllowedHost("adaway.org", EXTERNAL_SOURCE_ID);
        LiveData<Integer> allowedHostCount = this.hostListItemDao.getAllowedHostCount();
        assertEquals(1, getOrAwaitValue(allowedHostCount).intValue());
        assertEquals(1, this.hostEntryDao.getAll().size());

        /*
         * Test duplicate redirected hosts.
         */
        insertRedirectedHost("github.com", "1.1.1.1", USER_SOURCE_ID);
        insertRedirectedHost("github.com", "2.2.2.2", EXTERNAL_SOURCE_ID);
        LiveData<Integer> redirectHostCount = this.hostListItemDao.getRedirectHostCount();
        assertEquals(1, getOrAwaitValue(redirectHostCount).intValue());
        assertEquals(2, this.hostEntryDao.getAll().size());
    }

    @Test
    public void testDisabledSources() throws InterruptedException {
        /*
         * Create sources.
         */
        // Insert user source and external source
        insertSource(USER_SOURCE_ID, "userSource");
        insertSource(EXTERNAL_SOURCE_ID, "sourceUrl");
        HostsSource extenalHostSource = getSourceFromId(EXTERNAL_SOURCE_ID);
        // Test only external source is found
        List<HostsSource> sources = this.hostsSourceDao.getAll();
        assertEquals(1, sources.size());
        assertEquals("sourceUrl", sources.get(0).getUrl());

        /*
         * Test blocked hosts from disabled sources.
         */
        // Insert blocked hosts
        insertBlockedHost("advertising.apple.com", USER_SOURCE_ID);
        insertBlockedHost("an.facebook.com", USER_SOURCE_ID);
        insertBlockedHost("ads.google.com", USER_SOURCE_ID);
        insertBlockedHost("bingads.microsoft.com", EXTERNAL_SOURCE_ID);
        insertBlockedHost("ads.yahoo.com", EXTERNAL_SOURCE_ID);
        // Test inserted blocked hosts
        LiveData<Integer> blockedHostCount = this.hostListItemDao.getBlockedHostCount();
        assertEquals(5, getOrAwaitValue(blockedHostCount).intValue());
        assertEquals(5, this.hostEntryDao.getAll().size());
        // Disabled a source
        this.hostsSourceDao.toggleEnabled(extenalHostSource);
        assertEquals(3, getOrAwaitValue(blockedHostCount).intValue());
        assertEquals(3, this.hostEntryDao.getAll().size());
        // Re-enable a source
        this.hostsSourceDao.toggleEnabled(extenalHostSource);
        assertEquals(5, getOrAwaitValue(blockedHostCount).intValue());
        assertEquals(5, this.hostEntryDao.getAll().size());

        /*
         * Test allowed hosts from disabled sources.
         */
        // Insert blocked hosts
        insertBlockedHost("adaway.org", USER_SOURCE_ID);
        insertAllowedHost("adaway.org", EXTERNAL_SOURCE_ID);
        // Test inserted blocked hosts
        LiveData<Integer> allowedHostCount = this.hostListItemDao.getAllowedHostCount();
        assertEquals(6, getOrAwaitValue(blockedHostCount).intValue());
        assertEquals(1, getOrAwaitValue(allowedHostCount).intValue());
        assertEquals(5, this.hostEntryDao.getAll().size());
        // Disabled a source
        this.hostsSourceDao.toggleEnabled(extenalHostSource);
        assertEquals(4, getOrAwaitValue(blockedHostCount).intValue());
        assertEquals(0, getOrAwaitValue(allowedHostCount).intValue());
        assertEquals(4, this.hostEntryDao.getAll().size());
        // Re-enable a source
        this.hostsSourceDao.toggleEnabled(extenalHostSource);
        assertEquals(6, getOrAwaitValue(blockedHostCount).intValue());
        assertEquals(1, getOrAwaitValue(allowedHostCount).intValue());
        assertEquals(5, this.hostEntryDao.getAll().size());

        /*
         * Test redirected hosts from disabled sources.
         */
        // Insert blocked hosts
        insertRedirectedHost("github.com", "1.1.1.1", USER_SOURCE_ID);
        insertRedirectedHost("github.com", "2.2.2.2", EXTERNAL_SOURCE_ID);
        // Test inserted blocked hosts
        LiveData<Integer> redirectHostCount = this.hostListItemDao.getRedirectHostCount();
        assertEquals(6, getOrAwaitValue(blockedHostCount).intValue());
        assertEquals(1, getOrAwaitValue(allowedHostCount).intValue());
        assertEquals(1, getOrAwaitValue(redirectHostCount).intValue());
        assertEquals(6, this.hostEntryDao.getAll().size());
        // Disabled a source
        this.hostsSourceDao.toggleEnabled(extenalHostSource);
        assertEquals(4, getOrAwaitValue(blockedHostCount).intValue());
        assertEquals(0, getOrAwaitValue(allowedHostCount).intValue());
        assertEquals(5, this.hostEntryDao.getAll().size());
        // Re-enable a source
        this.hostsSourceDao.toggleEnabled(extenalHostSource);
        assertEquals(6, getOrAwaitValue(blockedHostCount).intValue());
        assertEquals(1, getOrAwaitValue(allowedHostCount).intValue());
        assertEquals(1, getOrAwaitValue(redirectHostCount).intValue());
        assertEquals(6, this.hostEntryDao.getAll().size());
    }

    @Test
    public void testRedirectionPriority() throws InterruptedException {
        /*
         * Create sources.
         */
        // Insert user source and external source
        insertSource(USER_SOURCE_ID, "userSource");
        insertSource(EXTERNAL_SOURCE_ID, "sourceUrl");

        /*
         * Insert redirected hosts.
         */
        // Insert two redirects for the same host
        insertRedirectedHost("adaway.org", "1.1.1.1", USER_SOURCE_ID);
        insertRedirectedHost("adaway.org", "2.2.2.2", EXTERNAL_SOURCE_ID);
        // Test inserted redirected hosts
        LiveData<Integer> redirectedHostCount = this.hostListItemDao.getRedirectHostCount();
        assertEquals(1, getOrAwaitValue(redirectedHostCount).intValue());
        // Test inserted redirect
        List<HostEntry> entries = this.hostEntryDao.getAll();
        assertEquals(1, entries.size());
        assertEquals("1.1.1.1", entries.get(0).getRedirection()); // User redirection must be apply
    }

    private void insertSource(int id, String url) {
        HostsSource source1 = new HostsSource();
        source1.setId(id);
        source1.setUrl(url);
        source1.setEnabled(true);
        this.hostsSourceDao.insert(source1);
    }

    private void insertBlockedHost(String host, int sourceId) {
        HostListItem item = new HostListItem();
        item.setType(BLOCKED);
        item.setHost(host);
        item.setEnabled(true);
        item.setSourceId(sourceId);
        this.hostListItemDao.insert(item);
    }

    private void insertAllowedHost(String host, int sourceId) {
        HostListItem item = new HostListItem();
        item.setType(ALLOWED);
        item.setDisplayedHost(host);
        item.setEnabled(true);
        item.setSourceId(sourceId);
        this.hostListItemDao.insert(item);
    }

    private void insertRedirectedHost(String host, String redirection, int sourceId) {
        HostListItem item = new HostListItem();
        item.setType(REDIRECTED);
        item.setDisplayedHost(host);
        item.setEnabled(true);
        item.setRedirection(redirection);
        item.setSourceId(sourceId);
        this.hostListItemDao.insert(item);
    }

    private HostsSource getSourceFromId(int id) {
        return this.hostsSourceDao.getAll()
                .stream()
                .filter(hostsSource -> hostsSource.getId() == id)
                .findAny()
                .orElse(null);
    }
}
