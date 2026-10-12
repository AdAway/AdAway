package org.adaway.ui.log;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;
import static org.adaway.db.entity.RuleKind.EXACT;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostEntryDao;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostEntry;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.vpn.VpnModel;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.util.AppExecutors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class LogRuntimeTruthTest {
    private static final int TIMEOUT_MS = 10_000;
    private static final int TEST_SOURCE_ID = 949494;
    private static final String LOG_HOST = "vpn-log-runtime-ci.invalid";
    private static final String REDIRECT_IP = "93.184.216.34";

    private Context context;
    private AdAwayApplication application;
    private AppDatabase database;
    private HostListItemDao hostListItemDao;
    private HostEntryDao hostEntryDao;
    private HostsSourceDao hostsSourceDao;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(context, "set up Log runtime truth");
        waitForDiskIoIdle();
        application = (AdAwayApplication) context.getApplicationContext();
        database = AppDatabase.getInstance(application);
        hostListItemDao = database.hostsListItemDao();
        hostEntryDao = database.hostEntryDao();
        hostsSourceDao = database.hostsSourceDao();
        cleanup();
        insertSource();
        insertBlockedHost();
        hostsSourceDao.updateActiveRuleStats(TEST_SOURCE_ID);
        hostEntryDao.sync();
        application.invalidateVpnRulesCache();
        PreferenceHelper.setAbBlockMethod(context, VPN);
    }

    @After
    public void tearDown() {
        finishResumedActivities();
        if (application != null) {
            AdBlockModel model = application.getAdBlockModel();
            model.setRecordingLogs(false);
            model.clearLogs();
        }
        cleanup();
        if (hostEntryDao != null) {
            hostEntryDao.sync();
        }
        if (context != null) {
            InstrumentedTestState.resetForPassiveRootUi(context, "tear down Log runtime truth");
        }
    }

    @Test(timeout = 120_000)
    public void changingExactUserRuleReplacesAllowAndPreservesSuffixRule() {
        HostListItem suffix = new HostListItem();
        suffix.setHost(LOG_HOST);
        suffix.setSourceId(USER_SOURCE_ID);
        suffix.setKind(org.adaway.db.entity.RuleKind.SUFFIX);
        suffix.setType(BLOCKED);
        suffix.setEnabled(true);
        hostListItemDao.insert(suffix);
        AtomicReference<LogViewModel> model = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            model.set(new LogViewModel(application));
            model.get().addListItem(LOG_HOST, org.adaway.db.entity.ListType.ALLOWED, null);
        });
        waitForDiskIoIdle();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                model.get().addListItem(LOG_HOST, BLOCKED, null));
        waitForDiskIoIdle();
        List<HostListItem> rules = hostListItemDao.getUserList();
        assertEquals(1, rules.stream().filter(row -> row.getHost().equals(LOG_HOST)
                && row.getKind() == EXACT).count());
        assertEquals(1, rules.stream().filter(row -> row.getHost().equals(LOG_HOST)
                && row.getKind() == org.adaway.db.entity.RuleKind.SUFFIX).count());
        hostEntryDao.sync();
        assertEquals(BLOCKED, hostEntryDao.getTypeForHost(LOG_HOST));
    }

    @Test(timeout = 120_000)
    public void vpnGeneratedDnsLogAppearsInLogUi() {
        recordFixtureDnsRequest();

        try (ActivityScenario<LogActivity> scenario = ActivityScenario.launch(LogActivity.class)) {
            LogActivity activity = waitForActivity(LogActivity.class);
            waitForVisibleText(activity, LOG_HOST);
        }
    }

    @Test(timeout = 120_000)
    public void redirectActionStoresRedirectedUserRuleFromLog() throws Exception {
        recordFixtureDnsRequest();

        ActivityScenario.launch(LogActivity.class);
        LogActivity activity = waitForActivity(LogActivity.class);
        waitForVisibleText(activity, LOG_HOST);

        clickRedirectActionForHost(activity, LOG_HOST);
        waitForAccessibilityText(context.getString(R.string.log_redirect_dialog_title));
        setEditTextValues(REDIRECT_IP);
        clickAccessibilityText(context.getString(R.string.button_add));

        waitForRedirectedUserRule();
        finishResumedActivities();
    }

    private void recordFixtureDnsRequest() {
        AdBlockModel model = application.getAdBlockModel();
        assertTrue("Expected VPN model after selecting VPN mode.", model instanceof VpnModel);
        model.clearLogs();
        model.setRecordingLogs(true);

        HostEntry entry = ((VpnModel) model).getEntry(LOG_HOST);
        assertEquals("Fixture host must be blocked by runtime truth before checking Log UI.",
                BLOCKED, entry.getType());
        model.setRecordingLogs(false);
        assertTrue("VPN model must record generated DNS request.",
                model.getLogs().contains(LOG_HOST));
    }

    private void insertSource() {
        HostsSource source = new HostsSource();
        source.setId(TEST_SOURCE_ID);
        source.setLabel("Log runtime truth test");
        source.setUrl("https://example.invalid/log-runtime-truth.txt");
        source.setEnabled(true);
        hostsSourceDao.insert(source);
    }

    private void insertBlockedHost() {
        HostListItem item = new HostListItem();
        item.setHost(LOG_HOST);
        item.setType(BLOCKED);
        item.setKind(EXACT);
        item.setEnabled(true);
        item.setSourceId(TEST_SOURCE_ID);
        item.setGeneration(getActiveGeneration());
        hostListItemDao.insert(item);
    }

    private int getActiveGeneration() {
        try (Cursor cursor = database.getOpenHelper().getWritableDatabase().query(
                "SELECT active_generation FROM hosts_meta WHERE id = 0 LIMIT 1")) {
            if (!cursor.moveToFirst()) {
                return 0;
            }
            return cursor.getInt(0);
        }
    }

    private void cleanup() {
        if (database == null) {
            return;
        }
        database.getOpenHelper().getWritableDatabase().execSQL(
                "DELETE FROM hosts_lists WHERE host = ?",
                new Object[]{LOG_HOST});
        hostListItemDao.clearSourceHosts(TEST_SOURCE_ID);
        hostsSourceDao.getById(TEST_SOURCE_ID).ifPresent(hostsSourceDao::delete);
        hostEntryDao.invalidateMaterializedRuntimeCaches();
    }

    private void waitForRedirectedUserRule() {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (countRedirectedUserRules() == 1) {
                return;
            }
            waitForDiskIoIdle();
            SystemClock.sleep(100);
        }
        assertEquals("Expected one redirected user rule for " + LOG_HOST,
                1, countRedirectedUserRules());
    }

    private int countRedirectedUserRules() {
        try (Cursor cursor = database.getOpenHelper().getReadableDatabase().query(
                "SELECT COUNT(*) FROM hosts_lists WHERE host = ? AND type = ? "
                        + "AND enabled = 1 AND source_id = ? AND redirection = ?",
                new Object[]{
                        LOG_HOST,
                        REDIRECTED.getValue(),
                        USER_SOURCE_ID,
                        REDIRECT_IP
                })) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    private static <T extends Activity> T waitForActivity(@NonNull Class<T> activityClass) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<T> resumed = new AtomicReference<>();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)) {
                    if (activityClass.isInstance(activity)) {
                        resumed.set(activityClass.cast(activity));
                        return;
                    }
                }
            });
            T activity = resumed.get();
            if (activity != null) {
                return activity;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Timed out waiting for " + activityClass.getSimpleName() + ".");
    }

    private static void clickRedirectActionForHost(LogActivity activity, String host) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<Boolean> clicked = new AtomicReference<>(false);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                RecyclerView recycler = activity.findViewById(R.id.log_list);
                if (recycler == null) {
                    return;
                }
                for (int i = 0; i < recycler.getChildCount(); i++) {
                    View child = recycler.getChildAt(i);
                    TextView hostname = child.findViewById(R.id.hostnameTextView);
                    View redirect = child.findViewById(R.id.redirectionImageView);
                    if (hostname != null
                            && redirect != null
                            && host.contentEquals(hostname.getText())) {
                        clicked.set(redirect.performClick());
                        return;
                    }
                }
            });
            if (Boolean.TRUE.equals(clicked.get())) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Could not click redirect action for " + host);
    }

    private static void waitForVisibleText(Activity activity, String expectedText) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<Boolean> found = new AtomicReference<>(false);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    found.set(hasVisibleText(activity.getWindow().getDecorView(), expectedText)));
            if (Boolean.TRUE.equals(found.get())) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Text did not appear: " + expectedText);
    }

    private static boolean hasVisibleText(@Nullable View view, String expectedText) {
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

    private static void waitForAccessibilityText(String expectedText) throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .getRootInActiveWindow();
            try {
                if (root != null && containsText(root, expectedText)) {
                    return;
                }
            } finally {
                if (root != null) {
                    root.recycle();
                }
            }
            SystemClock.sleep(100L);
        }
        throw new AssertionError("Text was not visible: " + expectedText);
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
            SystemClock.sleep(100L);
        }
        throw new AssertionError("Text was not clickable: " + expectedText);
    }

    private static void setEditTextValues(String... values) throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .getRootInActiveWindow();
            try {
                List<AccessibilityNodeInfo> editTexts = new ArrayList<>();
                if (root != null) {
                    collectEditTexts(root, editTexts);
                }
                if (editTexts.size() >= values.length) {
                    try {
                        for (int index = 0; index < values.length; index++) {
                            setNodeText(editTexts.get(index), values[index]);
                        }
                        return;
                    } finally {
                        for (AccessibilityNodeInfo editText : editTexts) {
                            editText.recycle();
                        }
                    }
                }
                for (AccessibilityNodeInfo editText : editTexts) {
                    editText.recycle();
                }
            } finally {
                if (root != null) {
                    root.recycle();
                }
            }
            SystemClock.sleep(100L);
        }
        throw new AssertionError("Expected at least " + values.length + " edit fields");
    }

    private static void setNodeText(@NonNull AccessibilityNodeInfo node, @NonNull String text) {
        Bundle arguments = new Bundle();
        arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text);
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
            throw new AssertionError("Could not set dialog field text");
        }
    }

    private static void collectEditTexts(
            @NonNull AccessibilityNodeInfo node,
            @NonNull List<AccessibilityNodeInfo> editTexts) {
        CharSequence className = node.getClassName();
        if (className != null && className.toString().endsWith("EditText")) {
            editTexts.add(AccessibilityNodeInfo.obtain(node));
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) {
                continue;
            }
            try {
                collectEditTexts(child, editTexts);
            } finally {
                child.recycle();
            }
        }
    }

    private static boolean containsText(AccessibilityNodeInfo node, String expectedText) {
        if (nodeTextContains(node, expectedText)) {
            return true;
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) {
                continue;
            }
            try {
                if (containsText(child, expectedText)) {
                    return true;
                }
            } finally {
                child.recycle();
            }
        }
        return false;
    }

    private static AccessibilityNodeInfo findText(
            AccessibilityNodeInfo node,
            String expectedText) {
        if (nodeTextMatches(node, expectedText)) {
            return AccessibilityNodeInfo.obtain(node);
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
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

    private static boolean nodeTextContains(AccessibilityNodeInfo node, String expectedText) {
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        return (text != null && text.toString().contains(expectedText))
                || (description != null && description.toString().contains(expectedText));
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

    private static void finishResumedActivities() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)) {
                activity.finish();
            }
        });
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
}
