package org.adaway.ui.discover;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.model.source.SourceModel;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.home.HomeActivity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

@RunWith(AndroidJUnit4.class)
public class DiscoverCacheRecoveryInstrumentedTest {
    private static final long TIMEOUT_MS = 10_000;
    private static final String LISTS = "[{\"id\":701,\"name\":\"Recovered directory\","
            + "\"syntaxIds\":[1],\"tagIds\":[],\"languageIds\":[]}]";
    private static final String SYNTAXES = "[{\"id\":1,\"name\":\"Hosts\"}]";

    private Context context;
    private SharedPreferences cache;
    private SourceModel sourceModel;
    private OkHttpClient previousClient;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicBoolean offline = new AtomicBoolean();

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(context, "set up Discover cache recovery");
        cache = context.getSharedPreferences("filterlists_cache", Context.MODE_PRIVATE);
        seedFreshCache();
        sourceModel = ((AdAwayApplication) context).getSourceModel();
        Field field = SourceModel.class.getDeclaredField("cachedHttpClient");
        field.setAccessible(true);
        previousClient = (OkHttpClient) field.get(sourceModel);
        field.set(sourceModel, new OkHttpClient.Builder().addInterceptor(chain -> {
            requests.incrementAndGet();
            if (offline.get()) throw new IOException("Offline fixture");
            String path = chain.request().url().encodedPath();
            String body;
            switch (path) {
                case "/lists": body = LISTS; break;
                case "/syntaxes": body = SYNTAXES; break;
                case "/tags":
                case "/languages": body = "[]"; break;
                default: throw new IOException("Unexpected request: " + path);
            }
            return new Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(ResponseBody.create(body, MediaType.get("application/json")))
                    .build();
        }).build());
    }

    @After
    public void tearDown() throws Exception {
        if (sourceModel != null) {
            Field field = SourceModel.class.getDeclaredField("cachedHttpClient");
            field.setAccessible(true);
            field.set(sourceModel, previousClient);
        }
        if (context != null) {
            assertTrue(cache.edit().clear().commit());
            InstrumentedTestState.resetForPassiveRootUi(context,
                    "tear down Discover cache recovery");
        }
    }

    @Test
    public void freshCorruptListsRefetchInsteadOfShowingEmptyDirectory() {
        assertCorruptCacheRecovers("listsJson");
    }

    @Test
    public void freshCorruptSyntaxesRefetchInsteadOfShowingEmptyDirectory() {
        assertCorruptCacheRecovers("syntaxesJson");
    }

    @Test
    public void freshCorruptTagsRefetchInsteadOfShowingEmptyDirectory() {
        assertCorruptCacheRecovers("tagsJson");
    }

    @Test
    public void freshCorruptLanguagesRefetchInsteadOfShowingEmptyDirectory() {
        assertCorruptCacheRecovers("languagesJson");
    }

    @Test
    public void freshValidCacheDoesNotFetchNetwork() {
        try (ActivityScenario<HomeActivity> scenario = launchDirectory()) {
            waitForDirectory(scenario);
            assertEquals("Valid fresh cache should not cause network requests", 0, requests.get());
        }
    }

    @Test
    public void corruptCacheOfflineShowsRetryAndRecoversWhenNetworkReturns() {
        assertTrue(cache.edit().putString("listsJson", "not JSON").commit());
        offline.set(true);
        try (ActivityScenario<HomeActivity> scenario = launchDirectory()) {
            waitForState(scenario, true);
            assertTrue("A corrupt fresh cache must attempt recovery", requests.get() > 0);
            offline.set(false);
            scenario.onActivity(activity ->
                    activity.findViewById(R.id.filterlistsStateRetryButton).performClick());
            waitForDirectory(scenario);
            assertEquals(LISTS, cache.getString("listsJson", null));
        }
    }

    private void assertCorruptCacheRecovers(String key) {
        assertTrue(cache.edit().putString(key, "not JSON").commit());
        try (ActivityScenario<HomeActivity> scenario = launchDirectory()) {
            waitForDirectory(scenario);
            assertEquals("All directory payloads must be refreshed", 4, requests.get());
            assertEquals(LISTS, cache.getString("listsJson", null));
            assertEquals(SYNTAXES, cache.getString("syntaxesJson", null));
            assertEquals("[]", cache.getString("tagsJson", null));
            assertEquals("[]", cache.getString("languagesJson", null));
            scenario.onActivity(activity ->
                    activity.findViewById(R.id.filterlistsStateRetryButton).performClick());
            waitForDirectory(scenario);
            assertEquals("Repaired fresh cache must be reusable", 4, requests.get());
        }
    }

    private ActivityScenario<HomeActivity> launchDirectory() {
        ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class);
        scenario.onActivity(activity -> activity.getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_fragment_container, new DiscoverFilterListsFragment())
                .commitNow());
        return scenario;
    }

    private void seedFreshCache() {
        long now = System.currentTimeMillis();
        assertTrue(cache.edit().clear()
                .putString("listsJson", LISTS)
                .putString("syntaxesJson", SYNTAXES)
                .putString("tagsJson", "[]")
                .putString("languagesJson", "[]")
                .putLong("cachedAt", now)
                .putLong("tagsCachedAt", now)
                .commit());
    }

    private void waitForDirectory(ActivityScenario<HomeActivity> scenario) {
        waitForState(scenario, false);
        scenario.onActivity(activity -> {
            RecyclerView recycler = activity.findViewById(R.id.filterlistsRecyclerView);
            assertEquals(1, recycler.getAdapter().getItemCount());
            TextView name = recycler.findViewById(R.id.filterlistsItemName);
            assertEquals("Recovered directory", name.getText().toString());
            assertFalse(activity.findViewById(R.id.filterlistsStateContainer).isShown());
        });
    }

    private void waitForState(ActivityScenario<HomeActivity> scenario, boolean failure) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            AtomicBoolean ready = new AtomicBoolean();
            scenario.onActivity(activity -> {
                View progress = activity.findViewById(R.id.filterlistsProgress);
                RecyclerView recycler = activity.findViewById(R.id.filterlistsRecyclerView);
                TextView title = activity.findViewById(R.id.filterlistsStateTitle);
                View retry = activity.findViewById(R.id.filterlistsStateRetryButton);
                if (progress == null || progress.getVisibility() == View.VISIBLE) return;
                if (failure) {
                    ready.set(retry.isShown() && context.getString(
                            R.string.filterlists_state_load_failed_title)
                            .contentEquals(title.getText()));
                } else {
                    ready.set(recycler != null && recycler.getAdapter() != null
                            && recycler.getAdapter().getItemCount() == 1
                            && recycler.findViewById(R.id.filterlistsItemName) != null);
                }
            });
            if (ready.get()) return;
            SystemClock.sleep(50);
        }
        throw new AssertionError("Directory did not reach " + (failure ? "retry" : "loaded")
                + " state; requests=" + requests.get());
    }
}
