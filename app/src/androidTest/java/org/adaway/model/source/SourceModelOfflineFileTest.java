package org.adaway.model.source;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.db.entity.HostsSource.USER_SOURCE_URL;
import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.model.error.HostError.NO_CONNECTION;
import static org.adaway.model.error.HostError.UPDATE_IN_PROGRESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.adaway.db.AppDatabase;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.model.error.HostErrorException;
import org.adaway.util.AppExecutors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class SourceModelOfflineFileTest {
    private static final int FILE_SOURCE_ID = 949491;
    private static final int URL_SOURCE_ID = 949492;
    private static final int ACTIVE_GENERATION = 7;
    private static final String FILE_URL = "content://org.adaway.test.hosts/success.txt";
    private static final String URL = "https://offline.example/hosts.txt";
    private static final String OLD_HOST = "offline-old.example";
    private static final String USER_HOST = "offline-user.example";

    private AppDatabase database;
    private SourceModel sourceModel;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        database.getOpenHelper().getWritableDatabase().execSQL(
                "INSERT OR REPLACE INTO hosts_meta (id, active_generation) VALUES (0, ?)",
                new Object[]{ACTIVE_GENERATION});
        Context offlineContext = new ContextWrapper(context) {
            @Override
            public Object getSystemService(String name) {
                return CONNECTIVITY_SERVICE.equals(name) ? null : super.getSystemService(name);
            }
        };
        sourceModel = new SourceModel(offlineContext, database);
    }

    @After
    public void tearDown() throws Exception {
        // Finalization queues a cache refresh; let it finish before closing this test database.
        CountDownLatch drained = new CountDownLatch(1);
        AppExecutors.getInstance().diskIO().execute(drained::countDown);
        assertTrue("Runtime cache refresh must finish", drained.await(10, TimeUnit.SECONDS));
        database.close();
    }

    @Test
    public void localFileImportsOfflineAndUpdatesRuntimeRules() throws Exception {
        insertSource(FILE_SOURCE_ID, FILE_URL, true);
        insertHost(OLD_HOST, FILE_SOURCE_ID);

        assertTrue(sourceModel.checkAndRetrieveHostsSources());

        assertImportedFile();
        assertEquals(ALLOWED, database.hostEntryDao().resolveEntry(OLD_HOST).getType());
        assertEquals(Integer.valueOf(17), sourceModel.runSourceMutation(() -> 17));
    }

    @Test
    public void disabledUrlDoesNotBlockLocalFileImportOffline() throws Exception {
        insertSource(FILE_SOURCE_ID, FILE_URL, true);
        insertSource(URL_SOURCE_ID, URL, false);
        insertHost(OLD_HOST, URL_SOURCE_ID);

        assertTrue(sourceModel.checkAndRetrieveHostsSources());

        assertImportedFile();
        assertEquals(0, queryInt("SELECT COUNT(*) FROM hosts_lists WHERE source_id = ?",
                URL_SOURCE_ID));
        assertNull(database.hostsSourceDao().getById(URL_SOURCE_ID).orElseThrow()
                .getLastDownloadError());
    }

    @Test
    public void userOnlyRulesRemainUsableOffline() throws Exception {
        insertSource(USER_SOURCE_ID, USER_SOURCE_URL, true);
        insertHost(USER_HOST, USER_SOURCE_ID);

        assertTrue(sourceModel.checkAndRetrieveHostsSources());

        assertEquals(ACTIVE_GENERATION, activeGeneration());
        assertEquals(BLOCKED, database.hostEntryDao().resolveEntry(USER_HOST).getType());
    }

    @Test
    public void disabledUrlOnlyCleansOldRowsOfflineAndPreservesUserRules() throws Exception {
        insertSource(USER_SOURCE_ID, USER_SOURCE_URL, true);
        insertHost(USER_HOST, USER_SOURCE_ID);
        insertSource(URL_SOURCE_ID, URL, false);
        insertHost(OLD_HOST, URL_SOURCE_ID);

        assertTrue(sourceModel.checkAndRetrieveHostsSources());

        assertEquals(ACTIVE_GENERATION, activeGeneration());
        assertEquals(0, queryInt("SELECT COUNT(*) FROM hosts_lists WHERE source_id = ?",
                URL_SOURCE_ID));
        assertEquals(BLOCKED, database.hostEntryDao().resolveEntry(USER_HOST).getType());
    }

    @Test
    public void enabledUrlOfflinePreservesGenerationAndReleasesGateForLocalRetry()
            throws Exception {
        insertSource(FILE_SOURCE_ID, FILE_URL, true);
        insertSource(URL_SOURCE_ID, URL, true);
        insertHost(OLD_HOST, URL_SOURCE_ID);

        try {
            sourceModel.checkAndRetrieveHostsSources();
            fail("An enabled URL still requires connectivity");
        } catch (HostErrorException expected) {
            assertEquals(NO_CONNECTION, expected.getError());
        }

        assertEquals(ACTIVE_GENERATION, activeGeneration());
        assertEquals(1, queryInt("SELECT COUNT(*) FROM hosts_lists"));
        assertEquals(BLOCKED, database.hostEntryDao().resolveEntry(OLD_HOST).getType());
        assertEquals(ALLOWED, database.hostEntryDao().resolveEntry("fresh-success.example")
                .getType());
        assertEquals(0, queryInt("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' "
                + "AND name = 'update_seen_hosts'"));
        assertEquals(2, queryInt("PRAGMA synchronous"));
        assertNull(database.hostsSourceDao().getById(FILE_SOURCE_ID).orElseThrow()
                .getLocalModificationDate());

        sourceModel.runSourceMutation(() -> {
            database.hostsSourceDao().applySourceSelection(URL_SOURCE_ID, false);
            return null;
        });
        assertTrue(sourceModel.checkAndRetrieveHostsSources());
        assertImportedFile();
    }

    @Test
    public void offlineUpdateCannotInspectSelectionWhileMutationOwnsGate() throws Exception {
        insertSource(URL_SOURCE_ID, URL, true);
        sourceModel.runSourceMutation(() -> {
            try {
                sourceModel.checkAndRetrieveHostsSources();
                fail("Update must respect the mutation gate before inspecting connectivity");
            } catch (HostErrorException expected) {
                assertEquals(UPDATE_IN_PROGRESS, expected.getError());
            }
            return null;
        });
    }

    private void assertImportedFile() {
        assertEquals(ACTIVE_GENERATION + 1, activeGeneration());
        assertEquals(2, database.hostsSourceDao().getById(FILE_SOURCE_ID).orElseThrow().getSize());
        assertEquals(BLOCKED, database.hostEntryDao().resolveEntry("fresh-success.example")
                .getType());
        assertEquals(BLOCKED, database.hostEntryDao().resolveEntry("second-success.example")
                .getType());
    }

    private void insertSource(int id, String url, boolean enabled) {
        HostsSource source = new HostsSource();
        source.setId(id);
        source.setLabel(url);
        source.setUrl(url);
        source.setEnabled(enabled);
        database.hostsSourceDao().insert(source);
    }

    private void insertHost(String host, int sourceId) {
        HostListItem item = new HostListItem();
        item.setHost(host);
        item.setType(BLOCKED);
        item.setEnabled(true);
        item.setSourceId(sourceId);
        item.setGeneration(ACTIVE_GENERATION);
        database.hostsListItemDao().insert(item);
    }

    private int activeGeneration() {
        return queryInt("SELECT active_generation FROM hosts_meta WHERE id = 0");
    }

    private int queryInt(String sql, Object... args) {
        try (Cursor cursor = database.getOpenHelper().getWritableDatabase().query(sql, args)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }
}
