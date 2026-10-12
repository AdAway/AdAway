package org.adaway.model.source;

import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.model.error.HostError.DOWNLOAD_FAILED;
import static org.adaway.model.error.HostError.UPDATE_IN_PROGRESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.database.Cursor;

import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostEntryDao;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.model.error.HostErrorException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

@RunWith(AndroidJUnit4.class)
public class SourceModelGenerationFailureTest {
    private static final int TEST_SOURCE_ID = 919191;
    private static final int SUCCESS_SOURCE_ID = 919192;
    private static final int ACTIVE_GENERATION = 7;
    private static final int STAGING_GENERATION = 8;
    private static final String ACTIVE_HOST = "active-preserved.example";
    private static final String OLD_SUCCESS_HOST = "old-success.example";
    private static final String FRESH_SUCCESS_HOST = "fresh-success.example";
    private static final String SECOND_SUCCESS_HOST = "second-success.example";

    private SourceModel sourceModel;
    private AppDatabase database;
    private HostsSourceDao hostsSourceDao;
    private HostListItemDao hostListItemDao;
    private HostEntryDao hostEntryDao;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        hostsSourceDao = database.hostsSourceDao();
        hostListItemDao = database.hostsListItemDao();
        hostEntryDao = database.hostEntryDao();
        sourceModel = new SourceModel(context, database);

        writableDb().execSQL(
                "INSERT OR IGNORE INTO hosts_meta (id, active_generation) VALUES (0, 0)");
        insertFailingFileSource();
        insertActiveHostRow();
        setActiveGeneration(ACTIVE_GENERATION);
        hostEntryDao.sync();
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void parallelDownloadsShareOneHttpClientAndCache() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(12);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<OkHttpClient>> clients = new ArrayList<>();
        try {
            for (int i = 0; i < 12; i++) {
                clients.add(executor.submit(() -> {
                    start.await();
                    return sourceModel.getHttpClientForUi();
                }));
            }
            start.countDown();
            OkHttpClient first = clients.get(0).get(5, TimeUnit.SECONDS);
            for (Future<OkHttpClient> client : clients) {
                assertSame(first, client.get(5, TimeUnit.SECONDS));
            }
            first.cache().close();
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    public void mutationGateRejectsOverlappingUpdatesAndAlwaysReleases() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Integer> mutation = executor.submit(() -> sourceModel.runSourceMutation(() -> {
            entered.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for test release");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return 17;
        }));
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            try {
                sourceModel.retrieveHostsSource(TEST_SOURCE_ID);
                fail("Busy single-source update must not return success");
            } catch (HostErrorException expected) {
                assertEquals(UPDATE_IN_PROGRESS, expected.getError());
            }
            try {
                sourceModel.retrieveHostsSources(Collections.singletonList(TEST_SOURCE_ID));
                fail("Busy targeted update must not return success");
            } catch (HostErrorException expected) {
                assertEquals(UPDATE_IN_PROGRESS, expected.getError());
            }
            try {
                sourceModel.runSourceMutation(() -> 0);
                fail("Concurrent mutation must not run");
            } catch (HostErrorException expected) {
                assertEquals(UPDATE_IN_PROGRESS, expected.getError());
            }
        } finally {
            release.countDown();
            executor.shutdown();
            assertEquals(Integer.valueOf(17), mutation.get(5, TimeUnit.SECONDS));
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
        try {
            sourceModel.runSourceMutation(() -> {
                throw new IllegalArgumentException("injected edit failure");
            });
            fail("Expected edit failure");
        } catch (IllegalArgumentException expected) {
            assertEquals("injected edit failure", expected.getMessage());
        }
        assertEquals(Integer.valueOf(23), sourceModel.runSourceMutation(() -> 23));
    }

    @Test
    public void fullUpdate_fileParserFailureIsRecordedAndKeepsActiveGeneration() throws Exception {
        HostsSource source = hostsSourceDao.getById(TEST_SOURCE_ID).orElseThrow();
        source.setUrl("content://org.adaway.test.hosts/success.txt");
        hostsSourceDao.update(source);
        writableDb().execSQL("CREATE TRIGGER reject_staged_import BEFORE INSERT ON hosts_lists "
                + "WHEN NEW.generation = " + STAGING_GENERATION + " BEGIN "
                + "SELECT RAISE(ABORT, 'forced file parse failure'); END");

        try {
            sourceModel.checkAndRetrieveHostsSources();
            fail("A failed file parse must report DOWNLOAD_FAILED");
        } catch (HostErrorException expected) {
            assertEquals(DOWNLOAD_FAILED, expected.getError());
        }

        assertEquals(ACTIVE_GENERATION, getActiveGeneration());
        assertEquals(1, countRows(TEST_SOURCE_ID, ACTIVE_GENERATION));
        assertEquals(0, countRows(TEST_SOURCE_ID, STAGING_GENERATION));
        assertNotNull(hostsSourceDao.getById(TEST_SOURCE_ID).orElseThrow().getLastDownloadError());
        assertScratchTableAbsent();
    }

    @Test
    public void fullUpdate_carryForwardWriteFailureAbortsEvenWithSuccessfulSource() throws Exception {
        insertSuccessFileSource();
        insertHostRow(OLD_SUCCESS_HOST, SUCCESS_SOURCE_ID, ACTIVE_GENERATION);
        hostEntryDao.sync();
        writableDb().execSQL("CREATE TRIGGER reject_failed_carry_forward BEFORE INSERT ON hosts_lists "
                + "WHEN NEW.source_id = " + TEST_SOURCE_ID
                + " AND NEW.generation = " + STAGING_GENERATION + " BEGIN "
                + "SELECT RAISE(ABORT, 'forced carry forward failure'); END");

        try {
            sourceModel.checkAndRetrieveHostsSources();
            fail("A failed carry-forward must report DOWNLOAD_FAILED");
        } catch (HostErrorException expected) {
            assertEquals(DOWNLOAD_FAILED, expected.getError());
        }

        assertEquals(ACTIVE_GENERATION, getActiveGeneration());
        assertEquals(1, countRows(TEST_SOURCE_ID, ACTIVE_GENERATION));
        assertEquals(1, countRows(SUCCESS_SOURCE_ID, ACTIVE_GENERATION));
        assertEquals(0, countRows(TEST_SOURCE_ID, STAGING_GENERATION));
        assertEquals(0, countRows(SUCCESS_SOURCE_ID, STAGING_GENERATION));
        assertEquals(BLOCKED, hostEntryDao.resolveEntry(ACTIVE_HOST).getType());
        assertEquals(BLOCKED, hostEntryDao.resolveEntry(OLD_SUCCESS_HOST).getType());
        assertEquals(ALLOWED, hostEntryDao.resolveEntry(FRESH_SUCCESS_HOST).getType());
        assertNotNull(hostsSourceDao.getById(TEST_SOURCE_ID).orElseThrow().getLastDownloadError());
        assertScratchTableAbsent();
    }

    @Test
    public void fullUpdate_allSourcesFail_abortsBeforeActivatingStagingGeneration()
            throws Exception {
        try {
            sourceModel.checkAndRetrieveHostsSources();
            HostsSource source = hostsSourceDao.getById(TEST_SOURCE_ID).orElse(null);
            fail("All-failed updates must surface DOWNLOAD_FAILED instead of activating staging. " +
                    "activeGeneration=" + getActiveGeneration() +
                    ", activeRows=" + countRows(TEST_SOURCE_ID, ACTIVE_GENERATION) +
                    ", stagingRows=" + countRows(TEST_SOURCE_ID, STAGING_GENERATION) +
                    ", enabledSources=" + hostsSourceDao.getEnabled().size() +
                    ", sourceType=" + (source == null ? "missing" : source.getType()) +
                    ", lastError=" + (source == null ? "missing" : source.getLastDownloadError()));
        } catch (HostErrorException expected) {
            assertEquals(DOWNLOAD_FAILED, expected.getError());
        }

        assertEquals(ACTIVE_GENERATION, getActiveGeneration());
        assertEquals(1, countRows(TEST_SOURCE_ID, ACTIVE_GENERATION));
        assertEquals(0, countRows(TEST_SOURCE_ID, STAGING_GENERATION));
        assertEquals(BLOCKED, hostEntryDao.resolveEntry(ACTIVE_HOST).getType());
        assertScratchTableAbsent();
    }

    @Test
    public void fullUpdate_zeroEnabledSources_removesDisabledRuntimeEntries()
            throws Exception {
        HostsSource source = hostsSourceDao.getById(TEST_SOURCE_ID).orElseThrow();
        source.setEnabled(false);
        hostsSourceDao.update(source);

        sourceModel.checkAndRetrieveHostsSources();

        assertEquals(0, countAllRows(TEST_SOURCE_ID));
        assertEquals(ALLOWED, hostEntryDao.resolveEntry(ACTIVE_HOST).getType());
    }

    @Test
    public void fullUpdate_mixedFileSuccessAndFailure_activatesCompleteGeneration()
            throws Exception {
        ZonedDateTime oldLocal = ZonedDateTime.parse("2026-01-01T00:00:00Z");
        ZonedDateTime oldOnline = ZonedDateTime.parse("2026-01-01T00:00:00Z");
        HostsSource failingSource = hostsSourceDao.getById(TEST_SOURCE_ID).orElseThrow();
        failingSource.setUrl("content://org.adaway.test.missing/failing.txt");
        hostsSourceDao.update(failingSource);
        hostsSourceDao.updateModificationDates(TEST_SOURCE_ID, oldLocal, oldOnline);
        hostsSourceDao.updateSizeForGeneration(TEST_SOURCE_ID, ACTIVE_GENERATION);
        insertSuccessFileSource();
        insertHostRow(OLD_SUCCESS_HOST, SUCCESS_SOURCE_ID, ACTIVE_GENERATION);
        hostsSourceDao.updateModificationDates(SUCCESS_SOURCE_ID, oldLocal, oldOnline);
        hostsSourceDao.updateDownloadError(SUCCESS_SOURCE_ID, "old parse failure");
        hostsSourceDao.updateSizeForGeneration(SUCCESS_SOURCE_ID, ACTIVE_GENERATION);
        hostEntryDao.sync();

        sourceModel.checkAndRetrieveHostsSources();

        HostsSource failed = hostsSourceDao.getById(TEST_SOURCE_ID).orElseThrow();
        HostsSource success = hostsSourceDao.getById(SUCCESS_SOURCE_ID).orElseThrow();
        assertEquals(STAGING_GENERATION, getActiveGeneration());
        assertEquals(1, countRows(TEST_SOURCE_ID, STAGING_GENERATION));
        assertEquals(0, countRows(TEST_SOURCE_ID, ACTIVE_GENERATION));
        assertEquals(2, countRows(SUCCESS_SOURCE_ID, STAGING_GENERATION));
        assertEquals(0, countRows(SUCCESS_SOURCE_ID, ACTIVE_GENERATION));
        assertEquals(BLOCKED, hostEntryDao.resolveEntry(ACTIVE_HOST).getType());
        assertEquals(BLOCKED, hostEntryDao.resolveEntry(FRESH_SUCCESS_HOST).getType());
        assertEquals(BLOCKED, hostEntryDao.resolveEntry(SECOND_SUCCESS_HOST).getType());
        assertEquals(ALLOWED, hostEntryDao.resolveEntry(OLD_SUCCESS_HOST).getType());
        assertNotNull(failed.getLastDownloadError());
        assertEquals(oldLocal, failed.getLocalModificationDate());
        assertEquals(oldOnline, failed.getOnlineModificationDate());
        assertEquals(1, failed.getSize());
        assertNull(success.getLastDownloadError());
        assertEquals(2, success.getSize());
        assertNotNull(success.getLocalModificationDate());
        assertNotNull(success.getOnlineModificationDate());
        assertScratchTableAbsent();
    }

    @Test
    public void retrieveHostsSource_publicFailure_preservesActiveRowsAndReportsDownloadFailed()
            throws Exception {
        try {
            sourceModel.retrieveHostsSource(TEST_SOURCE_ID);
            fail("Expected public single-source refresh failure to report DOWNLOAD_FAILED");
        } catch (HostErrorException expected) {
            assertEquals(DOWNLOAD_FAILED, expected.getError());
        }

        assertEquals(ACTIVE_GENERATION, getActiveGeneration());
        assertEquals(1, countRows(TEST_SOURCE_ID, ACTIVE_GENERATION));
        assertEquals(0, countRows(TEST_SOURCE_ID, STAGING_GENERATION));
        assertEquals(BLOCKED, hostEntryDao.resolveEntry(ACTIVE_HOST).getType());
        assertScratchTableAbsent();
    }

    @Test
    public void finalizeGeneration_runtimeCacheRebuildFailureDoesNotRollBackActivation()
            throws Exception {
        insertHostRow("staging-new.example", TEST_SOURCE_ID, STAGING_GENERATION);
        writableDb().execSQL("CREATE TRIGGER abort_host_entry_rebuild " +
                "BEFORE DELETE ON host_entries BEGIN " +
                "SELECT RAISE(ABORT, 'forced host_entries rebuild failure'); END");

        invokeGenerationFinalizer(Collections.emptyList());

        assertEquals(STAGING_GENERATION, getActiveGeneration());
        assertEquals(0, countRows(TEST_SOURCE_ID, ACTIVE_GENERATION));
        assertEquals(1, countRows(TEST_SOURCE_ID, STAGING_GENERATION));
        assertEquals(ALLOWED, hostEntryDao.resolveEntry(ACTIVE_HOST).getType());
        assertEquals(BLOCKED, hostEntryDao.resolveEntry("staging-new.example").getType());
    }

    @Test
    public void finalizeGeneration_runtimeCacheRebuildFailureDoesNotRollBackSourceMetadata()
            throws Exception {
        ZonedDateTime oldLocal = ZonedDateTime.parse("2026-01-01T00:00:00Z");
        ZonedDateTime oldOnline = ZonedDateTime.parse("2026-01-01T00:00:00Z");
        ZonedDateTime newLocal = ZonedDateTime.parse("2026-02-01T00:00:00Z");
        ZonedDateTime newOnline = ZonedDateTime.parse("2026-02-01T00:00:00Z");
        hostsSourceDao.updateModificationDates(TEST_SOURCE_ID, oldLocal, oldOnline);
        hostsSourceDao.updateEntityTag(TEST_SOURCE_ID, "old-etag");
        hostsSourceDao.updateSizeForGeneration(TEST_SOURCE_ID, ACTIVE_GENERATION);
        insertHostRow("staging-new.example", TEST_SOURCE_ID, STAGING_GENERATION);
        Object sourceCommit = newSourceCommit("new-etag", newLocal, newOnline);

        writableDb().execSQL("CREATE TRIGGER abort_host_entry_metadata_rebuild " +
                "BEFORE DELETE ON host_entries BEGIN " +
                "SELECT RAISE(ABORT, 'forced host_entries metadata failure'); END");

        invokeGenerationFinalizer(Collections.singletonList(sourceCommit));

        HostsSource source = hostsSourceDao.getById(TEST_SOURCE_ID).orElseThrow();
        assertEquals(STAGING_GENERATION, getActiveGeneration());
        assertEquals("new-etag", source.getEntityTag());
        assertEquals(newLocal, source.getLocalModificationDate());
        assertEquals(newOnline, source.getOnlineModificationDate());
        assertEquals(1, source.getSize());
    }

    @Test
    public void targetedFinalizer_runtimeCacheRebuildFailureDoesNotRollBackDisabledSourceCleanup()
            throws Exception {
        ZonedDateTime oldLocal = ZonedDateTime.parse("2026-01-01T00:00:00Z");
        ZonedDateTime oldOnline = ZonedDateTime.parse("2026-01-01T00:00:00Z");
        hostsSourceDao.updateModificationDates(TEST_SOURCE_ID, oldLocal, oldOnline);
        hostsSourceDao.updateEntityTag(TEST_SOURCE_ID, "old-etag");
        hostsSourceDao.updateSizeForGeneration(TEST_SOURCE_ID, ACTIVE_GENERATION);
        Object disabledUpdate = newDisabledTargetedUpdate();

        writableDb().execSQL("CREATE TRIGGER abort_targeted_disabled_rebuild " +
                "BEFORE DELETE ON host_entries BEGIN " +
                "SELECT RAISE(ABORT, 'forced targeted disabled failure'); END");

        invokeTargetedFinalizer(Collections.singletonList(disabledUpdate));

        HostsSource source = hostsSourceDao.getById(TEST_SOURCE_ID).orElseThrow();
        assertEquals(0, countRows(TEST_SOURCE_ID, ACTIVE_GENERATION));
        assertEquals(ALLOWED, hostEntryDao.resolveEntry(ACTIVE_HOST).getType());
        assertNull(source.getEntityTag());
        assertNull(source.getLocalModificationDate());
        assertNull(source.getOnlineModificationDate());
        assertEquals(0, source.getSize());
    }

    @Test
    public void targetedFinalizer_metadataOnlyCommitDoesNotRebuildRuntimeRows()
            throws Exception {
        ZonedDateTime oldLocal = ZonedDateTime.parse("2026-01-01T00:00:00Z");
        ZonedDateTime oldOnline = ZonedDateTime.parse("2026-01-01T00:00:00Z");
        ZonedDateTime newLocal = ZonedDateTime.parse("2026-03-01T00:00:00Z");
        ZonedDateTime newOnline = ZonedDateTime.parse("2026-03-01T00:00:00Z");
        hostsSourceDao.updateModificationDates(TEST_SOURCE_ID, oldLocal, oldOnline);
        hostsSourceDao.updateEntityTag(TEST_SOURCE_ID, "old-etag");
        hostsSourceDao.updateSizeForGeneration(TEST_SOURCE_ID, ACTIVE_GENERATION);
        Object metadataUpdate = newMetadataOnlyTargetedUpdate(
                newUnchangedSourceCommit("new-etag", newLocal, newOnline));

        writableDb().execSQL("CREATE TRIGGER abort_targeted_metadata_rebuild " +
                "BEFORE DELETE ON host_entries BEGIN " +
                "SELECT RAISE(ABORT, 'metadata-only must not rebuild runtime rows'); END");

        invokeTargetedFinalizer(Collections.singletonList(metadataUpdate));

        HostsSource source = hostsSourceDao.getById(TEST_SOURCE_ID).orElseThrow();
        assertEquals(ACTIVE_GENERATION, getActiveGeneration());
        assertEquals("new-etag", source.getEntityTag());
        assertEquals(newLocal, source.getLocalModificationDate());
        assertEquals(newOnline, source.getOnlineModificationDate());
        assertEquals(1, source.getSize());
        assertEquals(BLOCKED, hostEntryDao.resolveEntry(ACTIVE_HOST).getType());
    }

    private void insertFailingFileSource() {
        HostsSource source = new HostsSource();
        source.setId(TEST_SOURCE_ID);
        source.setLabel("Failing generation source");
        source.setUrl("https://127.0.0.1:1/adaway-generation-failure-hosts.txt");
        source.setEnabled(true);
        hostsSourceDao.insert(source);
    }

    private void insertSuccessFileSource() {
        HostsSource source = new HostsSource();
        source.setId(SUCCESS_SOURCE_ID);
        source.setLabel("Successful generation source");
        source.setUrl("content://org.adaway.test.hosts/success.txt");
        source.setEnabled(true);
        hostsSourceDao.insert(source);
    }

    private void insertActiveHostRow() {
        insertHostRow(ACTIVE_HOST, TEST_SOURCE_ID, ACTIVE_GENERATION);
    }

    private void insertHostRow(String host, int sourceId, int generation) {
        HostListItem item = new HostListItem();
        item.setHost(host);
        item.setType(BLOCKED);
        item.setEnabled(true);
        item.setSourceId(sourceId);
        item.setGeneration(generation);
        hostListItemDao.insert(item);
    }

    private void setActiveGeneration(int generation) {
        writableDb().execSQL("INSERT OR REPLACE INTO hosts_meta (id, active_generation) " +
                "VALUES (0, " + generation + ")");
    }

    private int getActiveGeneration() {
        try (Cursor cursor = writableDb().query(
                "SELECT active_generation FROM hosts_meta WHERE id = 0 LIMIT 1")) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    private int countRows(int sourceId, int generation) {
        try (Cursor cursor = writableDb().query(
                "SELECT COUNT(*) FROM hosts_lists WHERE source_id = ? AND generation = ?",
                new Object[]{sourceId, generation})) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    private int countAllRows(int sourceId) {
        try (Cursor cursor = writableDb().query(
                "SELECT COUNT(*) FROM hosts_lists WHERE source_id = ?",
                new Object[]{sourceId})) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    private void assertScratchTableAbsent() {
        try (Cursor cursor = writableDb().query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' " +
                        "AND name = 'update_seen_hosts'")) {
            cursor.moveToFirst();
            assertEquals(0, cursor.getInt(0));
        }
    }

    private SupportSQLiteDatabase writableDb() {
        return database.getOpenHelper().getWritableDatabase();
    }

    private void invokeGenerationFinalizer(List<?> sourceCommits) throws Exception {
        Method finalizer = SourceModel.class.getDeclaredMethod(
                "finalizeActivatedGeneration",
                int.class, List.class, List.class, List.class);
        finalizer.setAccessible(true);
        finalizer.invoke(sourceModel, STAGING_GENERATION,
                Collections.emptyList(), sourceCommits, Collections.emptyList());
    }

    private void invokeTargetedFinalizer(List<?> targetedUpdates) throws Exception {
        Method finalizer = SourceModel.class.getDeclaredMethod(
                "finalizeStagedSourceGenerations", List.class);
        finalizer.setAccessible(true);
        finalizer.invoke(sourceModel, targetedUpdates);
    }

    private Object newSourceCommit(
            String entityTag, ZonedDateTime localModificationDate,
            ZonedDateTime onlineModificationDate) throws Exception {
        Class<?> commitClass = Class.forName(SourceModel.class.getName() + "$SourceCommit");
        Method changed = commitClass.getDeclaredMethod(
                "changed", int.class, String.class, ZonedDateTime.class,
                ZonedDateTime.class, int.class);
        changed.setAccessible(true);
        return changed.invoke(null, TEST_SOURCE_ID, entityTag, localModificationDate,
                onlineModificationDate, STAGING_GENERATION);
    }

    private Object newUnchangedSourceCommit(
            String entityTag, ZonedDateTime localModificationDate,
            ZonedDateTime onlineModificationDate) throws Exception {
        Class<?> commitClass = Class.forName(SourceModel.class.getName() + "$SourceCommit");
        Method unchanged = commitClass.getDeclaredMethod(
                "unchanged", int.class, String.class, ZonedDateTime.class,
                ZonedDateTime.class, int.class);
        unchanged.setAccessible(true);
        return unchanged.invoke(null, TEST_SOURCE_ID, entityTag, localModificationDate,
                onlineModificationDate, ACTIVE_GENERATION);
    }

    private Object newDisabledTargetedUpdate() throws Exception {
        Class<?> updateClass = Class.forName(SourceModel.class.getName() + "$TargetedSourceUpdate");
        Method disabled = updateClass.getDeclaredMethod("disabled", int.class);
        disabled.setAccessible(true);
        return disabled.invoke(null, TEST_SOURCE_ID);
    }

    private Object newMetadataOnlyTargetedUpdate(Object sourceCommit) throws Exception {
        Class<?> commitClass = Class.forName(SourceModel.class.getName() + "$SourceCommit");
        Class<?> updateClass = Class.forName(SourceModel.class.getName() + "$TargetedSourceUpdate");
        Method metadataOnly = updateClass.getDeclaredMethod("metadataOnly", commitClass);
        metadataOnly.setAccessible(true);
        return metadataOnly.invoke(null, sourceCommit);
    }
}
