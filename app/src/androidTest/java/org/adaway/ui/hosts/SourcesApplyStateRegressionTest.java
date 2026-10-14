package org.adaway.ui.hosts;

import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.model.adblocking.AdBlockMethod.ROOT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.appbar.MaterialToolbar;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.RuleKind;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.error.HostError;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.source.SourceModel;
import org.adaway.model.source.SourceUpdateService;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

@RunWith(AndroidJUnit4.class)
public class SourcesApplyStateRegressionTest {
    private static final long TIMEOUT_MS = 15_000;
    private static final int SOURCE_A = 1911;
    private static final int SOURCE_B = 1912;

    private Context context;
    private AdAwayApplication application;
    private AppDatabase database;
    private SourceModel originalSourceModel;
    private ControlledSourceModel sourceModel;
    private ControlledAdBlockModel adBlockModel;
    private ActivityScenario<HomeActivity> scenario;
    private LiveData<List<HostsSource>> sourceRows;
    private Observer<List<HostsSource>> sourceObserver;
    private final AtomicReference<List<HostsSource>> observedSources = new AtomicReference<>();
    private final AtomicInteger sourceEmissions = new AtomicInteger();

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        application = (AdAwayApplication) context.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(context, "set up Sources apply regressions");
        database = AppDatabase.getInstance(context);
        database.getOpenHelper().getWritableDatabase();
        CountDownLatch diskIdle = new CountDownLatch(1);
        AppExecutors.getInstance().diskIO().execute(diskIdle::countDown);
        assertTrue(diskIdle.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        resetFixture();
        seedSource(SOURCE_A);
        seedSource(SOURCE_B);
        database.hostEntryDao().sync();
        originalSourceModel = application.getSourceModel();
        sourceModel = new ControlledSourceModel(context);
        adBlockModel = new ControlledAdBlockModel(context);
        inject("sourceModel", sourceModel);
        inject("adBlockModel", adBlockModel);
        sourceRows = database.hostsSourceDao().loadAll();
        sourceObserver = sources -> {
            observedSources.set(sources);
            sourceEmissions.incrementAndGet();
        };
        onMain(() -> sourceRows.observeForever(sourceObserver));
        await("Initial source rows", () -> observedSources.get() != null
                && observedSources.get().size() == 2);
    }

    @After
    public void tearDown() throws Exception {
        if (adBlockModel != null) {
            adBlockModel.releaseApply();
            if (adBlockModel.entered.getCount() == 0) {
                assertTrue(adBlockModel.completed.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            }
        }
        onMain(() -> {
            if (sourceRows != null && sourceObserver != null) {
                sourceRows.removeObserver(sourceObserver);
            }
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)) {
                activity.finish();
            }
        });
        if (scenario != null) scenario.close();
        if (application != null) {
            inject("adBlockModel", null);
            inject("sourceModel", originalSourceModel);
        }
        if (database != null) resetFixture();
        if (context != null) {
            InstrumentedTestState.resetForPassiveRootUi(context,
                    "tear down Sources apply regressions");
        }
    }

    @Test(timeout = 90_000)
    public void targetedUpdateLeavesReenabledUnrelatedSourcePending() {
        sourceModel.realUpdates = true;
        HomeActivity activity = launchSources();
        selectSource(SOURCE_B, false);
        waitForText(activity, R.string.notification_configuration_changed);
        clickApplyAction(activity);
        waitForText(activity, R.string.notification_configuration_applied);
        assertEquals(0, sourceRuleCount(SOURCE_B));

        selectSource(SOURCE_B, true);
        waitForText(activity, R.string.notification_configuration_changed);
        HostsSource sourceA = database.hostsSourceDao().getById(SOURCE_A).orElseThrow();
        onMain(() -> sourcesFragment(activity).updateSource(sourceA));
        waitForStatus(activity, HostsSourcesViewModel.ApplyStatus.SUCCEEDED);

        waitForText(activity, R.string.notification_configuration_changed);
        assertTrue(onMainBoolean(() -> viewModelOnMain(activity).getApplyState().isPending()));
        assertEquals(1, sourceModel.targetedUpdates.get());
        assertEquals(0, sourceRuleCount(SOURCE_B));
        assertTrue(sourceRuleCount(SOURCE_A) > 0);
        assertTrue(database.hostsSourceDao().getById(SOURCE_B).orElseThrow().isEnabled());
    }

    @Test(timeout = 90_000)
    public void failureWithUnchangedSelectionSurvivesLateMetadataUntilRetrySucceeds() {
        HomeActivity activity = launchSources();
        assertFalse(onMainBoolean(() -> viewModelOnMain(activity).getApplyState().isPending()));
        sourceModel.failUpdate.set(true);
        clickUpdateAll(activity);
        waitForText(activity, R.string.notification_configuration_failed);

        database.hostsSourceDao().updateDownloadError(SOURCE_B, "Late download failure metadata");
        await("Late Room metadata", () -> "Late download failure metadata".equals(
                observedSource(SOURCE_B).getLastDownloadError()));
        assertTextRemains(activity, R.string.notification_configuration_failed);
        assertFalse(onMainBoolean(() -> viewModelOnMain(activity).getApplyState().isPending()));

        sourceModel.failUpdate.set(false);
        clickApplyAction(activity);
        waitForText(activity, R.string.notification_configuration_applied);
        waitForTextAbsent(activity, R.string.notification_configuration_failed);
        waitForTextAbsent(activity, R.string.notification_configuration_changed);
        assertEquals(2, sourceModel.fullUpdates.get());
        assertEquals(1, adBlockModel.applyCount.get());
    }

    @Test(timeout = 90_000)
    public void manualApplyCompletionRefreshesRecreatedViewWithoutAnotherSourceEmission()
            throws Exception {
        assertCompletionAfterRecreation(false);
    }

    @Test(timeout = 90_000)
    public void toolbarCompletionRefreshesRecreatedViewWithoutAnotherSourceEmission()
            throws Exception {
        assertCompletionAfterRecreation(true);
    }

    @Test(timeout = 90_000)
    public void returningToOriginalSelectionDuringApplyRemainsPendingAfterCompletion()
            throws Exception {
        sourceModel.realUpdates = true;
        HomeActivity activity = launchSources();
        selectSource(SOURCE_B, false);
        waitForText(activity, R.string.notification_configuration_changed);
        adBlockModel.blockApply();
        clickApplyAction(activity);
        assertTrue(adBlockModel.entered.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals(0, sourceRuleCount(SOURCE_B));

        selectSource(SOURCE_B, true);
        adBlockModel.releaseApply();
        waitForStatus(activity, HostsSourcesViewModel.ApplyStatus.SUCCEEDED);

        waitForText(activity, R.string.notification_configuration_changed);
        assertTrue(onMainBoolean(() -> viewModelOnMain(activity).getApplyState().isPending()));
        assertEquals(0, sourceRuleCount(SOURCE_B));
    }

    @Test(timeout = 90_000)
    public void selectionChangedAndRestoredDuringApplyStillRequiresAcknowledgement()
            throws Exception {
        HomeActivity activity = launchSources();
        selectSource(SOURCE_B, false);
        waitForText(activity, R.string.notification_configuration_changed);
        adBlockModel.blockApply();
        clickApplyAction(activity);
        assertTrue(adBlockModel.entered.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

        HostsSource sourceA = database.hostsSourceDao().getById(SOURCE_A).orElseThrow();
        onMain(() -> sourcesFragment(activity).setEnabled(sourceA, false));
        await("First concurrent selection", () -> !observedSource(SOURCE_A).isEnabled());
        onMain(() -> sourcesFragment(activity).setEnabled(sourceA, true));
        await("Restored concurrent selection", () -> observedSource(SOURCE_A).isEnabled());
        adBlockModel.releaseApply();
        waitForStatus(activity, HostsSourcesViewModel.ApplyStatus.SUCCEEDED);

        waitForText(activity, R.string.notification_configuration_changed);
        assertTrue(onMainBoolean(() -> viewModelOnMain(activity).getApplyState().isPending()));
    }

    @Test(timeout = 90_000)
    public void uncertainAutomaticCompletionKeepsReenabledSourcePendingAfterRecreation()
            throws Exception {
        sourceModel.realUpdates = true;
        HomeActivity original = launchSources();
        HostsSourcesViewModel retained = viewModel(original);
        onMain(() -> retained.observeAutomaticWork(null, false, null));
        assertFalse(onMainBoolean(() -> retained.getApplyState().isPending()));
        selectSource(SOURCE_B, false);
        waitForText(original, R.string.notification_configuration_changed);
        adBlockModel.blockApply();
        WorkManager manager = WorkManager.getInstance(context);
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(
                SourceUpdateService.HostsSourcesImmediateUpdateWorker.class).build();
        manager.enqueueUniqueWork(SourceUpdateService.WORK_NAME_NOW,
                ExistingWorkPolicy.REPLACE, request).getResult()
                .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertTrue(adBlockModel.entered.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals(0, sourceRuleCount(SOURCE_B));

        selectSource(SOURCE_B, true);
        waitForTextAbsent(original, R.string.notification_configuration_changed);
        assertFalse(onMainBoolean(() -> retained.getApplyState().isPending()));
        onMain(original::recreate);
        HomeActivity recreated = waitForActivity(original);
        assertSame(retained, viewModel(recreated));
        waitForTextAbsent(recreated, R.string.notification_configuration_changed);
        int emissionsBeforeCompletion = sourceEmissions.get();

        adBlockModel.releaseApply();

        WorkInfo completed = waitForSuccessfulWork(manager, request);
        assertNull(completed.getOutputData().getString(SourceUpdateService.APPLIED_CONFIGURATION));
        waitForStatus(recreated, HostsSourcesViewModel.ApplyStatus.SUCCEEDED);
        waitForText(recreated, R.string.notification_configuration_changed);
        assertTrue(onMainBoolean(() -> retained.getApplyState().isPending()));
        assertEquals(0, sourceRuleCount(SOURCE_B));
        assertTrue(sourceRuleCount(SOURCE_A) > 0);
        assertTrue(database.hostsSourceDao().getById(SOURCE_B).orElseThrow().isEnabled());
        assertEquals(emissionsBeforeCompletion, sourceEmissions.get());
    }

    @Test(timeout = 90_000)
    public void updateBeforeFirstSourceObservationAcknowledgesUnchangedSelection()
            throws Exception {
        AtomicReference<HostsSourcesViewModel> model = new AtomicReference<>();
        adBlockModel.blockApply();
        onMain(() -> {
            model.set(new HostsSourcesViewModel(application));
            model.get().updateSources(null);
        });
        assertTrue(adBlockModel.entered.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        List<HostsSource> initialRows = database.hostsSourceDao().getAll();
        onMain(() -> model.get().onSourcesChanged(initialRows));

        adBlockModel.releaseApply();

        await("Apply before initial source observation", () -> onMainBoolean(() ->
                model.get().getApplyStatus().getValue()
                        == HostsSourcesViewModel.ApplyStatus.SUCCEEDED));
        assertFalse(onMainBoolean(() -> model.get().getApplyState().isPending()));
        assertEquals(1, sourceModel.fullUpdates.get());
        assertEquals(1, adBlockModel.applyCount.get());
    }

    @Test(timeout = 90_000)
    public void selectionEditedAndRestoredBeforeFirstObservationRemainsPending()
            throws Exception {
        AtomicReference<HostsSourcesViewModel> model = new AtomicReference<>();
        adBlockModel.blockApply();
        onMain(() -> {
            model.set(new HostsSourcesViewModel(application));
            model.get().updateSources(null);
        });
        assertTrue(adBlockModel.entered.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        HostsSource source = database.hostsSourceDao().getById(SOURCE_B).orElseThrow();
        onMain(() -> model.get().setSourceEnabled(source, false));
        await("Selection before initial observation", () -> !observedSource(SOURCE_B).isEnabled());
        onMain(() -> model.get().setSourceEnabled(source, true));
        await("Restored selection before initial observation",
                () -> observedSource(SOURCE_B).isEnabled());
        List<HostsSource> initialRows = database.hostsSourceDao().getAll();
        onMain(() -> model.get().onSourcesChanged(initialRows));

        adBlockModel.releaseApply();

        await("Apply with edits before initial source observation", () -> onMainBoolean(() ->
                model.get().getApplyStatus().getValue()
                        == HostsSourcesViewModel.ApplyStatus.SUCCEEDED));
        assertTrue(onMainBoolean(() -> model.get().getApplyState().isPending()));
    }

    private void assertCompletionAfterRecreation(boolean toolbar) throws Exception {
        HomeActivity original = launchSources();
        HostsSourcesViewModel retained = viewModel(original);
        selectSource(SOURCE_B, false);
        waitForText(original, R.string.notification_configuration_changed);
        adBlockModel.blockApply();
        if (toolbar) clickUpdateAll(original);
        else clickApplyAction(original);
        assertTrue(adBlockModel.entered.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        await("Final update metadata before recreation",
                () -> observedSource(SOURCE_A).getSkippedCount() == 1);
        onMain(original::recreate);
        HomeActivity recreated = waitForActivity(original);
        assertSame(retained, viewModel(recreated));
        waitForStatus(recreated, HostsSourcesViewModel.ApplyStatus.RUNNING);
        int emissionsBeforeCompletion = sourceEmissions.get();

        adBlockModel.releaseApply();

        waitForText(recreated, R.string.notification_configuration_applied);
        waitForTextAbsent(recreated, R.string.notification_configuration_changed);
        assertFalse(onMainBoolean(() -> viewModelOnMain(recreated).getApplyState().isPending()));
        assertEquals(1, sourceModel.fullUpdates.get());
        assertEquals(1, adBlockModel.applyCount.get());
        assertEquals("Completion must update the view without another source emission",
                emissionsBeforeCompletion, sourceEmissions.get());
    }

    private HomeActivity launchSources() {
        scenario = ActivityScenario.launch(HomeActivity.class);
        scenario.onActivity(activity -> activity.navigateTo(R.id.nav_sources));
        HomeActivity activity = waitForActivity(null);
        await("Initial source row", () -> onMainBoolean(() ->
                hasText(activity.getWindow().getDecorView(), "Apply regression source " + SOURCE_A)));
        return activity;
    }

    private static WorkInfo waitForSuccessfulWork(WorkManager manager, OneTimeWorkRequest request)
            throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            WorkInfo info = manager.getWorkInfoById(request.getId())
                    .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (info != null && info.getState().isFinished()) {
                assertEquals(WorkInfo.State.SUCCEEDED, info.getState());
                return info;
            }
            SystemClock.sleep(50);
        }
        throw new AssertionError("Immediate update did not succeed");
    }

    private HomeActivity waitForActivity(HomeActivity previous) {
        AtomicReference<HomeActivity> current = new AtomicReference<>();
        await("Resumed Sources activity", () -> {
            onMain(() -> {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)) {
                    if (activity instanceof HomeActivity && activity != previous) {
                        Fragment tab = ((HomeActivity) activity).getSupportFragmentManager()
                                .findFragmentById(R.id.nav_fragment_container);
                        if (tab != null && tab.getChildFragmentManager()
                                .findFragmentById(R.id.hosts_sources_child_container)
                                instanceof HostsSourcesFragment
                                && activity.findViewById(R.id.hosts_sources_toolbar) != null) {
                            current.set((HomeActivity) activity);
                        }
                    }
                }
            });
            return current.get() != null;
        });
        return current.get();
    }

    private static HostsSourcesFragment sourcesFragment(HomeActivity activity) {
        Fragment tab = activity.getSupportFragmentManager()
                .findFragmentById(R.id.nav_fragment_container);
        assertNotNull(tab);
        return (HostsSourcesFragment) tab.getChildFragmentManager()
                .findFragmentById(R.id.hosts_sources_child_container);
    }

    private static HostsSourcesViewModel viewModelOnMain(HomeActivity activity) {
        return new ViewModelProvider(sourcesFragment(activity)).get(HostsSourcesViewModel.class);
    }

    private static HostsSourcesViewModel viewModel(HomeActivity activity) {
        AtomicReference<HostsSourcesViewModel> model = new AtomicReference<>();
        onMain(() -> model.set(viewModelOnMain(activity)));
        return model.get();
    }

    private static void waitForStatus(HomeActivity activity,
            HostsSourcesViewModel.ApplyStatus status) {
        await("Apply status " + status, () -> onMainBoolean(() ->
                viewModelOnMain(activity).getApplyStatus().getValue() == status));
    }

    private void selectSource(int sourceId, boolean enabled) {
        database.hostsSourceDao().applySourceSelection(sourceId, enabled);
        await("Source selection " + sourceId, () -> observedSource(sourceId).isEnabled() == enabled);
    }

    private HostsSource observedSource(int sourceId) {
        return observedSources.get().stream().filter(source -> source.getId() == sourceId)
                .findFirst().orElseThrow();
    }

    private static void clickUpdateAll(HomeActivity activity) {
        onMain(() -> {
            MaterialToolbar toolbar = activity.findViewById(R.id.hosts_sources_toolbar);
            assertNotNull(toolbar);
            assertTrue(toolbar.getMenu().performIdentifierAction(R.id.action_hosts_update_all, 0));
        });
    }

    private void clickApplyAction(HomeActivity activity) {
        onMain(() -> {
            TextView action = activity.findViewById(com.google.android.material.R.id.snackbar_action);
            assertNotNull(action);
            assertTrue(action.isShown());
            assertEquals(context.getString(R.string.notification_configuration_changed_action),
                    action.getText().toString());
            assertTrue(action.performClick());
        });
    }

    private void waitForText(HomeActivity activity, int resource) {
        await(context.getString(resource), () -> hasText(activity, resource));
    }

    private void waitForTextAbsent(HomeActivity activity, int resource) {
        await("Dismissed " + context.getString(resource), () -> !hasText(activity, resource));
        long deadline = SystemClock.uptimeMillis() + 750;
        while (SystemClock.uptimeMillis() < deadline) {
            assertFalse(context.getString(resource), hasText(activity, resource));
            SystemClock.sleep(50);
        }
    }

    private void assertTextRemains(HomeActivity activity, int resource) {
        long deadline = SystemClock.uptimeMillis() + 750;
        while (SystemClock.uptimeMillis() < deadline) {
            assertTrue(context.getString(resource), hasText(activity, resource));
            SystemClock.sleep(50);
        }
    }

    private boolean hasText(HomeActivity activity, int resource) {
        return onMainBoolean(() -> hasText(activity.getWindow().getDecorView(),
                context.getString(resource)));
    }

    private static boolean hasText(View view, String text) {
        if (view instanceof TextView && view.isShown()
                && text.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hasText(group.getChildAt(i), text)) return true;
            }
        }
        return false;
    }

    private static void await(String description, BooleanSupplier ready) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (ready.getAsBoolean()) return;
            SystemClock.sleep(50);
        }
        throw new AssertionError("Timed out waiting for " + description);
    }

    private static void onMain(Runnable runnable) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(runnable);
    }

    private static boolean onMainBoolean(BooleanSupplier ready) {
        AtomicBoolean value = new AtomicBoolean();
        onMain(() -> value.set(ready.getAsBoolean()));
        return value.get();
    }

    private void seedSource(int sourceId) {
        HostsSource source = new HostsSource();
        source.setId(sourceId);
        source.setLabel("Apply regression source " + sourceId);
        source.setUrl("content://org.adaway.test.hosts/success.txt?source=" + sourceId);
        source.setEnabled(true);
        source.setLocalModificationDate(ZonedDateTime.now().minusDays(1));
        source.setOnlineModificationDate(source.getLocalModificationDate());
        source.setSize(1);
        source.setActiveRuleCount(1);
        source.setBlockedCount(1);
        source.setBlockedExactCount(1);
        database.hostsSourceDao().insert(source);
        HostListItem item = new HostListItem();
        item.setHost("apply-regression-" + sourceId + ".example");
        item.setType(BLOCKED);
        item.setKind(RuleKind.EXACT);
        item.setSourceId(sourceId);
        item.setEnabled(true);
        item.setGeneration(0);
        database.hostsListItemDao().insert(item);
    }

    private int sourceRuleCount(int sourceId) {
        try (Cursor cursor = database.getOpenHelper().getReadableDatabase().query(
                "SELECT COUNT(*) FROM hosts_lists WHERE source_id = ?",
                new Object[]{sourceId})) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    private void resetFixture() {
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
    }

    private void inject(String name, Object value) throws Exception {
        Field field = AdAwayApplication.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(application, value);
    }

    private static final class ControlledSourceModel extends SourceModel {
        private final AppDatabase database;
        private final AtomicInteger fullUpdates = new AtomicInteger();
        private final AtomicInteger targetedUpdates = new AtomicInteger();
        private final AtomicBoolean failUpdate = new AtomicBoolean();
        private boolean realUpdates;

        private ControlledSourceModel(Context context) {
            super(context);
            database = AppDatabase.getInstance(context);
        }

        @Override
        public boolean checkAndRetrieveHostsSources() throws HostErrorException {
            int count = fullUpdates.incrementAndGet();
            if (realUpdates) return super.checkAndRetrieveHostsSources();
            if (failUpdate.get()) {
                database.hostsSourceDao().updateDownloadError(SOURCE_A, "Download failed");
                throw new HostErrorException(HostError.DOWNLOAD_FAILED);
            }
            database.hostsSourceDao().clearDownloadError(SOURCE_A);
            database.hostsSourceDao().clearDownloadError(SOURCE_B);
            database.hostsSourceDao().updateSkippedCount(SOURCE_A, count);
            database.hostEntryDao().sync();
            return true;
        }

        @Override
        public void retrieveHostsSource(int sourceId) throws HostErrorException {
            targetedUpdates.incrementAndGet();
            super.retrieveHostsSource(sourceId);
        }
    }

    private static final class ControlledAdBlockModel extends AdBlockModel {
        private final AtomicInteger applyCount = new AtomicInteger();
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch completed = new CountDownLatch(1);
        private volatile CountDownLatch release = new CountDownLatch(0);

        private ControlledAdBlockModel(Context context) {
            super(context);
        }

        private void blockApply() {
            release = new CountDownLatch(1);
        }

        private void releaseApply() {
            release.countDown();
        }

        @Override
        public AdBlockMethod getMethod() {
            return ROOT;
        }

        @Override
        public void apply() throws HostErrorException {
            applyCount.incrementAndGet();
            entered.countDown();
            try {
                if (!release.await(30, TimeUnit.SECONDS)) {
                    throw new HostErrorException(HostError.COPY_FAIL);
                }
                applied.postValue(true);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new HostErrorException(HostError.COPY_FAIL);
            } finally {
                completed.countDown();
            }
        }

        @Override public void revert() { applied.postValue(false); }
        @Override public boolean isRecordingLogs() { return false; }
        @Override public void setRecordingLogs(boolean recording) { }
        @Override public List<String> getLogs() { return Collections.emptyList(); }
        @Override public void clearLogs() { }
    }
}
