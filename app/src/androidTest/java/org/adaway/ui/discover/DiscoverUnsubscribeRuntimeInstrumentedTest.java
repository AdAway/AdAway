package org.adaway.ui.discover;

import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.model.adblocking.AdBlockMethod.ROOT;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.SystemClock;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.checkbox.MaterialCheckBox;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.RuleKind;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.source.FilterListCatalog;
import org.adaway.model.source.FilterListsDirectoryApi;
import org.adaway.model.source.SourceModel;
import org.adaway.model.vpn.VpnModel;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.home.HomeActivity;
import org.adaway.util.AppExecutors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import okhttp3.OkHttpClient;

@RunWith(AndroidJUnit4.class)
public class DiscoverUnsubscribeRuntimeInstrumentedTest {
    private static final int SOURCE_ID = 840101;
    private static final int SECOND_SOURCE_ID = 840102;
    private static final int UNRELATED_SOURCE_ID = 840103;
    private static final int LIST_ID = 701;
    private static final String HOST = "discover-unsubscribe.invalid";
    private static final String SECOND_HOST = "second.discover-unsubscribe.invalid";
    private static final String UNRELATED_HOST = "unrelated.discover-unsubscribe.invalid";
    private static final long TIMEOUT_MS = 10_000;

    private AdAwayApplication application;
    private AppDatabase database;
    private SourceModel previousSourceModel;
    private AdBlockModel previousAdBlockModel;
    private RecordingSourceModel sourceModel;
    private RecordingVpnModel vpnModel;
    private String sourceUrl;

    @Before
    public void setUp() throws Exception {
        application = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(application, "set up Discover unsubscribe");
        database = AppDatabase.getInstance(application);
        database.getOpenHelper().getWritableDatabase();
        drainDiskIo();
        database.hostsSourceDao().deleteAll();
        previousSourceModel = application.getSourceModel();
        previousAdBlockModel = application.getAdBlockModel();
        sourceModel = new RecordingSourceModel(application);
        setApplicationField("sourceModel", sourceModel);
        sourceUrl = FilterListCatalog.getAll().get(0).url;
        seedSource(SOURCE_ID, sourceUrl, HOST, LIST_ID);
        seedSource(SECOND_SOURCE_ID, "https://second.invalid/hosts", SECOND_HOST, LIST_ID + 1);
        seedSource(UNRELATED_SOURCE_ID, "https://unrelated.invalid/hosts", UNRELATED_HOST, null);
        sourceModel.syncHostEntries();
        sourceModel.syncCount.set(0);
        seedDirectoryCache();
    }

    @After
    public void tearDown() throws Exception {
        if (sourceModel != null) sourceModel.releaseSync.countDown();
        drainDiskIo();
        if (application != null) {
            setApplicationField("sourceModel", previousSourceModel);
            setApplicationField("adBlockModel", previousAdBlockModel);
            database.hostsSourceDao().deleteAll();
            previousSourceModel.syncHostEntries();
            assertTrue(application.getSharedPreferences("filterlists_cache", Context.MODE_PRIVATE)
                    .edit().clear().commit());
            InstrumentedTestState.resetForPassiveRootUi(application,
                    "tear down Discover unsubscribe");
        }
    }

    @Test
    public void daoOnlyDeletionLeavesPrimedVpnDecisionBlocked() throws Exception {
        primeVpnCache();
        database.hostsSourceDao().delete(database.hostsSourceDao()
                .getById(SOURCE_ID).orElseThrow());
        assertEquals(ALLOWED, database.hostEntryDao().resolveEntry(HOST).getType());
        assertEquals("DAO deletion alone leaves a live cached decision blocked",
                BLOCKED, vpnModel.getEntry(HOST).getType());
        sourceModel.syncHostEntries();
        assertEquals(ALLOWED, vpnModel.getEntry(HOST).getType());
    }

    @Test
    public void singleUnsubscribeClearsRuntimeAndPrimedVpnCacheWithoutStartingVpn()
            throws Exception {
        DiscoverFilterListsFragment fragment = new DiscoverFilterListsFragment();
        try (ActivityScenario<HomeActivity> scenario = mount(fragment)) {
            primeVpnCache();
            scenario.onActivity(activity -> removeSingle(fragment));
            assertRemoved(SOURCE_ID, HOST);
            assertKept(SECOND_SOURCE_ID, SECOND_HOST);
            waitForApplyAction(scenario);
            assertEquals(1, sourceModel.syncCount.get());
            assertEquals(0, vpnModel.applyCount.get());
            assertEquals(0, sourceModel.networkRequests.get());
        }
    }

    @Test
    public void bulkUnsubscribeRebuildsOnceAndKeepsNonDirectorySource() throws Exception {
        DiscoverFilterListsFragment fragment = new DiscoverFilterListsFragment();
        try (ActivityScenario<HomeActivity> scenario = mount(fragment)) {
            primeVpnCache();
            scenario.onActivity(activity -> removeBulk(fragment, true));
            assertRemoved(SOURCE_ID, HOST);
            assertRemoved(SECOND_SOURCE_ID, SECOND_HOST);
            assertKept(UNRELATED_SOURCE_ID, UNRELATED_HOST);
            waitForApplyAction(scenario);
            assertEquals("Bulk removal should rebuild only once", 1, sourceModel.syncCount.get());
            assertEquals(0, vpnModel.applyCount.get());
            assertEquals(0, sourceModel.networkRequests.get());
        }
    }

    @Test
    public void selectedUnsubscribeLeavesUnselectedDirectorySourceBlocking() throws Exception {
        DiscoverFilterListsFragment fragment = new DiscoverFilterListsFragment();
        try (ActivityScenario<HomeActivity> scenario = mount(fragment)) {
            primeVpnCache();
            scenario.onActivity(activity -> {
                RecyclerView recycler = activity.findViewById(R.id.filterlistsRecyclerView);
                RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(0);
                assertNotNull(holder);
                MaterialCheckBox selected = holder.itemView
                        .findViewById(R.id.filterlistsItemSelectionCheckBox);
                selected.setChecked(true);
                removeBulk(fragment, false);
            });
            assertRemoved(SOURCE_ID, HOST);
            assertKept(SECOND_SOURCE_ID, SECOND_HOST);
            assertKept(UNRELATED_SOURCE_ID, UNRELATED_HOST);
            waitForApplyAction(scenario);
            assertEquals(1, sourceModel.syncCount.get());
        }
    }

    @Test
    public void catalogRemovalClearsRuntimeAndPrimedVpnCache() throws Exception {
        DiscoverCatalogFragment fragment = new DiscoverCatalogFragment();
        try (ActivityScenario<HomeActivity> scenario = mount(fragment)) {
            primeVpnCache();
            scenario.onActivity(activity -> removeCatalog(fragment));
            assertRemoved(SOURCE_ID, HOST);
            assertKept(SECOND_SOURCE_ID, SECOND_HOST);
            waitForApplyAction(scenario);
            assertEquals(1, sourceModel.syncCount.get());
            assertEquals(0, vpnModel.applyCount.get());
        }
    }

    @Test
    public void rootApplyInstallsPostRemovalConfigurationWithoutDownloadingSources()
            throws Exception {
        DiscoverCatalogFragment fragment = new DiscoverCatalogFragment();
        try (ActivityScenario<HomeActivity> scenario = mount(fragment)) {
            RecordingRootModel rootModel = new RecordingRootModel(application);
            setApplicationField("adBlockModel", rootModel);
            scenario.onActivity(activity -> removeCatalog(fragment));
            waitForApplyAction(scenario);
            scenario.onActivity(activity -> activity.findViewById(
                    com.google.android.material.R.id.snackbar_action).performClick());
            waitFor("root configuration applied", () -> rootModel.applyCount.get() == 1);
            assertFalse("Removed rule must not be included in root installation",
                    rootModel.installedRemovedHost);
            assertTrue("Unrelated rule must still be installed", rootModel.installedUnrelatedHost);
            assertEquals(2, sourceModel.syncCount.get());
            assertEquals(0, sourceModel.networkRequests.get());
        }
    }

    @Test
    public void singleUnsubscribeStillSynchronizesAfterViewIsDestroyed() throws Exception {
        assertDetachedRemoval(new DiscoverFilterListsFragment());
    }

    @Test
    public void catalogRemovalStillSynchronizesAfterViewIsDestroyed() throws Exception {
        assertDetachedRemoval(new DiscoverCatalogFragment());
    }

    private void assertDetachedRemoval(Fragment fragment) throws Exception {
        ActivityScenario<HomeActivity> scenario = mount(fragment);
        try {
            primeVpnCache();
            sourceModel.pauseSync = true;
            scenario.onActivity(activity -> {
                if (fragment instanceof DiscoverCatalogFragment) {
                    removeCatalog((DiscoverCatalogFragment) fragment);
                } else {
                    removeSingle((DiscoverFilterListsFragment) fragment);
                }
            });
            assertTrue("Removal should reach background synchronization",
                    sourceModel.syncEntered.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            scenario.close();
            sourceModel.releaseSync.countDown();
            assertRemoved(SOURCE_ID, HOST);
            drainDiskIo();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertFalse(fragment.isAdded());
        } finally {
            sourceModel.releaseSync.countDown();
            scenario.close();
        }
    }

    private ActivityScenario<HomeActivity> mount(Fragment fragment) {
        ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class);
        scenario.onActivity(activity -> activity.getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_fragment_container, fragment).commitNow());
        waitFor("Discover rows", () -> {
            AtomicBoolean ready = new AtomicBoolean();
            scenario.onActivity(activity -> {
                int id = fragment instanceof DiscoverCatalogFragment
                        ? R.id.catalogRecyclerView : R.id.filterlistsRecyclerView;
                RecyclerView recycler = activity.findViewById(id);
                ready.set(recycler != null && recycler.getAdapter() != null
                        && recycler.getAdapter().getItemCount() > 0
                        && recycler.findViewHolderForAdapterPosition(0) != null);
            });
            return ready.get();
        });
        return scenario;
    }

    private void primeVpnCache() throws Exception {
        PreferenceHelper.setAbBlockMethod(application, VPN);
        vpnModel = new RecordingVpnModel(application);
        setApplicationField("adBlockModel", vpnModel);
        assertEquals(BLOCKED, vpnModel.getEntry(HOST).getType());
        assertEquals(BLOCKED, vpnModel.getEntry(SECOND_HOST).getType());
        assertEquals(BLOCKED, vpnModel.getEntry(UNRELATED_HOST).getType());
    }

    private void assertRemoved(int sourceId, String host) {
        waitFor("removed source no longer blocking " + host, () ->
                !database.hostsSourceDao().getById(sourceId).isPresent()
                        && sourceModel.syncCount.get() > 0
                        && vpnModel.getEntry(host).getType() == ALLOWED);
        assertEquals(ALLOWED, database.hostEntryDao().resolveEntry(host).getType());
        assertFalse(database.hostEntryDao().getAll().stream()
                .anyMatch(entry -> host.equals(entry.getHost())));
    }

    private void assertKept(int sourceId, String host) {
        assertTrue(database.hostsSourceDao().getById(sourceId).isPresent());
        assertEquals(BLOCKED, vpnModel.getEntry(host).getType());
    }

    private static void waitForApplyAction(ActivityScenario<HomeActivity> scenario) {
        waitFor("Apply Configuration action", () -> {
            AtomicBoolean visible = new AtomicBoolean();
            scenario.onActivity(activity -> {
                TextView action = activity.findViewById(
                        com.google.android.material.R.id.snackbar_action);
                visible.set(action != null && action.isShown() && activity.getString(
                        R.string.notification_configuration_changed_action)
                        .contentEquals(action.getText()));
            });
            return visible.get();
        });
    }

    private static void removeSingle(DiscoverFilterListsFragment fragment) {
        FilterListsDirectoryApi.ListSummary summary = new FilterListsDirectoryApi.ListSummary(
                LIST_ID, "Removal test", null, new int[]{1}, new int[0], new int[0]);
        invoke(fragment, "updateSubscription",
                new Class<?>[]{FilterListsDirectoryApi.ListSummary.class, boolean.class},
                summary, false);
    }

    private static void removeBulk(DiscoverFilterListsFragment fragment, boolean all) {
        invoke(fragment, "unsubscribeAll", new Class<?>[]{boolean.class}, all);
    }

    private void removeCatalog(DiscoverCatalogFragment fragment) {
        invoke(fragment, "removeExistingSourceByUrl", new Class<?>[]{String.class}, sourceUrl);
    }

    // Invoke the confirmed actions; keep the real executors, database, sync, and UI callbacks.
    private static void invoke(Fragment fragment, String name, Class<?>[] types, Object... args) {
        try {
            Method method = fragment.getClass().getDeclaredMethod(name, types);
            method.setAccessible(true);
            method.invoke(fragment, args);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to invoke " + name, exception);
        }
    }

    private void seedSource(int id, String url, String host, Integer listId) {
        HostsSource source = new HostsSource();
        source.setId(id);
        source.setLabel("Removal test " + id);
        source.setUrl(url);
        source.setEnabled(true);
        source.setFilterListId(listId);
        if (listId != null) source.setFilterListSelectedUrl(url);
        database.hostsSourceDao().insert(source);
        HostListItem item = new HostListItem();
        item.setHost(host);
        item.setType(BLOCKED);
        item.setKind(RuleKind.EXACT);
        item.setEnabled(true);
        item.setSourceId(id);
        item.setGeneration(database.hostEntryDao().getActiveGeneration());
        database.hostsListItemDao().insert(item);
        database.hostsSourceDao().updateActiveRuleStats(id);
    }

    private void seedDirectoryCache() {
        long now = System.currentTimeMillis();
        assertTrue(application.getSharedPreferences("filterlists_cache", Context.MODE_PRIVATE)
                .edit().clear()
                .putString("listsJson", "[{\"id\":701,\"name\":\"First removal test\","
                        + "\"syntaxIds\":[1]},"
                        + "{\"id\":702,\"name\":\"Second removal test\",\"syntaxIds\":[1]}]")
                .putString("syntaxesJson", "[{\"id\":1,\"name\":\"Hosts\"}]")
                .putString("tagsJson", "[]")
                .putString("languagesJson", "[]")
                .putLong("cachedAt", now)
                .putLong("tagsCachedAt", now)
                .commit());
    }

    private void setApplicationField(String name, Object value) throws Exception {
        Field field = AdAwayApplication.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(application, value);
    }

    private static void waitFor(String description, BooleanSupplier condition) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            SystemClock.sleep(50);
        }
        throw new AssertionError("Timed out waiting for " + description);
    }

    private static void drainDiskIo() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AppExecutors.getInstance().diskIO().execute(latch::countDown);
        assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    private static final class RecordingSourceModel extends SourceModel {
        final AtomicInteger syncCount = new AtomicInteger();
        final AtomicInteger networkRequests = new AtomicInteger();
        final CountDownLatch syncEntered = new CountDownLatch(1);
        final CountDownLatch releaseSync = new CountDownLatch(1);
        volatile boolean pauseSync;
        private final OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            networkRequests.incrementAndGet();
            throw new IOException("Unsubscribe must not require network access");
        }).build();

        RecordingSourceModel(Context context) { super(context); }

        @Override
        public OkHttpClient getHttpClientForUi() { return client; }

        @Override
        public boolean checkAndRetrieveHostsSources() {
            networkRequests.incrementAndGet();
            return false;
        }

        @Override
        public void syncHostEntries() {
            if (pauseSync) {
                syncEntered.countDown();
                try {
                    assertTrue(releaseSync.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
            }
            super.syncHostEntries();
            syncCount.incrementAndGet();
        }
    }

    private static final class RecordingVpnModel extends VpnModel {
        final AtomicInteger applyCount = new AtomicInteger();

        RecordingVpnModel(Context context) { super(context); }

        @Override
        public void apply() { applyCount.incrementAndGet(); }
    }

    private final class RecordingRootModel extends AdBlockModel {
        final AtomicInteger applyCount = new AtomicInteger();
        volatile boolean installedRemovedHost;
        volatile boolean installedUnrelatedHost;

        RecordingRootModel(Context context) { super(context); }

        @Override
        public AdBlockMethod getMethod() { return ROOT; }

        @Override
        public void apply() {
            installedRemovedHost = database.hostEntryDao().getAllForRootHostsFile().stream()
                    .anyMatch(entry -> HOST.equals(entry.getHost()));
            installedUnrelatedHost = database.hostEntryDao().getAllForRootHostsFile().stream()
                    .anyMatch(entry -> UNRELATED_HOST.equals(entry.getHost()));
            applyCount.incrementAndGet();
        }

        @Override
        public void revert() { }

        @Override
        public boolean isRecordingLogs() { return false; }

        @Override
        public void setRecordingLogs(boolean recording) { }

        @Override
        public List<String> getLogs() { return Collections.emptyList(); }

        @Override
        public void clearLogs() { }
    }
}
