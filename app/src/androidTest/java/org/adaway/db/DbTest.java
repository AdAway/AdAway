package org.adaway.db;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.db.entity.HostsSource.USER_SOURCE_URL;
import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;
import static org.junit.Assert.fail;

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
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * This class is a base class for testing database feature.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
@RunWith(AndroidJUnit4.class)
public abstract class DbTest {
    protected static final int EXTERNAL_SOURCE_ID = 2;
    @Rule
    public TestRule rule = new InstantTaskExecutorRule();
    protected AppDatabase db;
    protected HostsSourceDao hostsSourceDao;
    protected HostListItemDao hostListItemDao;
    protected HostEntryDao hostEntryDao;
    protected LiveData<Integer> blockedHostCount;
    protected LiveData<Integer> allowedHostCount;
    protected LiveData<Integer> redirectedHostCount;
    protected HostsSource externalHostSource;

    protected static <T> T getOrAwaitValue(final LiveData<T> liveData) throws InterruptedException {
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
        if (!latch.await(2, TimeUnit.SECONDS)) {
            fail("Failed to get LiveData value in time");
        }
        //noinspection unchecked
        return (T) data[0];
    }

    @Before
    public void init() {
        createDb();
        loadDao();
        createSources();
    }

    protected void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        this.db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    protected void loadDao() {
        this.hostsSourceDao = this.db.hostsSourceDao();
        this.hostListItemDao = this.db.hostsListItemDao();
        this.hostEntryDao = this.db.hostEntryDao();
        this.blockedHostCount = this.hostListItemDao.getBlockedHostCount();
        this.allowedHostCount = this.hostListItemDao.getAllowedHostCount();
        this.redirectedHostCount = this.hostListItemDao.getRedirectHostCount();
    }

    protected void createSources() {
        // Insert at least user source and external source to allow duplicate hosts to be inserted
        insertSource(USER_SOURCE_ID, USER_SOURCE_URL);
        insertSource(EXTERNAL_SOURCE_ID, "https://adaway.org/hosts.txt");
        this.externalHostSource = getSourceFromId(EXTERNAL_SOURCE_ID);
    }

    @After
    public void closeDb() {
        this.db.close();
    }

    protected void insertSource(int id, String url) {
        HostsSource source = new HostsSource();
        source.setId(id);
        source.setLabel(url);
        source.setUrl(url);
        source.setEnabled(true);
        this.hostsSourceDao.insert(source);
    }

    protected void insertBlockedHost(String host, int sourceId) {
        HostListItem item = new HostListItem();
        item.setType(BLOCKED);
        item.setHost(host);
        item.setEnabled(true);
        item.setSourceId(sourceId);
        this.hostListItemDao.insert(item);
    }

    protected void insertAllowedHost(String host, int sourceId) {
        HostListItem item = new HostListItem();
        item.setType(ALLOWED);
        item.setHost(host);
        item.setEnabled(true);
        item.setSourceId(sourceId);
        this.hostListItemDao.insert(item);
    }

    protected void insertRedirectedHost(String host, String redirection, int sourceId) {
        HostListItem item = new HostListItem();
        item.setType(REDIRECTED);
        item.setHost(host);
        item.setEnabled(true);
        item.setRedirection(redirection);
        item.setSourceId(sourceId);
        this.hostListItemDao.insert(item);
    }

    protected HostsSource getSourceFromId(int id) {
        return this.hostsSourceDao.getAll()
                .stream()
                .filter(hostsSource -> hostsSource.getId() == id)
                .findAny()
                .orElse(null);
    }
}
