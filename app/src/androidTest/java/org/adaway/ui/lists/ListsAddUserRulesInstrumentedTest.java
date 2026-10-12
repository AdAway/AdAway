/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */
package org.adaway.ui.lists;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.db.entity.HostsSource.USER_SOURCE_URL;
import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;
import static org.adaway.ui.lists.ListsActivity.ALLOWED_HOSTS_TAB;
import static org.adaway.ui.lists.ListsActivity.BLOCKED_HOSTS_TAB;
import static org.adaway.ui.lists.ListsActivity.REDIRECTED_HOSTS_TAB;
import static org.adaway.ui.lists.ListsActivity.TAB;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleCallback;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.viewpager2.widget.ViewPager2;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.testing.InstrumentedTestState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
public class ListsAddUserRulesInstrumentedTest {
    private static final String TEST_DATABASE_NAME = "lists-add-user-rules-test.db";
    private static final String BLOCKED_HOST = "add-blocked-ui.invalid";
    private static final String ALLOWED_HOST = "*.add-allowed-ui.invalid";
    private static final String REDIRECTED_HOST = "add-redirected-ui.invalid";
    private static final String REDIRECT_IP = "8.8.8.8";
    private static final long TIMEOUT_MS = 10_000L;

    private Context context;
    private AppDatabase database;
    private AppDatabase previousDatabase;
    private boolean databaseSingletonSwapped;

    @Before
    public void setUp() throws Exception {
        this.context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up add user rules");
        this.context.deleteDatabase(TEST_DATABASE_NAME);
        this.database = Room.databaseBuilder(this.context, AppDatabase.class, TEST_DATABASE_NAME)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .allowMainThreadQueries()
                .build();
        initializeTestDatabase(this.database.getOpenHelper().getWritableDatabase());
        this.previousDatabase = swapAppDatabaseSingleton(this.database);
        this.databaseSingletonSwapped = true;
        insertUserSource(this.database.hostsSourceDao());
    }

    @After
    public void tearDown() throws Exception {
        try {
            finishResumedActivities();
            if (this.context != null) {
                InstrumentedTestState.resetForPassiveRootUi(this.context, "tear down add user rules");
            }
        } finally {
            if (this.databaseSingletonSwapped) {
                swapAppDatabaseSingleton(this.previousDatabase);
                this.databaseSingletonSwapped = false;
            }
            if (this.database != null) {
                this.database.close();
            }
            if (this.context != null) {
                this.context.deleteDatabase(TEST_DATABASE_NAME);
            }
        }
    }

    @Test(timeout = 90_000)
    public void addDialogsUseRestoredFragmentsAfterActivityRecreation() throws Exception {
        ActivityScenario<ListsActivity> scenario = ActivityScenario.launch(
                new Intent(this.context, ListsActivity.class).putExtra(TAB, BLOCKED_HOSTS_TAB));
        try {
            waitForTabFragment(BLOCKED_HOSTS_TAB);
            recreateResumedListsActivity();
            addRule(BLOCKED_HOSTS_TAB, R.string.list_add_dialog_black, BLOCKED_HOST);
            waitForUserRule(BLOCKED, BLOCKED_HOST, null);
            addRule(ALLOWED_HOSTS_TAB, R.string.list_add_dialog_white, ALLOWED_HOST);
            waitForUserRule(ALLOWED, ALLOWED_HOST, null);
            recreateResumedListsActivity();
            addRule(REDIRECTED_HOSTS_TAB, R.string.list_add_dialog_redirect,
                    REDIRECTED_HOST, REDIRECT_IP);
            waitForUserRule(REDIRECTED, REDIRECTED_HOST, REDIRECT_IP);
        } finally {
            // Remove the animated UI before ActivityScenario.close() waits for idleness.
            finishResumedActivities();
            scenario.close();
        }
    }

    private static void recreateResumedListsActivity() throws InterruptedException {
        ListsActivity[] previous = new ListsActivity[1];
        CountDownLatch resumed = new CountDownLatch(1);
        ActivityLifecycleCallback callback = (activity, stage) -> {
            if (activity instanceof ListsActivity && activity != previous[0]
                    && stage == Stage.RESUMED) {
                resumed.countDown();
            }
        };
        try {
            runOnResumedListsActivity(activity -> {
                previous[0] = activity;
                ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback);
                // ActivityScenario.recreate() first waits indefinitely for UI idleness.
                // Observe the real replacement lifecycle instead of waiting on animations.
                activity.recreate();
            });
            assertTrue("Replacement ListsActivity did not resume after recreation",
                    resumed.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    ActivityLifecycleMonitorRegistry.getInstance()
                            .removeLifecycleCallback(callback));
        }
    }

    @Test(timeout = 90_000)
    public void addDialogsStoreBlockedAllowedAndRedirectedUserRules()
            throws Exception {
        ActivityScenario.launch(
                new Intent(this.context, ListsActivity.class).putExtra(TAB, BLOCKED_HOSTS_TAB));

        addRule(
                BLOCKED_HOSTS_TAB,
                R.string.list_add_dialog_black,
                BLOCKED_HOST);
        waitForUserRule(BLOCKED, BLOCKED_HOST, null);

        addRule(
                ALLOWED_HOSTS_TAB,
                R.string.list_add_dialog_white,
                ALLOWED_HOST);
        waitForUserRule(ALLOWED, ALLOWED_HOST, null);

        addRule(
                REDIRECTED_HOSTS_TAB,
                R.string.list_add_dialog_redirect,
                REDIRECTED_HOST,
                REDIRECT_IP);
        waitForUserRule(REDIRECTED, REDIRECTED_HOST, REDIRECT_IP);
    }

    private void addRule(
            int tab,
            int dialogTitleRes,
            String... values)
            throws Exception {
        runOnResumedListsActivity(activity -> {
            ViewPager2 viewPager = activity.findViewById(R.id.lists_view_pager);
            viewPager.setCurrentItem(tab, false);
        });
        waitForTabFragment(tab);
        waitForAccessibilityText(this.context.getString(R.string.lists_add));
        runOnResumedListsActivity(activity -> activity.findViewById(R.id.lists_add).performClick());
        waitForAccessibilityText(this.context.getString(dialogTitleRes));
        setEditTextValues(values);
        clickAccessibilityText(this.context.getString(R.string.button_add));
    }

    private static void waitForTabFragment(int tab) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            final boolean[] ready = new boolean[1];
            runOnResumedListsActivity(activity -> {
                ViewPager2 viewPager = activity.findViewById(R.id.lists_view_pager);
                Fragment fragment = activity.getSupportFragmentManager()
                        .findFragmentByTag("f" + tab);
                ready[0] = viewPager.getCurrentItem() == tab
                        && fragment != null
                        && fragment.getView() != null;
            });
            if (ready[0]) {
                return;
            }
            SystemClock.sleep(100L);
        }
        throw new AssertionError("Tab fragment was not ready: " + tab);
    }

    private void waitForUserRule(
            @NonNull ListType type,
            @NonNull String host,
            @Nullable String redirection) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (countUserRule(type, host, redirection) == 1) {
                return;
            }
            SystemClock.sleep(100L);
        }
        assertEquals("Expected one user rule for " + host, 1,
                countUserRule(type, host, redirection));
    }

    private int countUserRule(
            @NonNull ListType type,
            @NonNull String host,
            @Nullable String redirection) {
        String redirectionClause = redirection == null
                ? "redirection IS NULL"
                : "redirection = ?";
        List<Object> args = new ArrayList<>();
        args.add(type.getValue());
        args.add(host);
        if (redirection != null) {
            args.add(redirection);
        }
        try (Cursor cursor = this.database.getOpenHelper().getReadableDatabase().query(
                new SimpleSQLiteQuery("SELECT COUNT(*) FROM hosts_lists "
                        + "WHERE type = ? AND host = ? AND enabled = 1 "
                        + "AND source_id = " + USER_SOURCE_ID + " AND " + redirectionClause,
                        args.toArray()))) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
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

    private static void insertUserSource(@NonNull HostsSourceDao hostsSourceDao) {
        HostsSource source = new HostsSource();
        source.setId(USER_SOURCE_ID);
        source.setLabel("User rules");
        source.setUrl(USER_SOURCE_URL);
        source.setAllowEnabled(true);
        source.setRedirectEnabled(true);
        hostsSourceDao.insert(source);
    }

    private static void initializeTestDatabase(@NonNull SupportSQLiteDatabase writableDatabase) {
        AppDatabase.optimizeCreatedDatabaseStorage(writableDatabase);
        writableDatabase.execSQL("INSERT OR IGNORE INTO hosts_meta "
                + "(id, active_generation) VALUES (0, 0)");
        writableDatabase.execSQL("INSERT OR IGNORE INTO hosts_stats "
                + "(id, blocked_count, blocked_exact_count, allowed_count, redirected_count, "
                + "active_rule_count, root_export_materialized, root_export_stage_materialized) "
                + "VALUES (0, 0, 0, 0, 0, 0, 0, 0)");
    }

    private static AppDatabase swapAppDatabaseSingleton(@Nullable AppDatabase database)
            throws Exception {
        Field field = AppDatabase.class.getDeclaredField("instance");
        field.setAccessible(true);
        AppDatabase previous = (AppDatabase) field.get(null);
        field.set(null, database);
        return previous;
    }

    private static void runOnResumedListsActivity(@NonNull Consumer<ListsActivity> action) {
        final boolean[] handled = new boolean[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)) {
                if (activity instanceof ListsActivity) {
                    action.accept((ListsActivity) activity);
                    handled[0] = true;
                    return;
                }
            }
        });
        if (!handled[0]) {
            throw new AssertionError("No resumed ListsActivity");
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
}
