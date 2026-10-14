package org.adaway.ui.home;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.lifecycle.MutableLiveData;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.model.source.FilterListCatalog;
import org.adaway.model.source.FilterOperationState;
import org.adaway.model.source.SourceModel;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.testing.UiEvidence;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class HomeCountersInstrumentedTest {
    private static final int TIMEOUT_MS = 10_000;
    private static final int ACTIVE_GENERATION = 7;
    private static final int CURRENT_SOURCE_ID = 1201;
    private static final int OUTDATED_SOURCE_ID = 1202;

    private Context context;

    @Before
    public void setUp() throws Exception {
        this.context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up Home counters");
        publishOperationState(this.context, FilterOperationState.idle());
    }

    @After
    public void tearDown() throws Exception {
        if (this.context != null) {
            publishOperationState(this.context, FilterOperationState.idle());
            clearCounterFixture(this.context);
            InstrumentedTestState.resetForPassiveRootUi(this.context, "tear down Home counters");
        }
    }

    @Test
    public void homeCountersReflectSeededRuntimeAndSourceTruth() {
        try (ActivityScenario<HomeActivity> scenario =
                     ActivityScenario.launch(HomeActivity.class)) {
            seedCounterFixture(this.context);

            waitForText(scenario, R.id.blockedHostCounterTextView, "3");
            waitForText(scenario, R.id.allowedHostCounterTextView, "2");
            waitForText(scenario, R.id.redirectHostCounterTextView, "1");
            waitForText(scenario, R.id.upToDateSourcesTextView, "1 up-to-date source");
            waitForText(scenario, R.id.outdatedSourcesTextView, "1 outdated source");
        }
    }

    @Test
    public void homeFreshnessCountsNeverDownloadedSubscriptionsAndTracksChanges() {
        try (ActivityScenario<HomeActivity> scenario =
                     ActivityScenario.launch(HomeActivity.class)) {
            runDatabaseWork(this.context, "seeding never-downloaded defaults", database -> {
                resetCounterTables(database);
                for (FilterListCatalog.CatalogEntry entry : FilterListCatalog.getDefaults()) {
                    HostsSource source = entry.toHostsSource();
                    source.setLastDownloadError("Network unavailable");
                    database.hostsSourceDao().insert(source);
                }
            });

            waitForText(scenario, R.id.upToDateSourcesTextView, "0 up-to-date sources");
            waitForText(scenario, R.id.outdatedSourcesTextView, "4 outdated sources");
            scenario.recreate();
            waitForText(scenario, R.id.upToDateSourcesTextView, "0 up-to-date sources");
            waitForText(scenario, R.id.outdatedSourcesTextView, "4 outdated sources");
            UiEvidence.capture(this.context, "home-never-downloaded-0-current-4-outdated");

            runDatabaseWork(this.context, "recording a successful empty download", database -> {
                HostsSource source = database.hostsSourceDao().getAll().get(0);
                ZonedDateTime downloadedAt = ZonedDateTime.parse("2026-10-14T00:00:00Z");
                database.hostsSourceDao().updateModificationDates(
                        source.getId(), downloadedAt, downloadedAt);
                database.hostsSourceDao().clearDownloadError(source.getId());
            });
            waitForText(scenario, R.id.upToDateSourcesTextView, "1 up-to-date source");
            waitForText(scenario, R.id.outdatedSourcesTextView, "3 outdated sources");
            UiEvidence.capture(this.context, "home-empty-download-1-current-3-outdated");

            runDatabaseWork(this.context, "disabling a never-downloaded source", database -> {
                HostsSource source = database.hostsSourceDao().getAll().get(1);
                database.hostsSourceDao().applySourceSelection(source.getId(), false);
            });
            waitForText(scenario, R.id.upToDateSourcesTextView, "1 up-to-date source");
            waitForText(scenario, R.id.outdatedSourcesTextView, "2 outdated sources");
            UiEvidence.capture(this.context, "home-disabled-source-1-current-2-outdated");
        }
    }

    @Test
    public void homeCountersFreezeDuringActiveUpdateAndRefreshAfterCompletion()
            throws Exception {
        try (ActivityScenario<HomeActivity> scenario =
                     ActivityScenario.launch(HomeActivity.class)) {
            seedCounterFixture(this.context);

            waitForText(scenario, R.id.blockedHostCounterTextView, "3");
            waitForText(scenario, R.id.allowedHostCounterTextView, "2");
            waitForText(scenario, R.id.redirectHostCounterTextView, "1");

            publishOperationState(this.context, operationState(
                    FilterOperationState.Phase.PARSE,
                    62,
                    9L));
            waitForVisibility(scenario, R.id.multiPhaseProgressContainer, View.VISIBLE);

            seedUpdatedCounterFixture(this.context);

            assertTextRemainsFor(scenario, R.id.blockedHostCounterTextView, "3", 800);
            assertTextRemainsFor(scenario, R.id.allowedHostCounterTextView, "2", 800);
            assertTextRemainsFor(scenario, R.id.redirectHostCounterTextView, "1", 800);

            publishOperationState(this.context, operationState(
                    FilterOperationState.Phase.COMPLETE,
                    100,
                    14L));

            waitForText(scenario, R.id.blockedHostCounterTextView, "5");
            waitForText(scenario, R.id.allowedHostCounterTextView, "4");
            waitForText(scenario, R.id.redirectHostCounterTextView, "2");
        } finally {
            publishOperationState(this.context, FilterOperationState.idle());
        }
    }

    private static void seedCounterFixture(Context context) {
        runDatabaseWork(context, "seeding Home counter fixture", database -> {
            resetCounterTables(database);

            database.hostsSourceDao().insert(source(
                    CURRENT_SOURCE_ID,
                    "Counter current source",
                    "https://counter-current.test/hosts.txt",
                    ZonedDateTime.parse("2026-06-01T00:00:00Z"),
                    ZonedDateTime.parse("2026-06-01T00:00:00Z")));
            database.hostsSourceDao().insert(source(
                    OUTDATED_SOURCE_ID,
                    "Counter outdated source",
                    "https://counter-outdated.test/hosts.txt",
                    ZonedDateTime.parse("2026-05-01T00:00:00Z"),
                    ZonedDateTime.parse("2026-06-01T00:00:00Z")));

            insertItem(database, "ads-one.example", ListType.BLOCKED, CURRENT_SOURCE_ID);
            insertItem(database, "ads-two.example", ListType.BLOCKED, CURRENT_SOURCE_ID);
            insertItem(database, "ads-three.example", ListType.BLOCKED, OUTDATED_SOURCE_ID);
            insertItem(database, "allow-one.example", ListType.ALLOWED, USER_SOURCE_ID);
            insertItem(database, "allow-two.example", ListType.ALLOWED, CURRENT_SOURCE_ID);
            insertItem(database, "redirect-one.example", ListType.REDIRECTED, OUTDATED_SOURCE_ID);

            database.hostsSourceDao().updateActiveRuleStats(CURRENT_SOURCE_ID);
            database.hostsSourceDao().updateActiveRuleStats(OUTDATED_SOURCE_ID);
            database.hostEntryDao().sync();
        });
    }

    private static void seedUpdatedCounterFixture(Context context) {
        runDatabaseWork(context, "seeding updated Home counter fixture", database -> {
            resetCounterTables(database);

            database.hostsSourceDao().insert(source(
                    CURRENT_SOURCE_ID,
                    "Counter current source",
                    "https://counter-current.test/hosts.txt",
                    ZonedDateTime.parse("2026-06-01T00:00:00Z"),
                    ZonedDateTime.parse("2026-06-01T00:00:00Z")));
            database.hostsSourceDao().insert(source(
                    OUTDATED_SOURCE_ID,
                    "Counter outdated source",
                    "https://counter-outdated.test/hosts.txt",
                    ZonedDateTime.parse("2026-05-01T00:00:00Z"),
                    ZonedDateTime.parse("2026-06-01T00:00:00Z")));

            insertItem(database, "ads-one.example", ListType.BLOCKED, CURRENT_SOURCE_ID);
            insertItem(database, "ads-two.example", ListType.BLOCKED, CURRENT_SOURCE_ID);
            insertItem(database, "ads-three.example", ListType.BLOCKED, OUTDATED_SOURCE_ID);
            insertItem(database, "ads-four.example", ListType.BLOCKED, CURRENT_SOURCE_ID);
            insertItem(database, "ads-five.example", ListType.BLOCKED, OUTDATED_SOURCE_ID);
            insertItem(database, "allow-one.example", ListType.ALLOWED, USER_SOURCE_ID);
            insertItem(database, "allow-two.example", ListType.ALLOWED, CURRENT_SOURCE_ID);
            insertItem(database, "allow-three.example", ListType.ALLOWED, CURRENT_SOURCE_ID);
            insertItem(database, "allow-four.example", ListType.ALLOWED, OUTDATED_SOURCE_ID);
            insertItem(database, "redirect-one.example", ListType.REDIRECTED, OUTDATED_SOURCE_ID);
            insertItem(database, "redirect-two.example", ListType.REDIRECTED, CURRENT_SOURCE_ID);

            database.hostsSourceDao().updateActiveRuleStats(CURRENT_SOURCE_ID);
            database.hostsSourceDao().updateActiveRuleStats(OUTDATED_SOURCE_ID);
            database.hostEntryDao().sync();
        });
    }

    private static void clearCounterFixture(Context context) {
        runDatabaseWork(context, "clearing Home counter fixture",
                HomeCountersInstrumentedTest::resetCounterTables);
    }

    private static void resetCounterTables(AppDatabase database) {
        database.getOpenHelper().getWritableDatabase().execSQL("DELETE FROM hosts_lists");
        database.hostEntryDao().clear();
        database.hostsSourceDao().deleteAll();
        database.getOpenHelper().getWritableDatabase().execSQL(
                "INSERT OR REPLACE INTO hosts_meta (id, active_generation) VALUES (0, "
                        + ACTIVE_GENERATION + ")");
        database.getOpenHelper().getWritableDatabase().execSQL(
                "INSERT OR REPLACE INTO hosts_stats "
                        + "(id, blocked_count, blocked_exact_count, allowed_count, "
                        + "redirected_count, active_rule_count) VALUES (0, 0, 0, 0, 0, 0)");
    }

    private static HostsSource source(int id, String label, String url,
            ZonedDateTime localModificationDate, ZonedDateTime onlineModificationDate) {
        HostsSource source = new HostsSource();
        source.setId(id);
        source.setLabel(label);
        source.setUrl(url);
        source.setEnabled(true);
        source.setLocalModificationDate(localModificationDate);
        source.setOnlineModificationDate(onlineModificationDate);
        return source;
    }

    private static void insertItem(
            AppDatabase database,
            String host,
            ListType type,
            int sourceId) {
        HostListItem item = new HostListItem();
        item.setHost(host);
        item.setType(type);
        item.setEnabled(true);
        item.setSourceId(sourceId);
        item.setGeneration(ACTIVE_GENERATION);
        if (type == ListType.REDIRECTED) {
            item.setRedirection("127.0.0.1");
        }
        database.hostsListItemDao().insert(item);
    }

    private static void waitForText(
            ActivityScenario<HomeActivity> scenario,
            int viewId,
            String expectedText) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            AtomicReference<String> actualText = readText(scenario, viewId);
            if (expectedText.equals(actualText.get())) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("View " + viewId + " did not show \"" + expectedText
                + "\". Last value: \"" + readText(scenario, viewId).get() + "\".");
    }

    private static void assertTextRemainsFor(
            ActivityScenario<HomeActivity> scenario,
            int viewId,
            String expectedText,
            long durationMs) {
        long deadline = SystemClock.uptimeMillis() + durationMs;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            String actualText = readText(scenario, viewId).get();
            if (!expectedText.equals(actualText)) {
                throw new AssertionError("View " + viewId + " changed from \""
                        + expectedText + "\" to \"" + actualText
                        + "\" while update progress was active.");
            }
            SystemClock.sleep(100);
        }
    }

    private static void waitForVisibility(
            ActivityScenario<HomeActivity> scenario,
            int viewId,
            int expectedVisibility) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            AtomicReference<Integer> actualVisibility = new AtomicReference<>(View.GONE);
            scenario.onActivity(activity -> {
                View view = activity.findViewById(viewId);
                actualVisibility.set(view == null ? View.GONE : view.getVisibility());
            });
            if (actualVisibility.get() == expectedVisibility) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("View " + viewId + " did not reach visibility "
                + expectedVisibility + ".");
    }

    private static AtomicReference<String> readText(
            ActivityScenario<HomeActivity> scenario,
            int viewId) {
        AtomicReference<String> actualText = new AtomicReference<>("");
        scenario.onActivity(activity -> {
            TextView view = activity.findViewById(viewId);
            actualText.set(view == null ? "" : view.getText().toString());
        });
        return actualText;
    }

    private static FilterOperationState operationState(
            FilterOperationState.Phase phase,
            int overallPercent,
            long parsedHostCount) throws Exception {
        Constructor<FilterOperationState> constructor =
                FilterOperationState.class.getDeclaredConstructor(
                        FilterOperationState.Kind.class,
                        FilterOperationState.Phase.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        long.class,
                        String.class,
                        String.class,
                        long.class,
                        long.class,
                        boolean.class,
                        boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                FilterOperationState.Kind.SOURCE_UPDATE,
                phase,
                2,
                2,
                1,
                phase == FilterOperationState.Phase.COMPLETE ? 2 : 1,
                overallPercent,
                parsedHostCount,
                "Counter update",
                null,
                1L,
                2L,
                false,
                false);
    }

    @SuppressWarnings("unchecked")
    private static void publishOperationState(Context context, FilterOperationState state)
            throws Exception {
        SourceModel sourceModel =
                ((AdAwayApplication) context.getApplicationContext()).getSourceModel();
        Field field = SourceModel.class.getDeclaredField("filterOperationState");
        field.setAccessible(true);
        MutableLiveData<FilterOperationState> liveData =
                (MutableLiveData<FilterOperationState>) field.get(sourceModel);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> liveData.setValue(state));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static void runDatabaseWork(Context context, String description, DatabaseWork work) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> work.run(AppDatabase.getInstance(context)));
        try {
            future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while " + description + ".",
                    interruptedException);
        } catch (Exception exception) {
            throw new AssertionError("Failed while " + description + ".", exception);
        } finally {
            executor.shutdownNow();
        }
    }

    private interface DatabaseWork {
        void run(AppDatabase database);
    }
}
