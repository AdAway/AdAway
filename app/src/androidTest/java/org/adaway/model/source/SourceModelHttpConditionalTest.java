package org.adaway.model.source;

import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.adaway.model.error.HostError.DOWNLOAD_FAILED;

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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;

@RunWith(AndroidJUnit4.class)
public class SourceModelHttpConditionalTest {
    private static final int FIRST_SOURCE_ID = 929291;
    private static final int SECOND_SOURCE_ID = 929292;
    private static final int THIRD_SOURCE_ID = 929293;
    private static final int ACTIVE_GENERATION = 7;
    private static final int STAGING_GENERATION = 8;
    private static final String FIRST_HOST = "first-active.example";
    private static final String SECOND_HOST = "second-active.example";
    private static final String THIRD_HOST = "third-active.example";
    private static final String UPDATED_FIRST_HOST = "first-updated.example";
    private static final String IF_NONE_MATCH = "If-None-Match";
    private static final String IF_MODIFIED_SINCE = "If-Modified-Since";

    private AppDatabase database;
    private HostsSourceDao hostsSourceDao;
    private HostListItemDao hostListItemDao;
    private HostEntryDao hostEntryDao;
    private SourceModel sourceModel;
    private MockWebServer server;

    @Before
    public void setUp() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        hostsSourceDao = database.hostsSourceDao();
        hostListItemDao = database.hostsListItemDao();
        hostEntryDao = database.hostEntryDao();
        sourceModel = new SourceModel(context, database);
        server = new MockWebServer();

        HeldCertificate localhostCertificate = new HeldCertificate.Builder()
                .commonName("localhost")
                .addSubjectAlternativeName("localhost")
                .build();
        HandshakeCertificates serverCertificates = new HandshakeCertificates.Builder()
                .heldCertificate(localhostCertificate)
                .build();
        HandshakeCertificates clientCertificates = new HandshakeCertificates.Builder()
                .addTrustedCertificate(localhostCertificate.certificate())
                .build();
        server.useHttps(serverCertificates.sslSocketFactory(), false);
        server.start();
        OkHttpClient testClient = new OkHttpClient.Builder()
                .sslSocketFactory(clientCertificates.sslSocketFactory(),
                        clientCertificates.trustManager())
                .hostnameVerifier((hostname, session) -> "localhost".equals(hostname))
                .build();
        injectHttpClient(testClient);

        writableDb().execSQL(
                "INSERT OR IGNORE INTO hosts_meta (id, active_generation) VALUES (0, 0)");
        setActiveGeneration(ACTIVE_GENERATION);
        ZonedDateTime freshDate = ZonedDateTime.now().minusDays(1);
        insertHttpsSource(FIRST_SOURCE_ID, "First conditional source", "/first.txt",
                "\"first-etag\"", freshDate);
        insertHttpsSource(SECOND_SOURCE_ID, "Second conditional source", "/second.txt",
                "\"second-etag\"", freshDate);
        insertHostRow(FIRST_HOST, FIRST_SOURCE_ID, ACTIVE_GENERATION);
        insertHostRow(SECOND_HOST, SECOND_SOURCE_ID, ACTIVE_GENERATION);
        hostsSourceDao.updateSizeForGeneration(FIRST_SOURCE_ID, ACTIVE_GENERATION);
        hostsSourceDao.updateSizeForGeneration(SECOND_SOURCE_ID, ACTIVE_GENERATION);
        hostEntryDao.sync();
    }

    @After
    public void tearDown() throws Exception {
        if (server != null) {
            server.shutdown();
        }
        if (database != null) {
            database.close();
        }
    }

    @Test
    public void successfulHttpErrorPageDoesNotReplaceWorkingFilters() throws Exception {
        server.enqueue(new MockResponse().setHeader("Content-Type", "text/html")
                .setBody("<!doctype html>\n<html><body>Service unavailable</body></html>\n"));

        assertTargetedDownloadFailure();

        assertTrue(hostsSourceDao.getById(FIRST_SOURCE_ID).orElseThrow()
                .getLastDownloadError().contains("no supported rules"));
        assertEquals("\"first-etag\"",
                hostsSourceDao.getById(FIRST_SOURCE_ID).orElseThrow().getEntityTag());
    }

    @Test
    public void disableDuringDownloadDoesNotImportEnabledRowsFromStaleSnapshot() throws Exception {
        CountDownLatch requested = new CountDownLatch(1);
        CountDownLatch respond = new CountDownLatch(1);
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                requested.countDown();
                if (!respond.await(5, TimeUnit.SECONDS)) return new MockResponse().setResponseCode(500);
                return new MockResponse().setBody("0.0.0.0 " + UPDATED_FIRST_HOST + "\n");
            }
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> update = executor.submit(() -> {
            sourceModel.retrieveHostsSource(FIRST_SOURCE_ID);
            return null;
        });
        try {
            assertTrue(requested.await(5, TimeUnit.SECONDS));
            hostsSourceDao.applySourceSelection(FIRST_SOURCE_ID, false);
            respond.countDown();
            update.get(10, TimeUnit.SECONDS);
            assertEquals(ALLOWED, hostEntryDao.resolveEntry(UPDATED_FIRST_HOST).getType());
            try (Cursor cursor = writableDb().query(
                    "SELECT COUNT(*) FROM hosts_lists WHERE source_id = ? AND enabled = 1",
                    new Object[]{FIRST_SOURCE_ID})) {
                assertTrue(cursor.moveToFirst());
                assertEquals(0, cursor.getInt(0));
            }
            assertEquals(1, countRows(FIRST_SOURCE_ID, ACTIVE_GENERATION));
            hostsSourceDao.applySourceSelection(FIRST_SOURCE_ID, true);
            sourceModel.syncHostEntries();
            assertEquals(BLOCKED, hostEntryDao.resolveEntry(UPDATED_FIRST_HOST).getType());
        } finally {
            respond.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    public void notModifiedDoesNotRenewTheLastBodyFetchDate() throws Exception {
        ZonedDateTime previous = hostsSourceDao.getById(FIRST_SOURCE_ID).orElseThrow()
                .getLocalModificationDate();
        server.enqueue(new MockResponse().setResponseCode(304));

        sourceModel.retrieveHostsSource(FIRST_SOURCE_ID);

        assertEquals(previous, hostsSourceDao.getById(FIRST_SOURCE_ID).orElseThrow()
                .getLocalModificationDate());
        assertEquals("\"first-etag\"",
                hostsSourceDao.getById(FIRST_SOURCE_ID).orElseThrow().getEntityTag());
    }

    @Test
    public void changedResponseWithoutEtagClearsPreviousValidator() throws Exception {
        server.enqueue(new MockResponse().setBody("0.0.0.0 " + UPDATED_FIRST_HOST + "\n"));

        sourceModel.retrieveHostsSource(FIRST_SOURCE_ID);

        assertNull(hostsSourceDao.getById(FIRST_SOURCE_ID).orElseThrow().getEntityTag());
        assertEquals(BLOCKED, hostEntryDao.resolveEntry(UPDATED_FIRST_HOST).getType());
    }

    @Test
    public void weakEtagIsStoredAndSentUnchanged() throws Exception {
        String weakEtag = "W/\"new-weak-etag\"";
        server.enqueue(new MockResponse().setHeader("ETag", weakEtag)
                .setBody("0.0.0.0 " + UPDATED_FIRST_HOST + "\n"));
        sourceModel.retrieveHostsSource(FIRST_SOURCE_ID);
        assertEquals(weakEtag,
                hostsSourceDao.getById(FIRST_SOURCE_ID).orElseThrow().getEntityTag());
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS));

        server.enqueue(new MockResponse().setResponseCode(304));
        sourceModel.retrieveHostsSource(FIRST_SOURCE_ID);

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals(weakEtag, request.getHeader(IF_NONE_MATCH));
        assertEquals(weakEtag,
                hostsSourceDao.getById(FIRST_SOURCE_ID).orElseThrow().getEntityTag());
    }

    @Test
    public void unsolicited304FailsAndPreservesPreviousRows() throws Exception {
        hostsSourceDao.clearProperties(FIRST_SOURCE_ID);
        server.enqueue(new MockResponse().setResponseCode(304));

        assertTargetedDownloadFailure();

        HostsSource source = hostsSourceDao.getById(FIRST_SOURCE_ID).orElseThrow();
        assertTrue(source.getLastDownloadError().contains("Unexpected HTTP 304"));
        assertNull(source.getLocalModificationDate());
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertNull(request.getHeader(IF_NONE_MATCH));
        assertNull(request.getHeader(IF_MODIFIED_SINCE));
    }

    @Test
    public void oversizedResponseFailsBeforeReadingAndPreservesPreviousRows() throws Exception {
        server.enqueue(new MockResponse().setHeader("Content-Length",
                Long.toString(SourceModel.MAX_SOURCE_BYTES + 1)));

        assertTargetedDownloadFailure();

        assertTrue(hostsSourceDao.getById(FIRST_SOURCE_ID).orElseThrow()
                .getLastDownloadError().contains("exceeds"));
    }

    @Test
    public void expiredMetadataForcesNetworkInsteadOfReusingHttpCache() throws Exception {
        HostsSource source = hostsSourceDao.getById(FIRST_SOURCE_ID).orElseThrow();
        source.setLocalModificationDate(ZonedDateTime.now().minusDays(8));
        Method method = SourceModel.class.getDeclaredMethod("getRequestFor", HostsSource.class);
        method.setAccessible(true);
        Request request = ((Request.Builder) method.invoke(sourceModel, source)).build();

        assertTrue(request.cacheControl().noCache());
        assertNull(request.header(IF_NONE_MATCH));
        assertNull(request.header(IF_MODIFIED_SINCE));
    }

    private void assertTargetedDownloadFailure() throws Exception {
        try {
            sourceModel.retrieveHostsSource(FIRST_SOURCE_ID);
            fail("Expected DOWNLOAD_FAILED");
        } catch (HostErrorException expected) {
            assertEquals(DOWNLOAD_FAILED, expected.getError());
        }
        assertEquals(ACTIVE_GENERATION, getActiveGeneration());
        assertEquals(1, countRows(FIRST_SOURCE_ID, ACTIVE_GENERATION));
        assertEquals(0, countRows(FIRST_SOURCE_ID, STAGING_GENERATION));
        assertEquals(BLOCKED, hostEntryDao.resolveEntry(FIRST_HOST).getType());
        assertScratchTableAbsent();
    }

    @Test
    public void checkAndRetrieveHostsSources_all304_keepsActiveGenerationAndRuntimeRows()
            throws Exception {
        server.enqueue(new MockResponse().setResponseCode(304));
        server.enqueue(new MockResponse().setResponseCode(304));
        writableDb().execSQL("CREATE TRIGGER abort_host_entries_rebuild " +
                "BEFORE DELETE ON host_entries BEGIN " +
                "SELECT RAISE(ABORT, 'all-304 must not rebuild runtime rows'); END");

        sourceModel.checkAndRetrieveHostsSources();

        RecordedRequest firstRequest = server.takeRequest(5, TimeUnit.SECONDS);
        RecordedRequest secondRequest = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(firstRequest);
        assertNotNull(secondRequest);
        assertEquals("GET", firstRequest.getMethod());
        assertEquals("GET", secondRequest.getMethod());
        assertNotNull(firstRequest.getHeader(IF_NONE_MATCH));
        assertNotNull(firstRequest.getHeader(IF_MODIFIED_SINCE));
        assertNotNull(secondRequest.getHeader(IF_NONE_MATCH));
        assertNotNull(secondRequest.getHeader(IF_MODIFIED_SINCE));
        assertEquals(ACTIVE_GENERATION, getActiveGeneration());
        assertEquals(1, countRows(FIRST_SOURCE_ID, ACTIVE_GENERATION));
        assertEquals(1, countRows(SECOND_SOURCE_ID, ACTIVE_GENERATION));
        assertEquals(0, countRows(FIRST_SOURCE_ID, STAGING_GENERATION));
        assertEquals(0, countRows(SECOND_SOURCE_ID, STAGING_GENERATION));
        assertEquals(BLOCKED, hostEntryDao.resolveEntry(FIRST_HOST).getType());
        assertEquals(BLOCKED, hostEntryDao.resolveEntry(SECOND_HOST).getType());
        assertEquals(1, hostsSourceDao.getById(FIRST_SOURCE_ID).orElseThrow().getSize());
        assertEquals(1, hostsSourceDao.getById(SECOND_SOURCE_ID).orElseThrow().getSize());
        assertNull(hostsSourceDao.getById(FIRST_SOURCE_ID).orElseThrow().getLastDownloadError());
        assertNull(hostsSourceDao.getById(SECOND_SOURCE_ID).orElseThrow().getLastDownloadError());
        assertScratchTableAbsent();
    }

    @Test
    public void checkAndRetrieveHostsSources_changedSourcePublishesWithoutRuntimeMaterialization()
            throws Exception {
        clearMaterializedRuntimeTables();
        installMaterializedRuntimeAbortTriggers();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if ("/first.txt".equals(request.getPath())) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("ETag", "\"first-new-etag\"")
                            .setHeader("Last-Modified", "Sat, 13 Jun 2026 00:00:00 GMT")
                            .setBody("0.0.0.0 " + UPDATED_FIRST_HOST + "\n");
                }
                if ("/second.txt".equals(request.getPath())) {
                    return new MockResponse().setResponseCode(304);
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        sourceModel.checkAndRetrieveHostsSources();

        assertConditionalRequestsSent("/first.txt", "/second.txt");
        assertEquals(STAGING_GENERATION, getActiveGeneration());
        assertEquals(0, countRows(FIRST_SOURCE_ID, ACTIVE_GENERATION));
        assertEquals(1, countRows(FIRST_SOURCE_ID, STAGING_GENERATION));
        assertEquals(0, countRows(SECOND_SOURCE_ID, ACTIVE_GENERATION));
        assertEquals(1, countRows(SECOND_SOURCE_ID, STAGING_GENERATION));
        assertEquals(BLOCKED, hostEntryDao.resolveEntry(UPDATED_FIRST_HOST).getType());
        assertEquals(BLOCKED, hostEntryDao.resolveEntry(SECOND_HOST).getType());
        assertEquals(2, drainRootHostsCursor());
        assertEquals(0, countTableRows("host_entries"));
        assertEquals(0, countTableRows("root_host_entries"));
        assertNull(hostsSourceDao.getById(FIRST_SOURCE_ID).orElseThrow().getLastDownloadError());
        assertNull(hostsSourceDao.getById(SECOND_SOURCE_ID).orElseThrow().getLastDownloadError());
        assertScratchTableAbsent();
    }

    @Test
    public void checkAndRetrieveHostsSources_mixed200304AndFailurePreservesCoverage()
            throws Exception {
        ZonedDateTime freshDate = ZonedDateTime.now().minusDays(1);
        insertHttpsSource(THIRD_SOURCE_ID, "Third failing source", "/third.txt",
                "\"third-etag\"", freshDate);
        insertHostRow(THIRD_HOST, THIRD_SOURCE_ID, ACTIVE_GENERATION);
        hostsSourceDao.updateSizeForGeneration(THIRD_SOURCE_ID, ACTIVE_GENERATION);
        hostEntryDao.sync();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if ("/first.txt".equals(request.getPath())) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("ETag", "\"first-new-etag\"")
                            .setHeader("Last-Modified", "Sat, 13 Jun 2026 00:00:00 GMT")
                            .setBody("0.0.0.0 " + UPDATED_FIRST_HOST + "\n");
                }
                if ("/second.txt".equals(request.getPath())) {
                    return new MockResponse().setResponseCode(304);
                }
                if ("/third.txt".equals(request.getPath())) {
                    return new MockResponse().setResponseCode(500);
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        sourceModel.checkAndRetrieveHostsSources();

        assertConditionalRequestsSent("/first.txt", "/second.txt", "/third.txt");
        assertEquals(STAGING_GENERATION, getActiveGeneration());
        assertEquals(0, countRows(FIRST_SOURCE_ID, ACTIVE_GENERATION));
        assertEquals(1, countRows(FIRST_SOURCE_ID, STAGING_GENERATION));
        assertEquals(0, countRows(SECOND_SOURCE_ID, ACTIVE_GENERATION));
        assertEquals(1, countRows(SECOND_SOURCE_ID, STAGING_GENERATION));
        assertEquals(0, countRows(THIRD_SOURCE_ID, ACTIVE_GENERATION));
        assertEquals(1, countRows(THIRD_SOURCE_ID, STAGING_GENERATION));
        assertEquals(ALLOWED, hostEntryDao.resolveEntry(FIRST_HOST).getType());
        assertEquals(BLOCKED, hostEntryDao.resolveEntry(UPDATED_FIRST_HOST).getType());
        assertEquals(BLOCKED, hostEntryDao.resolveEntry(SECOND_HOST).getType());
        assertEquals(BLOCKED, hostEntryDao.resolveEntry(THIRD_HOST).getType());
        assertNull(hostsSourceDao.getById(FIRST_SOURCE_ID).orElseThrow().getLastDownloadError());
        assertNull(hostsSourceDao.getById(SECOND_SOURCE_ID).orElseThrow().getLastDownloadError());
        assertNotNull(hostsSourceDao.getById(THIRD_SOURCE_ID).orElseThrow()
                .getLastDownloadError());
        assertEquals(1, hostsSourceDao.getById(FIRST_SOURCE_ID).orElseThrow().getSize());
        assertEquals(1, hostsSourceDao.getById(SECOND_SOURCE_ID).orElseThrow().getSize());
        assertEquals(1, hostsSourceDao.getById(THIRD_SOURCE_ID).orElseThrow().getSize());
        assertScratchTableAbsent();
    }

    @Test
    public void checkAndRetrieveHostsSources_stopDuringDownloadReturnsCancelledAndKeepsRuntimeTruth()
            throws Exception {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if ("/first.txt".equals(request.getPath())) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("ETag", "\"first-new-etag\"")
                            .setHeader("Last-Modified", "Sat, 13 Jun 2026 00:00:00 GMT")
                            .setBody("0.0.0.0 " + UPDATED_FIRST_HOST + "\n")
                            .setBodyDelay(5, TimeUnit.SECONDS);
                }
                if ("/second.txt".equals(request.getPath())) {
                    return new MockResponse().setResponseCode(304);
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Object> updateResult = executor.submit(this::invokeCheckAndRetrieveHostsSources);
        try {
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS));

            sourceModel.requestStop();

            assertEquals(Boolean.FALSE, updateResult.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertEquals(ACTIVE_GENERATION, getActiveGeneration());
        assertEquals(1, countRows(FIRST_SOURCE_ID, ACTIVE_GENERATION));
        assertEquals(1, countRows(SECOND_SOURCE_ID, ACTIVE_GENERATION));
        assertEquals(0, countRows(FIRST_SOURCE_ID, STAGING_GENERATION));
        assertEquals(0, countRows(SECOND_SOURCE_ID, STAGING_GENERATION));
        assertEquals(BLOCKED, hostEntryDao.resolveEntry(FIRST_HOST).getType());
        assertEquals(BLOCKED, hostEntryDao.resolveEntry(SECOND_HOST).getType());
        assertEquals(ALLOWED, hostEntryDao.resolveEntry(UPDATED_FIRST_HOST).getType());
        assertScratchTableAbsent();
    }

    private void insertHttpsSource(
            int sourceId,
            String label,
            String path,
            String entityTag,
            ZonedDateTime modificationDate) {
        HostsSource source = new HostsSource();
        source.setId(sourceId);
        source.setLabel(label);
        source.setUrl(server.url(path).toString());
        source.setEnabled(true);
        source.setEntityTag(entityTag);
        source.setLocalModificationDate(modificationDate);
        source.setOnlineModificationDate(modificationDate);
        hostsSourceDao.insert(source);
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

    private void injectHttpClient(OkHttpClient client) throws Exception {
        Field field = SourceModel.class.getDeclaredField("cachedHttpClient");
        field.setAccessible(true);
        field.set(sourceModel, client);
    }

    private Object invokeCheckAndRetrieveHostsSources() throws Exception {
        Method method = SourceModel.class.getMethod("checkAndRetrieveHostsSources");
        try {
            return method.invoke(sourceModel);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new AssertionError(cause);
        }
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

    private void assertConditionalRequestsSent(String... expectedPaths) throws Exception {
        Map<String, RecordedRequest> requestsByPath = new HashMap<>();
        for (int i = 0; i < expectedPaths.length; i++) {
            RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull("Expected conditional request " + (i + 1), request);
            requestsByPath.put(request.getPath(), request);
        }

        for (String path : expectedPaths) {
            RecordedRequest request = requestsByPath.get(path);
            assertNotNull("Expected request for " + path, request);
            assertEquals("GET", request.getMethod());
            assertNotNull(path + " must send " + IF_NONE_MATCH,
                    request.getHeader(IF_NONE_MATCH));
            assertNotNull(path + " must send " + IF_MODIFIED_SINCE,
                    request.getHeader(IF_MODIFIED_SINCE));
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

    private void clearMaterializedRuntimeTables() {
        writableDb().execSQL("DELETE FROM host_entries");
        writableDb().execSQL("DELETE FROM root_host_entries");
    }

    private void installMaterializedRuntimeAbortTriggers() {
        writableDb().execSQL("CREATE TRIGGER abort_host_entries_insert " +
                "BEFORE INSERT ON host_entries BEGIN " +
                "SELECT RAISE(ABORT, 'host_entries must not be materialized'); END");
        writableDb().execSQL("CREATE TRIGGER abort_root_host_entries_insert " +
                "BEFORE INSERT ON root_host_entries BEGIN " +
                "SELECT RAISE(ABORT, 'root_host_entries must not be materialized'); END");
    }

    private int countTableRows(String table) {
        try (Cursor cursor = writableDb().query("SELECT COUNT(*) FROM " + table)) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    private int drainRootHostsCursor() {
        int count = 0;
        try (Cursor cursor = hostEntryDao.getRootHostsFileCursor()) {
            int hostColumn = cursor.getColumnIndexOrThrow("host");
            while (cursor.moveToNext()) {
                assertTrue(cursor.getString(hostColumn).endsWith(".example"));
                count++;
            }
        }
        return count;
    }

    private SupportSQLiteDatabase writableDb() {
        return database.getOpenHelper().getWritableDatabase();
    }
}
