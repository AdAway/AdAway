/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */
package org.adaway.ui.onboarding;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.db.entity.HostsSource.USER_SOURCE_URL;
import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.model.adblocking.AdBlockMethod.ROOT;
import static org.adaway.model.adblocking.AdBlockMethod.UNDEFINED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.source.FilterListCatalog;
import org.adaway.model.source.WaTgSafetyAllowlist;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.home.HomeActivity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class OnboardingFirstRunInstrumentedTest {
    private static final String TEST_DATABASE_NAME = "onboarding-first-run-test.db";
    private static final long TIMEOUT_MS = 10_000L;

    private Context context;
    private AppDatabase database;
    private AppDatabase previousDatabase;
    private boolean databaseSingletonSwapped;

    @Before
    public void setUp() throws Exception {
        this.context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up onboarding first run");
        this.context.deleteDatabase(TEST_DATABASE_NAME);
        this.database = Room.databaseBuilder(this.context, AppDatabase.class, TEST_DATABASE_NAME)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .allowMainThreadQueries()
                .build();
        initializeTestDatabase(this.database.getOpenHelper().getWritableDatabase());
        this.previousDatabase = swapAppDatabaseSingleton(this.database);
        this.databaseSingletonSwapped = true;
        insertUserSource();
    }

    @After
    public void tearDown() throws Exception {
        try {
            finishResumedActivities();
            if (this.context != null) {
                InstrumentedTestState.resetForPassiveRootUi(
                        this.context, "tear down onboarding first run");
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

    @Test(timeout = 60_000)
    public void recreationRetainsSelectionAndPendingVpnCompletion() throws Exception {
        Intent intent = new Intent(this.context, OnboardingActivity.class)
                .putExtra(OnboardingActivity.EXTRA_SKIP_AUTO_DETECT, true);
        Field selected = OnboardingActivity.class.getDeclaredField("selectedMethod");
        Field pending = OnboardingActivity.class.getDeclaredField("finishAfterVpnAuthorization");
        selected.setAccessible(true);
        pending.setAccessible(true);
        try (ActivityScenario<OnboardingActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                try {
                    selected.set(activity, org.adaway.model.adblocking.AdBlockMethod.VPN);
                    pending.setBoolean(activity, true);
                } catch (IllegalAccessException exception) {
                    throw new AssertionError(exception);
                }
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                try {
                    assertEquals(org.adaway.model.adblocking.AdBlockMethod.VPN,
                            selected.get(activity));
                    assertTrue(pending.getBoolean(activity));
                    assertTrue(activity.findViewById(R.id.onboarding_start_button).isEnabled());
                    com.google.android.material.card.MaterialCardView card =
                            activity.findViewById(R.id.onboarding_vpn_card);
                    assertTrue(card.isChecked());
                } catch (IllegalAccessException exception) {
                    throw new AssertionError(exception);
                }
            });
        }
    }

    @Test(timeout = 60_000)
    public void undefinedMethodHomeLaunchRedirectsToOnboarding() throws Exception {
        PreferenceHelper.setAbBlockMethod(this.context, UNDEFINED);

        ActivityScenario.launch(HomeActivity.class);

        assertAccessibilityText(this.context.getString(R.string.onboarding_title));
        assertAccessibilityText(this.context.getString(R.string.onboarding_vpn_title));
        assertAccessibilityText(this.context.getString(R.string.onboarding_root_title));
        assertEquals(UNDEFINED, PreferenceHelper.getAdBlockMethod(this.context));
    }

    @Test(timeout = 60_000)
    public void onboardingChooserStartsUnselectedWhenAutoDetectIsSkipped() throws Exception {
        Intent intent = new Intent(this.context, OnboardingActivity.class)
                .putExtra(OnboardingActivity.EXTRA_SKIP_AUTO_DETECT, true);

        ActivityScenario.launch(intent);

        assertAccessibilityText(this.context.getString(R.string.onboarding_title));
        assertAccessibilityText(this.context.getString(R.string.onboarding_vpn_title));
        assertAccessibilityText(this.context.getString(R.string.onboarding_root_title));
        assertAccessibilityTextEnabled(
                this.context.getString(R.string.onboarding_start_button), false);
        assertAccessibilityText(this.context.getString(R.string.onboarding_method_not_selected));
        assertEquals(ROOT, PreferenceHelper.getAdBlockMethod(this.context));
    }

    @Test(timeout = 60_000)
    public void noRootAutoDetectPreselectsVpnWithoutCompletingOnboarding() throws Exception {
        PreferenceHelper.setAbBlockMethod(this.context, UNDEFINED);
        String vpnSelectedDescription = this.context.getString(
                R.string.onboarding_method_accessibility,
                this.context.getString(R.string.onboarding_vpn_title),
                this.context.getString(R.string.onboarding_vpn_desc),
                this.context.getString(R.string.onboarding_method_selected));

        ActivityScenario.launch(OnboardingActivity.class);

        assertAccessibilityText(this.context.getString(R.string.onboarding_title));
        assertAccessibilityText(vpnSelectedDescription);
        assertAccessibilityTextEnabled(
                this.context.getString(R.string.onboarding_start_button), true);
        assertEquals(UNDEFINED, PreferenceHelper.getAdBlockMethod(this.context));
    }

    @Test(timeout = 90_000)
    public void onboardingCompleteHomeLaunchSubscribesDefaultSourcesWhenEmpty()
            throws Exception {
        assertTrue("Test DB should start with no external sources.",
                this.database.hostsSourceDao().getAll().isEmpty());
        PreferenceHelper.setAbBlockMethod(this.context, ROOT);

        Intent intent = new Intent(this.context, HomeActivity.class)
                .putExtra(HomeActivity.EXTRA_ONBOARDING_COMPLETE, true);
        ActivityScenario.launch(intent);

        waitForDefaultSources();
        assertSafetyAllowlistPresent();
    }

    private void waitForDefaultSources() {
        Set<String> expectedUrls = new HashSet<>();
        for (FilterListCatalog.CatalogEntry entry : FilterListCatalog.getDefaults()) {
            expectedUrls.add(entry.url);
        }
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (containsAllDefaults(expectedUrls)) {
                return;
            }
            SystemClock.sleep(100L);
        }

        List<HostsSource> sources = this.database.hostsSourceDao().getAll();
        Set<String> actualUrls = new HashSet<>();
        for (HostsSource source : sources) {
            actualUrls.add(source.getUrl());
        }
        expectedUrls.removeAll(actualUrls);
        throw new AssertionError("Default sources were not inserted. Missing URLs: "
                + expectedUrls + ", sourceCount=" + sources.size());
    }

    private boolean containsAllDefaults(@NonNull Set<String> expectedUrls) {
        List<HostsSource> sources = this.database.hostsSourceDao().getAll();
        Set<String> actualUrls = new HashSet<>();
        for (HostsSource source : sources) {
            actualUrls.add(source.getUrl());
            if (expectedUrls.contains(source.getUrl())) {
                assertTrue("Default source should be enabled: " + source.getLabel(),
                        source.isEnabled());
            }
        }
        return actualUrls.containsAll(expectedUrls);
    }

    private void assertSafetyAllowlistPresent() {
        Set<String> expectedHosts = new HashSet<>(WaTgSafetyAllowlist.REQUIRED_DOMAINS);
        for (HostListItem item : this.database.hostsListItemDao().getUserList()) {
            if (expectedHosts.remove(item.getHost())) {
                assertEquals("Safety allowlist entry should be allowed: " + item.getHost(),
                        ALLOWED, item.getType());
                assertTrue("Safety allowlist entry should be enabled: " + item.getHost(),
                        item.isEnabled());
            }
        }
        assertTrue("Missing safety allowlist entries: " + expectedHosts, expectedHosts.isEmpty());
    }

    private void insertUserSource() {
        HostsSource source = new HostsSource();
        source.setId(USER_SOURCE_ID);
        source.setLabel(this.context.getString(R.string.hosts_user_source));
        source.setUrl(USER_SOURCE_URL);
        source.setAllowEnabled(true);
        source.setRedirectEnabled(true);
        this.database.hostsSourceDao().insert(source);
    }

    private static void assertAccessibilityText(String expectedText) throws Exception {
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

    private static void assertAccessibilityTextEnabled(String expectedText, boolean enabled)
            throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .getRootInActiveWindow();
            try {
                AccessibilityNodeInfo node = root == null ? null : findText(root, expectedText);
                if (node != null) {
                    try {
                        assertEquals("Unexpected enabled state for " + expectedText,
                                enabled, node.isEnabled());
                        return;
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
        throw new AssertionError("Text was not visible: " + expectedText);
    }

    private static boolean containsText(
            @NonNull AccessibilityNodeInfo node,
            @NonNull String expectedText) {
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

    @Nullable
    private static AccessibilityNodeInfo findText(
            @NonNull AccessibilityNodeInfo node,
            @NonNull String expectedText) {
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

    private static boolean nodeTextMatches(
            @NonNull AccessibilityNodeInfo node,
            @NonNull String expectedText) {
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        return (text != null && expectedText.contentEquals(text))
                || (description != null && expectedText.contentEquals(description));
    }

    private static boolean nodeTextContains(
            @NonNull AccessibilityNodeInfo node,
            @NonNull String expectedText) {
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        return (text != null && text.toString().contains(expectedText))
                || (description != null && description.toString().contains(expectedText));
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

    private static void finishResumedActivities() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)) {
                activity.finish();
            }
        });
    }
}
