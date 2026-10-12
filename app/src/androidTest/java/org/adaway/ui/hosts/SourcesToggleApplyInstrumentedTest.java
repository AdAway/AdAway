package org.adaway.ui.hosts;

import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.model.adblocking.AdBlockMethod.ROOT;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.switchmaterial.SwitchMaterial;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.RuleKind;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.source.SourceModel;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.home.HomeActivity;
import org.adaway.util.AppExecutors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class SourcesToggleApplyInstrumentedTest {
    private static final int TIMEOUT_MS = 10_000;
    private static final int TEST_SOURCE_ID = 1711;
    private static final String TEST_SOURCE_LABEL = "Sources toggle proof source";
    private static final String TEST_SOURCE_URL =
            "content://org.adaway.test.hosts/sources-toggle.txt";
    private static final String TEST_HOST = "sources-toggle.example";

    private Context context;
    private AdAwayApplication application;
    private SourceModel originalSourceModel;
    private RecordingAdBlockModel recordingAdBlockModel;

    @Before
    public void setUp() throws Exception {
        this.context = ApplicationProvider.getApplicationContext();
        this.application = (AdAwayApplication) this.context.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up Sources toggle");
        AppDatabase.getInstance(this.context).getOpenHelper().getWritableDatabase();
        waitForDiskIoIdle();
        resetFixture(this.context);
        seedEnabledSourceWithActiveItem(this.context);
        PreferenceHelper.setAbBlockMethod(this.context, ROOT);
        this.originalSourceModel = this.application.getSourceModel();
        injectSourceModel(this.application, new LocalSyncSourceModel(this.context));
        this.recordingAdBlockModel = new RecordingAdBlockModel(this.context);
        injectAdBlockModel(this.application, this.recordingAdBlockModel);
    }

    @After
    public void tearDown() throws Exception {
        if (this.application != null) {
            injectAdBlockModel(this.application, null);
            injectSourceModel(this.application, this.originalSourceModel);
        }
        if (this.context != null) {
            resetFixture(this.context);
            InstrumentedTestState.resetForPassiveRootUi(this.context, "tear down Sources toggle");
        }
    }

    @Test(timeout = 60_000)
    public void togglingSourceOffPersistsRowsAndApplyRunsProtectionUpdate() {
        ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class);
        HomeActivity activity = getCurrentActivity(scenario);
        try {
            navigateToSources(scenario);
            waitForSourceSwitch(scenario, true);
            turnSourceSwitchOff(scenario);

            waitForSourceSelection(false);
            waitForEnabledSourceItems(0);
            waitForText(activity, this.context.getString(
                    R.string.notification_configuration_changed));

            clickSnackbarAction(activity);

            waitForApplyCount(1);
            assertFalse(AppDatabase.getInstance(this.context)
                    .hostsSourceDao()
                    .getById(TEST_SOURCE_ID)
                    .orElseThrow()
                    .isEnabled());
        } finally {
            finishActivity(activity);
            scenario.close();
        }
    }

    private static void finishActivity(HomeActivity activity) {
        // Scenario.close() waits for global idleness before it requests destruction.
        // Finish directly so active view animations cannot prevent that request.
        InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        AtomicBoolean destroyed = new AtomicBoolean(false);
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    destroyed.set(activity.isDestroyed()));
            if (destroyed.get()) {
                return;
            }
            SystemClock.sleep(50);
        }
        throw new AssertionError("HomeActivity did not finish before scenario cleanup.");
    }

    private static void navigateToSources(ActivityScenario<HomeActivity> scenario) {
        scenario.onActivity(activity -> activity.navigateTo(R.id.nav_sources));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static HomeActivity getCurrentActivity(ActivityScenario<HomeActivity> scenario) {
        AtomicReference<HomeActivity> activityRef = new AtomicReference<>();
        scenario.onActivity(activityRef::set);
        HomeActivity activity = activityRef.get();
        assertNotNull("Expected launched HomeActivity.", activity);
        return activity;
    }

    private void waitForSourceSwitch(
            ActivityScenario<HomeActivity> scenario,
            boolean expectedChecked) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        AtomicReference<String> lastState = new AtomicReference<>("not found");
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicBoolean matched = new AtomicBoolean(false);
            scenario.onActivity(activity -> {
                SwitchMaterial toggle = findSourceSwitch(
                        activity.getWindow().getDecorView(),
                        sourceSwitchDescription());
                if (toggle == null) {
                    return;
                }
                lastState.set("checked=" + toggle.isChecked()
                        + ", shown=" + toggle.isShown());
                matched.set(toggle.isShown() && toggle.isChecked() == expectedChecked);
            });
            if (matched.get()) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Source switch did not become checked="
                + expectedChecked + " (" + lastState.get() + ").");
    }

    private void turnSourceSwitchOff(ActivityScenario<HomeActivity> scenario) {
        scenario.onActivity(activity -> {
            SwitchMaterial toggle = findSourceSwitch(
                    activity.getWindow().getDecorView(),
                    sourceSwitchDescription());
            assertNotNull("Expected the seeded source switch to be visible.", toggle);
            assertTrue("Expected the seeded source to start enabled.", toggle.isChecked());
            toggle.setChecked(false);
        });
    }

    private void waitForText(HomeActivity activity, String expectedText) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicBoolean found = new AtomicBoolean(false);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    found.set(hasVisibleText(activity.getWindow().getDecorView(), expectedText)));
            if (found.get()) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Text did not appear: " + expectedText);
    }

    private void clickSnackbarAction(HomeActivity activity) {
        String actionText = this.context.getString(
                R.string.notification_configuration_changed_action);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            TextView action = findSnackbarAction(
                    activity.getWindow().getDecorView(),
                    actionText);
            assertNotNull("Expected pending-configuration snackbar action.", action);
            action.performClick();
        });
    }

    private void waitForApplyCount(int expectedCount) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            String failure = this.recordingAdBlockModel.failure.get();
            if (failure != null) {
                throw new AssertionError(failure);
            }
            if (this.recordingAdBlockModel.applyCount.get() >= expectedCount) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Sources toggle apply did not reach AdBlockModel.apply().");
    }

    private void waitForSourceSelection(boolean expectedEnabled) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            boolean enabled = AppDatabase.getInstance(this.context)
                    .hostsSourceDao()
                    .getById(TEST_SOURCE_ID)
                    .orElseThrow()
                    .isEnabled();
            if (enabled == expectedEnabled) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Source enabled state did not become " + expectedEnabled);
    }

    private void waitForEnabledSourceItems(int expectedCount) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            int count = countEnabledSourceItems(this.context);
            if (count == expectedCount) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Enabled source item count did not become " + expectedCount);
    }

    private String sourceSwitchDescription() {
        return this.context.getString(R.string.filter_source_toggle_description, TEST_SOURCE_LABEL);
    }

    private static SwitchMaterial findSourceSwitch(View view, String contentDescription) {
        if (view instanceof SwitchMaterial
                && view.isShown()
                && R.id.sourceSwitch == view.getId()
                && contentDescription.contentEquals(view.getContentDescription())) {
            return (SwitchMaterial) view;
        }
        if (!(view instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            SwitchMaterial match = findSourceSwitch(group.getChildAt(i), contentDescription);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static boolean hasVisibleText(View view, String expectedText) {
        if (view instanceof TextView
                && view.isShown()
                && expectedText.contentEquals(((TextView) view).getText())) {
            return true;
        }
        if (!(view instanceof ViewGroup)) {
            return false;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (hasVisibleText(group.getChildAt(i), expectedText)) {
                return true;
            }
        }
        return false;
    }

    private static TextView findSnackbarAction(View view, String expectedText) {
        if (view instanceof TextView
                && view.isShown()
                && com.google.android.material.R.id.snackbar_action == view.getId()
                && expectedText.contentEquals(((TextView) view).getText())) {
            return (TextView) view;
        }
        if (!(view instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            TextView match = findSnackbarAction(group.getChildAt(i), expectedText);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static void seedEnabledSourceWithActiveItem(Context context) {
        runDatabaseWork(context, database -> {
            HostsSource source = new HostsSource();
            source.setId(TEST_SOURCE_ID);
            source.setLabel(TEST_SOURCE_LABEL);
            source.setUrl(TEST_SOURCE_URL);
            source.setEnabled(true);
            source.setLocalModificationDate(ZonedDateTime.now());
            source.setOnlineModificationDate(source.getLocalModificationDate());
            source.setSize(1);
            source.setActiveRuleCount(1);
            source.setBlockedCount(1);
            source.setBlockedExactCount(1);
            database.hostsSourceDao().insert(source);

            HostListItem item = new HostListItem();
            item.setHost(TEST_HOST);
            item.setType(BLOCKED);
            item.setKind(RuleKind.EXACT);
            item.setEnabled(true);
            item.setSourceId(TEST_SOURCE_ID);
            item.setGeneration(0);
            database.hostsListItemDao().insert(item);
        });
    }

    private static void resetFixture(Context context) {
        runDatabaseWork(context, database -> {
            database.getOpenHelper().getWritableDatabase().execSQL("DELETE FROM hosts_lists");
            database.hostEntryDao().clear();
            database.hostEntryDao().clearRootExport();
            database.hostsSourceDao().deleteAll();
            database.getOpenHelper().getWritableDatabase().execSQL(
                    "INSERT OR REPLACE INTO hosts_meta (id, active_generation) VALUES (0, 0)");
            database.getOpenHelper().getWritableDatabase().execSQL(
                    "INSERT OR REPLACE INTO hosts_stats "
                            + "(id, blocked_count, blocked_exact_count, allowed_count, "
                            + "redirected_count, active_rule_count) VALUES (0, 0, 0, 0, 0, 0)");
        });
    }

    private static void runDatabaseWork(Context context, DatabaseWork work) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> work.run(AppDatabase.getInstance(context)));
        try {
            future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Database fixture work failed.", exception);
        } finally {
            executor.shutdownNow();
        }
    }

    private static int countEnabledSourceItems(Context context) {
        try (Cursor cursor = AppDatabase.getInstance(context)
                .getOpenHelper()
                .getReadableDatabase()
                .query("SELECT COUNT(*) FROM hosts_lists WHERE source_id = ? AND enabled = 1",
                        new Object[]{TEST_SOURCE_ID})) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    private static void waitForDiskIoIdle() {
        CountDownLatch latch = new CountDownLatch(1);
        AppExecutors.getInstance().diskIO().execute(latch::countDown);
        try {
            if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new AssertionError("Timed out waiting for app disk executor to become idle.");
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for app disk executor.",
                    interruptedException);
        }
    }

    private static void injectAdBlockModel(
            AdAwayApplication application,
            AdBlockModel adBlockModel) throws Exception {
        Field field = AdAwayApplication.class.getDeclaredField("adBlockModel");
        field.setAccessible(true);
        field.set(application, adBlockModel);
    }

    private static void injectSourceModel(
            AdAwayApplication application,
            SourceModel sourceModel) throws Exception {
        Field field = AdAwayApplication.class.getDeclaredField("sourceModel");
        field.setAccessible(true);
        field.set(application, sourceModel);
    }

    private interface DatabaseWork {
        void run(AppDatabase database);
    }

    private static final class LocalSyncSourceModel extends SourceModel {
        private final Context context;

        private LocalSyncSourceModel(Context context) {
            super(context);
            this.context = context;
        }

        @Override
        public boolean checkAndRetrieveHostsSources() {
            AppDatabase.getInstance(this.context).hostEntryDao().sync();
            return true;
        }
    }

    private static final class RecordingAdBlockModel extends AdBlockModel {
        private final AtomicInteger applyCount = new AtomicInteger();
        private final AtomicReference<String> failure = new AtomicReference<>();

        private RecordingAdBlockModel(Context context) {
            super(context);
        }

        @Override
        public AdBlockMethod getMethod() {
            return ROOT;
        }

        @Override
        public void apply() {
            int enabledRows = countEnabledSourceItems(this.context);
            if (enabledRows != 0) {
                this.failure.compareAndSet(null,
                        "Sources apply ran before source item rows were disabled.");
            }
            this.applyCount.incrementAndGet();
            this.applied.postValue(true);
        }

        @Override
        public void revert() {
            this.applied.postValue(false);
        }

        @Override
        public boolean isRecordingLogs() {
            return false;
        }

        @Override
        public void setRecordingLogs(boolean recording) {
            // No-op for the test model.
        }

        @Override
        public List<String> getLogs() {
            return Collections.emptyList();
        }

        @Override
        public void clearLogs() {
            // No-op for the test model.
        }
    }
}
