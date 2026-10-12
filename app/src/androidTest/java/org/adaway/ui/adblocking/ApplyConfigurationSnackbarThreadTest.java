package org.adaway.ui.adblocking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Looper;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.error.HostError;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.source.SourceModel;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.lists.ListsActivity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class ApplyConfigurationSnackbarThreadTest {
    private AdAwayApplication application;
    private SourceModel originalSource;
    private AdBlockModel originalAdBlock;

    @Before
    public void setUp() {
        application = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(application, "set up apply feedback thread");
        originalSource = application.getSourceModel();
        originalAdBlock = application.getAdBlockModel();
    }

    @After
    public void tearDown() throws Exception {
        setModel("sourceModel", originalSource);
        setModel("adBlockModel", originalAdBlock);
        InstrumentedTestState.resetForPassiveRootUi(application, "tear down apply feedback thread");
    }

    @Test
    public void successfulApplyBuildsCompletionFeedbackOnMainThread() throws Exception {
        assertCompletionThread(0);
    }

    @Test
    public void hostFailureBuildsErrorFeedbackOnMainThread() throws Exception {
        assertCompletionThread(1);
    }

    @Test
    public void unexpectedDatabaseFailureEndsLoadingOnMainThread() throws Exception {
        assertCompletionThread(2);
    }

    private void assertCompletionThread(int failure) throws Exception {
        AtomicReference<Thread> worker = new AtomicReference<>();
        setModel("sourceModel", new SourceModel(application) {
            @Override
            public boolean checkAndRetrieveHostsSources() throws HostErrorException {
                worker.set(Thread.currentThread());
                if (failure == 1) throw new HostErrorException(HostError.NO_CONNECTION);
                if (failure == 2) throw new IllegalStateException("fixture database failure");
                return true;
            }
        });
        setModel("adBlockModel", new NoOpAdBlockModel(application));
        AtomicReference<RecordingSnackbar> snackbar = new AtomicReference<>();
        try (ActivityScenario<ListsActivity> scenario = ActivityScenario.launch(ListsActivity.class)) {
            scenario.onActivity(activity -> {
                RecordingSnackbar feedback = new RecordingSnackbar(
                        activity.findViewById(R.id.coordinator));
                snackbar.set(feedback);
                feedback.apply();
            });
            assertTrue("Apply must complete", snackbar.get().completed.await(10, TimeUnit.SECONDS));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertNotSame(Looper.getMainLooper().getThread(), worker.get());
            assertSame(Looper.getMainLooper().getThread(), snackbar.get().completionThread);
            assertEquals(failure == 0, snackbar.get().successful);
            assertNull("Real Snackbar creation must not throw", snackbar.get().uiFailure);
        }
    }

    private void setModel(String fieldName, Object value) throws Exception {
        Field field = AdAwayApplication.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(application, value);
    }

    private static final class RecordingSnackbar extends ApplyConfigurationSnackbar {
        final CountDownLatch completed = new CountDownLatch(1);
        Thread completionThread;
        boolean successful;
        Throwable uiFailure;

        RecordingSnackbar(View view) {
            super(view, true, false);
        }

        @Override
        void endLoading(boolean successfulInstall) {
            completionThread = Thread.currentThread();
            successful = successfulInstall;
            try {
                super.endLoading(successfulInstall);
            } catch (Throwable failure) {
                uiFailure = failure;
            } finally {
                completed.countDown();
            }
        }
    }

    private static final class NoOpAdBlockModel extends AdBlockModel {
        NoOpAdBlockModel(Context context) { super(context); }
        @Override public AdBlockMethod getMethod() { return AdBlockMethod.ROOT; }
        @Override public void apply() { }
        @Override public void revert() { }
        @Override public boolean isRecordingLogs() { return false; }
        @Override public void setRecordingLogs(boolean recording) { }
        @Override public List<String> getLogs() { return Collections.emptyList(); }
        @Override public void clearLogs() { }
    }
}
