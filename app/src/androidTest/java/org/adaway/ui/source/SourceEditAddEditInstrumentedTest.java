package org.adaway.ui.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.db.entity.RuleKind;
import org.adaway.model.source.SourceModel;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.home.HomeActivity;
import org.adaway.util.AppExecutors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Optional;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

@RunWith(AndroidJUnit4.class)
public class SourceEditAddEditInstrumentedTest {
    private static final int TIMEOUT_MS = 10_000;
    private static final String ADD_LABEL = "Custom add proof source";
    private static final String ADD_URL = "https://example.com/adaway-add-proof.txt";
    private static final String EDIT_LABEL = "Custom edited proof source";
    private static final String EDIT_URL = "https://example.com/adaway-edited-proof.txt";
    private static final String INVALID_URL = "http://example.com/not-secure.txt";
    private static final int DELETE_SOURCE_ID = 2210;
    private static final String DELETE_LABEL = "Custom delete proof source";
    private static final String DELETE_URL = "https://example.com/adaway-delete-proof.txt";

    private Context context;
    private SourceModel sourceModel;
    private OkHttpClient previousHttpClient;

    @Before
    public void setUp() throws Exception {
        this.context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up source add edit");
        AppDatabase.getInstance(this.context).getOpenHelper().getWritableDatabase();
        waitForDiskIoIdle();
        resetSources(this.context);
        this.sourceModel = ((AdAwayApplication) this.context).getSourceModel();
        Field client = SourceModel.class.getDeclaredField("cachedHttpClient");
        client.setAccessible(true);
        this.previousHttpClient = (OkHttpClient) client.get(this.sourceModel);
        client.set(this.sourceModel, new OkHttpClient.Builder().addInterceptor(chain ->
                new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                        .code(200).message("OK")
                        .body(ResponseBody.create("0.0.0.0 ads.url-import.example\n",
                                MediaType.get("text/plain")))
                        .build()).build());
    }

    @After
    public void tearDown() throws Exception {
        finishResumedActivities();
        if (this.context != null) {
            InstrumentedTestState.resetForPassiveRootUi(this.context, "tear down source add edit");
            resetSources(this.context);
        }
        if (this.sourceModel != null) {
            Field client = SourceModel.class.getDeclaredField("cachedHttpClient");
            client.setAccessible(true);
            client.set(this.sourceModel, this.previousHttpClient);
        }
    }

    @Test(timeout = 120_000)
    public void busyUpdateRejectsSaveAndDeleteThenDeleteClearsRuntime() throws Exception {
        seedDownloadedSource(this.context);
        runDatabaseWork(this.context, database -> this.sourceModel.syncHostEntries());
        AppDatabase database = AppDatabase.getInstance(this.context);
        assertNotNull(database.hostEntryDao().getEntry("ads.delete-proof.example"));
        Intent intent = new Intent(this.context, SourceEditActivity.class)
                .putExtra(SourceEditActivity.SOURCE_ID, DELETE_SOURCE_ID);
        CountDownLatch gateHeld = new CountDownLatch(1);
        CountDownLatch releaseGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> holder = null;
        try (ActivityScenario<SourceEditActivity> ignored = ActivityScenario.launch(intent)) {
            SourceEditActivity activity = waitForActivity(SourceEditActivity.class);
            waitForFieldText(activity, R.id.labelEditText, DELETE_LABEL);
            holder = executor.submit(() -> this.sourceModel.runSourceMutation(() -> {
                gateHeld.countDown();
                try {
                    if (!releaseGate.await(60, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for mutation test");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return null;
            }));
            assertTrue(gateHeld.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

            saveSource(activity, EDIT_LABEL, EDIT_URL);
            waitForDiskIoIdle();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertFieldError(activity, R.id.location_edit_text,
                    this.context.getString(R.string.error_update_in_progress_message));
            assertEquals(DELETE_LABEL, waitForSource(DELETE_URL).getLabel());
            assertFalse(database.hostsSourceDao().getByUrl(EDIT_URL).isPresent());
            assertEquals(1, countListRowsForSource(DELETE_SOURCE_ID));

            clickAccessibilityText(this.context.getString(R.string.checkbox_list_context_delete));
            clickAccessibilityText(this.context.getString(R.string.source_edit_delete_confirm_action));
            waitForDiskIoIdle();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertFieldError(activity, R.id.location_edit_text,
                    this.context.getString(R.string.error_update_in_progress_message));
            assertEquals(DELETE_SOURCE_ID, waitForSource(DELETE_URL).getId());
            assertNotNull(database.hostEntryDao().getEntry("ads.delete-proof.example"));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    assertFalse(activity.isFinishing()));

            releaseGate.countDown();
            holder.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            clickAccessibilityText(this.context.getString(R.string.checkbox_list_context_delete));
            clickAccessibilityText(this.context.getString(R.string.source_edit_delete_confirm_action));
            waitForSourceMissing(DELETE_URL);
            waitForDiskIoIdle();
            assertNull(database.hostEntryDao().getEntry("ads.delete-proof.example"));
            assertEquals(ListType.ALLOWED,
                    database.hostEntryDao().resolveEntry("ads.delete-proof.example").getType());
            assertSourceRootStageEmpty();
        } finally {
            releaseGate.countDown();
            executor.shutdownNow();
            executor.awaitTermination(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }

    @Test(timeout = 120_000)
    public void urlEditClearsOldRuntimeBeforeReplacementDownloadCompletes() throws Exception {
        seedDownloadedSource(this.context);
        runDatabaseWork(this.context, database -> this.sourceModel.syncHostEntries());
        AppDatabase database = AppDatabase.getInstance(this.context);
        assertNotNull(database.hostEntryDao().getEntry("ads.delete-proof.example"));
        CountDownLatch requested = new CountDownLatch(1);
        CountDownLatch respond = new CountDownLatch(1);
        Field field = SourceModel.class.getDeclaredField("cachedHttpClient");
        field.setAccessible(true);
        Object originalClient = field.get(this.sourceModel);
        field.set(this.sourceModel, new OkHttpClient.Builder().addInterceptor(chain -> {
            requested.countDown();
            try {
                if (!respond.await(30, TimeUnit.SECONDS)) {
                    throw new java.io.IOException("Timed out waiting for download release");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new java.io.IOException(e);
            }
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(503).message("Unavailable")
                    .body(ResponseBody.create("", MediaType.get("text/plain"))).build();
        }).build());
        Intent intent = new Intent(this.context, SourceEditActivity.class)
                .putExtra(SourceEditActivity.SOURCE_ID, DELETE_SOURCE_ID);
        try (ActivityScenario<SourceEditActivity> ignored = ActivityScenario.launch(intent)) {
            SourceEditActivity activity = waitForActivity(SourceEditActivity.class);
            waitForFieldText(activity, R.id.labelEditText, DELETE_LABEL);
            saveSource(activity, EDIT_LABEL, EDIT_URL);
            assertEquals(DELETE_SOURCE_ID, waitForSource(EDIT_URL).getId());
            waitForDiskIoIdle();
            assertTrue(requested.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            assertEquals(0, countListRowsForSource(DELETE_SOURCE_ID));
            assertNull(database.hostEntryDao().getEntry("ads.delete-proof.example"));
            assertEquals(ListType.ALLOWED,
                    database.hostEntryDao().resolveEntry("ads.delete-proof.example").getType());
            assertSourceRootStageEmpty();
        } finally {
            respond.countDown();
            InstrumentedTestState.resetForPassiveRootUi(this.context, "finish URL edit download");
            field.set(this.sourceModel, originalClient);
        }
    }

    private void assertSourceRootStageEmpty() {
        try (Cursor cursor = AppDatabase.getInstance(this.context).getOpenHelper()
                .getReadableDatabase().query(
                        "SELECT COUNT(*) FROM root_host_entries_stage WHERE source_id = ?",
                        new Object[]{DELETE_SOURCE_ID})) {
            assertTrue(cursor.moveToFirst());
            assertEquals(0, cursor.getInt(0));
        }
    }

    @Test(timeout = 120_000)
    public void customSourceAddRejectsInvalidUrlThenEditPersistsMetadata() throws Exception {
        ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class);
        navigateToSources(scenario);
        HomeActivity homeActivity = waitForHomeActivity();

        clickViewById(homeActivity, R.id.hosts_sources_add);
        clickAccessibilityText(this.context.getString(R.string.filter_add_custom));

        SourceEditActivity addActivity = waitForActivity(SourceEditActivity.class);
        saveSource(addActivity, ADD_LABEL, INVALID_URL);
        waitForSourceMissing(INVALID_URL);
        assertFieldError(addActivity, R.id.location_edit_text,
                this.context.getString(R.string.source_edit_location_invalid));

        saveSource(addActivity, ADD_LABEL, ADD_URL);
        HostsSource added = waitForSource(ADD_URL);
        assertEquals(ADD_LABEL, added.getLabel());
        assertTrue(added.isEnabled());
        assertFalse(added.isAllowEnabled());
        assertFalse(added.isRedirectEnabled());

        homeActivity = waitForHomeActivity();
        waitForSourceCard(homeActivity, ADD_LABEL);
        clickSourceCard(homeActivity, ADD_LABEL);

        SourceEditActivity editActivity = waitForActivity(SourceEditActivity.class);
        assertFieldText(editActivity, R.id.labelEditText, ADD_LABEL);
        assertFieldText(editActivity, R.id.location_edit_text, ADD_URL);
        clickViewById(editActivity, R.id.allow_format_button);

        saveSource(editActivity, EDIT_LABEL, EDIT_URL);
        HostsSource edited = waitForSource(EDIT_URL);
        assertEquals(EDIT_LABEL, edited.getLabel());
        assertTrue(edited.isEnabled());
        assertTrue(edited.isAllowEnabled());
        assertFalse(edited.isRedirectEnabled());
        waitForSourceMissing(ADD_URL);
    }

    @Test(timeout = 120_000)
    public void renamingDisabledSourcePreservesIdentityAndDownloadedRules() throws Exception {
        seedDownloadedSource(this.context);
        runDatabaseWork(this.context, database -> {
            database.hostsSourceDao().applySourceSelection(DELETE_SOURCE_ID, false);
            database.hostsSourceDao().updateEntityTag(DELETE_SOURCE_ID, "saved-etag");
            database.hostsSourceDao().updateSize(DELETE_SOURCE_ID);
        });
        Intent intent = new Intent(this.context, SourceEditActivity.class)
                .putExtra(SourceEditActivity.SOURCE_ID, DELETE_SOURCE_ID);
        try (ActivityScenario<SourceEditActivity> ignored = ActivityScenario.launch(intent)) {
            SourceEditActivity activity = waitForActivity(SourceEditActivity.class);
            waitForFieldText(activity, R.id.labelEditText, DELETE_LABEL);
            saveSource(activity, EDIT_LABEL, DELETE_URL);
            waitForSourceLabel(DELETE_URL, EDIT_LABEL);
            HostsSource renamed = waitForSource(DELETE_URL);
            assertEquals(DELETE_SOURCE_ID, renamed.getId());
            assertFalse(renamed.isEnabled());
            assertEquals("saved-etag", renamed.getEntityTag());
            assertEquals(1, renamed.getSize());
            assertEquals(1, countListRowsForSource(DELETE_SOURCE_ID));
        }
    }

    @Test(timeout = 120_000)
    public void duplicateUrlEditLeavesBothSourcesAndRulesIntact() throws Exception {
        seedDownloadedSource(this.context);
        runDatabaseWork(this.context, database -> {
            HostsSource other = new HostsSource();
            other.setLabel(ADD_LABEL);
            other.setUrl(ADD_URL);
            database.hostsSourceDao().insert(other);
        });
        Intent intent = new Intent(this.context, SourceEditActivity.class)
                .putExtra(SourceEditActivity.SOURCE_ID, DELETE_SOURCE_ID);
        try (ActivityScenario<SourceEditActivity> ignored = ActivityScenario.launch(intent)) {
            SourceEditActivity activity = waitForActivity(SourceEditActivity.class);
            waitForFieldText(activity, R.id.labelEditText, DELETE_LABEL);
            saveSource(activity, EDIT_LABEL, ADD_URL);
            waitForDiskIoIdle();
            assertEquals(DELETE_SOURCE_ID, waitForSource(DELETE_URL).getId());
            assertEquals(ADD_LABEL, waitForSource(ADD_URL).getLabel());
            assertEquals(1, countListRowsForSource(DELETE_SOURCE_ID));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                TextInputEditText field = activity.findViewById(R.id.location_edit_text);
                assertNotNull("Duplicate URL must be explained in the editor", field.getError());
                assertFalse(activity.isFinishing());
            });
        }
    }

    @Test(timeout = 120_000)
    public void pastedUrlCanBeSavedWithoutChoosingALabel() throws Exception {
        try (ActivityScenario<SourceEditActivity> ignored =
                     ActivityScenario.launch(SourceEditActivity.class)) {
            SourceEditActivity activity = waitForActivity(SourceEditActivity.class);
            assertFieldText(activity, R.id.location_edit_text, "");
            saveSource(activity, "", "  " + ADD_URL + "\n");
            HostsSource source = waitForSource(ADD_URL);
            assertEquals("example.com", source.getLabel());
            assertTrue(source.isEnabled());
            long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
            while (SystemClock.uptimeMillis() < deadline && AppDatabase.getInstance(this.context)
                    .hostEntryDao().getEntry("ads.url-import.example") == null) {
                SystemClock.sleep(50);
            }
            assertNotNull("Saving a new URL must download and publish its rules",
                    AppDatabase.getInstance(this.context).hostEntryDao()
                            .getEntry("ads.url-import.example"));
        }
    }

    @Test(timeout = 120_000)
    public void deleteSourceRequiresConfirmationAndRemovesDownloadedRules() throws Exception {
        seedDownloadedSource(this.context);

        ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class);
        navigateToSources(scenario);
        HomeActivity homeActivity = waitForHomeActivity();
        waitForSourceCard(homeActivity, DELETE_LABEL);
        clickSourceCard(homeActivity, DELETE_LABEL);

        SourceEditActivity editActivity = waitForActivity(SourceEditActivity.class);
        assertFieldText(editActivity, R.id.labelEditText, DELETE_LABEL);
        assertFieldText(editActivity, R.id.location_edit_text, DELETE_URL);
        assertEquals(1, countListRowsForSource(DELETE_SOURCE_ID));

        clickAccessibilityText(this.context.getString(R.string.checkbox_list_context_delete));
        assertAccessibilityText(this.context.getString(R.string.source_edit_delete_confirm_title));
        assertAccessibilityText(this.context.getString(
                R.string.source_edit_delete_confirm_message, DELETE_LABEL));
        clickAccessibilityText(this.context.getString(R.string.button_cancel));
        waitForSource(DELETE_URL);
        assertEquals(1, countListRowsForSource(DELETE_SOURCE_ID));

        clickAccessibilityText(this.context.getString(R.string.checkbox_list_context_delete));
        clickAccessibilityText(this.context.getString(
                R.string.source_edit_delete_confirm_action));

        waitForSourceMissing(DELETE_URL);
        waitForListRowsMissing(DELETE_SOURCE_ID);
        homeActivity = waitForHomeActivity();
        waitForSourceCardMissing(homeActivity, DELETE_LABEL);
    }

    private static void navigateToSources(ActivityScenario<HomeActivity> scenario) {
        scenario.onActivity(activity -> activity.navigateTo(R.id.nav_sources));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static HomeActivity waitForHomeActivity() {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<Activity> resumed = new AtomicReference<>();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)) {
                    if (activity instanceof HomeActivity) {
                        resumed.set(activity);
                        return;
                    }
                }
            });
            Activity activity = resumed.get();
            if (activity instanceof HomeActivity) {
                return (HomeActivity) activity;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Timed out waiting for HomeActivity.");
    }

    private static void saveSource(
            SourceEditActivity activity,
            String label,
            String url) throws Exception {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            TextInputEditText labelField = activity.findViewById(R.id.labelEditText);
            TextInputEditText locationField = activity.findViewById(R.id.location_edit_text);
            assertNotNull(labelField);
            assertNotNull(locationField);
            labelField.setText(label);
            locationField.setText(url);
        });
        clickAccessibilityText(ApplicationProvider.getApplicationContext()
                .getString(R.string.checkbox_list_context_apply));
    }

    private static void assertFieldText(Activity activity, int viewId, String expectedText) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            TextView field = activity.findViewById(viewId);
            assertNotNull(field);
            assertEquals(expectedText, field.getText().toString());
        });
    }

    private static void waitForFieldText(Activity activity, int viewId, String text) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<String> value = new AtomicReference<>();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    value.set(((TextView) activity.findViewById(viewId)).getText().toString()));
            if (text.equals(value.get())) {
                return;
            }
            SystemClock.sleep(50);
        }
        throw new AssertionError("Editor did not load " + text);
    }

    private static void waitForSourceLabel(String url, String label) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (label.equals(waitForSource(url).getLabel())) {
                return;
            }
            SystemClock.sleep(50);
        }
        throw new AssertionError("Source label was not saved");
    }

    private static void assertFieldError(Activity activity, int viewId, String expectedError) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            TextInputEditText field = activity.findViewById(viewId);
            assertNotNull(field);
            assertEquals(expectedError, String.valueOf(field.getError()));
        });
    }

    private static void clickViewById(Activity activity, int viewId) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<View> viewRef = new AtomicReference<>();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                View view = findViewById(activity.getWindow().getDecorView(), viewId);
                if (view != null && view.isShown()) {
                    viewRef.set(view);
                }
            });
            View view = viewRef.get();
            if (view != null) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(view::performClick);
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Timed out waiting to click view " + viewId);
    }

    private static void waitForSourceCard(Activity activity, String label) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<MaterialCardView> cardRef = new AtomicReference<>();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    cardRef.set(findSourceCardByLabel(
                            activity.getWindow().getDecorView(), label)));
            if (cardRef.get() != null) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Timed out waiting for source card: " + label);
    }

    private static void waitForSourceCardMissing(Activity activity, String label) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<MaterialCardView> cardRef = new AtomicReference<>();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    cardRef.set(findSourceCardByLabel(
                            activity.getWindow().getDecorView(), label)));
            if (cardRef.get() == null) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Source card remained visible: " + label);
    }

    private static void clickSourceCard(Activity activity, String label) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            MaterialCardView card = findSourceCardByLabel(
                    activity.getWindow().getDecorView(), label);
            assertNotNull("Expected source card for " + label, card);
            assertTrue("Expected source card click to be handled.", card.performClick());
        });
    }

    private static void clickAccessibilityText(String expectedText) throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .getRootInActiveWindow();
            try {
                AccessibilityNodeInfo node = root == null ? null : findText(root, expectedText);
                if (node != null) {
                    try {
                        if (clickNodeOrParent(node)) {
                            return;
                        }
                    } finally {
                        node.recycle();
                    }
                }
            } finally {
                if (root != null) {
                    root.recycle();
                }
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Text was not clickable: " + expectedText);
    }

    private static void assertAccessibilityText(String expectedText) throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .getRootInActiveWindow();
            try {
                AccessibilityNodeInfo node = root == null ? null : findText(root, expectedText);
                if (node != null) {
                    node.recycle();
                    return;
                }
            } finally {
                if (root != null) {
                    root.recycle();
                }
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Text was not visible: " + expectedText);
    }

    private static AccessibilityNodeInfo findText(
            AccessibilityNodeInfo node,
            String expectedText) {
        if (nodeTextMatches(node, expectedText)) {
            return AccessibilityNodeInfo.obtain(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            try {
                AccessibilityNodeInfo result = findText(child, expectedText);
                if (result != null) {
                    return result;
                }
            } finally {
                child.recycle();
            }
        }
        return null;
    }

    private static boolean nodeTextMatches(AccessibilityNodeInfo node, String expectedText) {
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        return (text != null && expectedText.contentEquals(text))
                || (description != null && expectedText.contentEquals(description));
    }

    private static boolean clickNodeOrParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = AccessibilityNodeInfo.obtain(node);
        try {
            while (current != null) {
                if (current.isClickable()
                        && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true;
                }
                AccessibilityNodeInfo parent = current.getParent();
                current.recycle();
                current = parent;
            }
            return false;
        } finally {
            if (current != null) {
                current.recycle();
            }
        }
    }

    private static View findViewById(View view, int viewId) {
        if (view.getId() == viewId) {
            return view;
        }
        if (!(view instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View match = findViewById(group.getChildAt(i), viewId);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static MaterialCardView findSourceCardByLabel(View view, String label) {
        if (view instanceof TextView
                && view.getId() == R.id.sourceLabel
                && view.isShown()
                && label.contentEquals(((TextView) view).getText())) {
            ViewParent parent = view.getParent();
            while (parent instanceof View) {
                View parentView = (View) parent;
                if (parentView.getId() == R.id.sourceCard
                        && parentView instanceof MaterialCardView) {
                    return (MaterialCardView) parentView;
                }
                parent = parentView.getParent();
            }
        }
        if (!(view instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            MaterialCardView match = findSourceCardByLabel(group.getChildAt(i), label);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static SourceEditActivity waitForActivity(
            Class<SourceEditActivity> activityClass) throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<Activity> resumed = new AtomicReference<>();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)) {
                    if (activityClass.isInstance(activity)) {
                        resumed.set(activity);
                        return;
                    }
                }
            });
            Activity activity = resumed.get();
            if (activityClass.isInstance(activity)) {
                return activityClass.cast(activity);
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Timed out waiting for " + activityClass.getSimpleName());
    }

    private static HostsSource waitForSource(String url) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            Optional<HostsSource> source = AppDatabase.getInstance(
                    ApplicationProvider.getApplicationContext())
                    .hostsSourceDao()
                    .getByUrl(url);
            if (source.isPresent()) {
                return source.get();
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Timed out waiting for source: " + url);
    }

    private static void waitForSourceMissing(String url) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            Optional<HostsSource> source = AppDatabase.getInstance(
                    ApplicationProvider.getApplicationContext())
                    .hostsSourceDao()
                    .getByUrl(url);
            if (source.isEmpty()) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Source remained present: " + url);
    }

    private static void waitForListRowsMissing(int sourceId) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (countListRowsForSource(sourceId) == 0) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Downloaded rules remained for source: " + sourceId);
    }

    private static int countListRowsForSource(int sourceId) {
        try (Cursor cursor = AppDatabase.getInstance(ApplicationProvider.getApplicationContext())
                .getOpenHelper()
                .getReadableDatabase()
                .query("SELECT COUNT(*) FROM hosts_lists WHERE source_id = ?",
                        new Object[]{sourceId})) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    private static void seedDownloadedSource(Context context) {
        runDatabaseWork(context, database -> {
            HostsSource source = new HostsSource();
            source.setId(DELETE_SOURCE_ID);
            source.setLabel(DELETE_LABEL);
            source.setUrl(DELETE_URL);
            source.setEnabled(true);
            database.hostsSourceDao().insert(source);

            HostListItem item = new HostListItem();
            item.setHost("ads.delete-proof.example");
            item.setType(ListType.BLOCKED);
            item.setKind(RuleKind.EXACT);
            item.setEnabled(true);
            item.setSourceId(DELETE_SOURCE_ID);
            item.setGeneration(0);
            database.hostsListItemDao().insert(item);
        });
    }

    private static void resetSources(Context context) {
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

    private static void finishResumedActivities() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)) {
                activity.finish();
            }
        });
    }

    private interface DatabaseWork {
        void run(AppDatabase database);
    }
}
