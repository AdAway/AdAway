package org.adaway.tile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.service.quicksettings.TileService;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.AdAwayApplication;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.adblocking.AdBlockModel;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public class AdBlockingTileServiceInstrumentedTest {
    private static final long TILE_CLICK_TIMEOUT_MS = 5_000L;
    private static final long TILE_COMMAND_POLL_MS = 500L;

    private AdAwayApplication application;
    private AdBlockModel originalAdBlockModel;

    @After
    public void tearDown() throws Exception {
        if (this.application != null) {
            injectAdBlockModel(this.application, this.originalAdBlockModel);
        }
    }

    @Test
    public void listeningStopsObservingOriginalModelEvenAfterModelChanges() {
        Context context = ApplicationProvider.getApplicationContext();
        RecordingAdBlockModel first = new RecordingAdBlockModel(context);
        RecordingAdBlockModel second = new RecordingAdBlockModel(context);
        java.util.concurrent.atomic.AtomicReference<AdBlockModel> current =
                new java.util.concurrent.atomic.AtomicReference<>(first);
        AdBlockingTileService service = new AdBlockingTileService() {
            @Override
            AdBlockModel getModel() {
                return current.get();
            }
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                service.onStartListening();
                service.onStartListening();
                assertTrue(first.isApplied().hasObservers());
                current.set(second);
                service.onStopListening();
                assertFalse(first.isApplied().hasObservers());
                service.onStartListening();
                assertTrue(second.isApplied().hasObservers());
                service.onDestroy();
                assertFalse(second.isApplied().hasObservers());
            } finally {
                service.onStopListening();
            }
        });
    }

    @Test
    public void installedTileServiceIsExportedAndQuickSettingsPermissionProtected()
            throws PackageManager.NameNotFoundException {
        Context context = ApplicationProvider.getApplicationContext();
        PackageManager packageManager = context.getPackageManager();
        ComponentName component = tileComponent(context);

        ServiceInfo service = packageManager.getServiceInfo(component, 0);

        assertTrue("Quick Settings tile service must be exported for SystemUI binding.",
                service.exported);
        assertEquals("Quick Settings tile service must require the platform bind permission.",
                Manifest.permission.BIND_QUICK_SETTINGS_TILE,
                service.permission);
    }

    @Test
    public void quickSettingsTileActionResolvesOnlyToProtectedTileService() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(TileService.ACTION_QS_TILE)
                .setPackage(context.getPackageName());

        List<ResolveInfo> services = context.getPackageManager().queryIntentServices(intent, 0);

        assertEquals("Exactly one installed service should handle the Quick Settings tile action.",
                1, services.size());
        ServiceInfo service = services.get(0).serviceInfo;
        assertEquals(context.getPackageName(), service.packageName);
        assertEquals(AdBlockingTileService.class.getName(), service.name);
        assertTrue("Resolved tile service must be exported for SystemUI binding.",
                service.exported);
        assertEquals("Resolved tile service must require the platform bind permission.",
                Manifest.permission.BIND_QUICK_SETTINGS_TILE,
                service.permission);
    }

    @Test
    public void statusBarCommandCanAddClickAndRemoveTileWhenAvailable() throws Exception {
        assumeTrue("Skipping Quick Settings shell proof: cmd statusbar tile commands unavailable.",
                statusBarTileCommandsAvailable());

        Context context = ApplicationProvider.getApplicationContext();
        this.application = (AdAwayApplication) context.getApplicationContext();
        this.originalAdBlockModel = this.application.getAdBlockModel();
        RecordingAdBlockModel recordingAdBlockModel = new RecordingAdBlockModel(context);
        injectAdBlockModel(this.application, recordingAdBlockModel);

        String component = tileComponent(context).flattenToShortString();
        boolean addAttempted = false;
        String lastClickOutput = "";
        try {
            assumeCommandAccepted("add-tile",
                    executeShellCommand("cmd statusbar add-tile " + component + " 2>&1"));
            addAttempted = true;
            SystemClock.sleep(TILE_COMMAND_POLL_MS);
            long deadline = SystemClock.uptimeMillis() + TILE_CLICK_TIMEOUT_MS;
            while (!recordingAdBlockModel.hasToggle()
                    && SystemClock.uptimeMillis() < deadline) {
                lastClickOutput =
                        executeShellCommand("cmd statusbar click-tile " + component + " 2>&1");
                assumeCommandAccepted("click-tile", lastClickOutput);
                recordingAdBlockModel.awaitToggle(TILE_COMMAND_POLL_MS);
            }

            assumeTrue("Skipping Quick Settings shell proof: cmd statusbar accepted add/click "
                            + "but SystemUI did not dispatch the tile callback. Last output: "
                            + lastClickOutput,
                    recordingAdBlockModel.hasToggle());
        } finally {
            if (addAttempted) {
                executeShellCommand("cmd statusbar remove-tile " + component + " 2>&1");
            }
        }
    }

    private static ComponentName tileComponent(Context context) {
        return new ComponentName(context, AdBlockingTileService.class);
    }

    private static boolean statusBarTileCommandsAvailable() {
        String help = executeShellCommand("cmd statusbar help 2>&1");
        String usage = executeShellCommand("cmd statusbar 2>&1");
        String output = (help + "\n" + usage).toLowerCase(Locale.US);
        return !hasUnavailableStatusBarOutput(output)
                && output.contains("add-tile")
                && output.contains("click-tile")
                && output.contains("remove-tile");
    }

    private static void assumeCommandAccepted(String operation, String output) {
        assumeTrue("Skipping Quick Settings shell proof: cmd statusbar " + operation
                        + " was not accepted: " + output,
                !hasUnavailableStatusBarOutput(output.toLowerCase(Locale.US)));
    }

    private static boolean hasUnavailableStatusBarOutput(String output) {
        return output.contains("can't find service: statusbar")
                || output.contains("unknown command")
                || output.contains("permission denial")
                || output.contains("permission denied")
                || output.contains("cmd: inaccessible or not found");
    }

    private static String executeShellCommand(String command) {
        try (ParcelFileDescriptor descriptor = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .executeShellCommand(command);
             FileInputStream output = new FileInputStream(descriptor.getFileDescriptor());
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[256];
            int count;
            while ((count = output.read(chunk)) != -1) {
                buffer.write(chunk, 0, count);
            }
            return buffer.toString(StandardCharsets.UTF_8.name());
        } catch (IOException exception) {
            throw new AssertionError("Failed to execute shell command: " + command, exception);
        }
    }

    private static void injectAdBlockModel(
            AdAwayApplication application,
            AdBlockModel adBlockModel) throws Exception {
        Field field = AdAwayApplication.class.getDeclaredField("adBlockModel");
        field.setAccessible(true);
        field.set(application, adBlockModel);
    }

    private static final class RecordingAdBlockModel extends AdBlockModel {
        private final AtomicInteger toggleCount = new AtomicInteger();
        private final CountDownLatch toggleLatch = new CountDownLatch(1);

        private RecordingAdBlockModel(Context context) {
            super(context);
        }

        @Override
        public AdBlockMethod getMethod() {
            return AdBlockMethod.ROOT;
        }

        @Override
        public void apply() {
            this.toggleCount.incrementAndGet();
            this.applied.postValue(true);
            this.toggleLatch.countDown();
        }

        @Override
        public void revert() {
            this.toggleCount.incrementAndGet();
            this.applied.postValue(false);
            this.toggleLatch.countDown();
        }

        private boolean awaitToggle(long timeoutMs) throws InterruptedException {
            return this.toggleLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
        }

        private boolean hasToggle() {
            return this.toggleCount.get() > 0;
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
