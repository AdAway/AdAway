/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */
package org.adaway.ui.update;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.google.android.material.button.MaterialButton;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.model.update.Manifest;
import org.adaway.model.update.UpdateModel;
import org.adaway.testing.InstrumentedTestState;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@RunWith(AndroidJUnit4.class)
public class UpdateActivityDownloadStatusInstrumentedTest {
    private static final long TIMEOUT_MS = 10_000L;

    private Context context;
    private AdAwayApplication application;

    @Before
    public void setUp() throws Exception {
        this.context = ApplicationProvider.getApplicationContext();
        this.application = (AdAwayApplication) this.context.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up update status UI");
        publishManifest(null);
    }

    @After
    public void tearDown() throws Exception {
        try {
            publishManifest(null);
            finishResumedActivities();
        } finally {
            if (this.context != null) {
                InstrumentedTestState.resetForPassiveRootUi(
                        this.context,
                        "tear down update status UI");
            }
        }
    }

    @Test(timeout = 60_000)
    public void updateActivityRendersPendingCompleteAndResetDownloadStatus() throws Exception {
        Manifest manifest = createManifest();
        publishManifest(manifest);

        try (ActivityScenario<UpdateActivity> scenario =
                     ActivityScenario.launch(UpdateActivity.class)) {
            waitForUpdateAvailable(scenario, manifest.changelog);

            PendingDownloadStatus pending = new PendingDownloadStatus(2_621_440L, 10_485_760L);
            publishProgress(scenario, pending);
            waitForDownloadState(
                    scenario,
                    INVISIBLE,
                    VISIBLE,
                    pending.getProgress(),
                    pending.format(this.context));

            CompleteDownloadStatus complete = new CompleteDownloadStatus();
            publishProgress(scenario, complete);
            waitForDownloadState(
                    scenario,
                    INVISIBLE,
                    VISIBLE,
                    complete.getProgress(),
                    complete.format(this.context));

            publishProgress(scenario, null);
            waitForDownloadState(scenario, VISIBLE, GONE, 0, "");
        }
    }

    private void publishManifest(Manifest manifest) throws Exception {
        MutableLiveData<Manifest> manifestLiveData = getManifestLiveData();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> manifestLiveData.setValue(manifest));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    @Test(timeout = 60_000)
    public void resetDownloadStatusDoesNotOfferAnUnavailableUpdate() throws Exception {
        Manifest manifest = createManifest();
        publishManifest(manifest);

        try (ActivityScenario<UpdateActivity> scenario =
                     ActivityScenario.launch(UpdateActivity.class)) {
            waitForUpdateAvailable(scenario, manifest.changelog);
            publishProgress(scenario, new PendingDownloadStatus(1L, 2L));
            publishManifest(null);
            publishProgress(scenario, null);

            waitForDownloadState(scenario, GONE, GONE, 0, "");
        }
    }

    @SuppressWarnings("unchecked")
    private MutableLiveData<Manifest> getManifestLiveData() throws Exception {
        Field field = UpdateModel.class.getDeclaredField("manifest");
        field.setAccessible(true);
        return (MutableLiveData<Manifest>) field.get(this.application.getUpdateModel());
    }

    private Manifest createManifest() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        int nextVersion = this.application.getUpdateModel().getVersionCode() + 1;
        String sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        JSONObject payload = new JSONObject()
                .put("version", "13.5.2-test")
                .put("versionCode", nextVersion)
                .put("changelog", "Update download status proof")
                .put("apkSha256", sha256)
                .put("signingCertificateSha256", sha256)
                .put("apkUrl", "https://app.adaway.org/adaway.apk?versionCode=" + nextVersion)
                .put("channel", "stable")
                .put("store", "adaway")
                .put("expiresAt", Instant.now().plus(1, ChronoUnit.DAYS).toString());
        String payloadText = payload.toString();
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(payloadText.getBytes(StandardCharsets.UTF_8));
        JSONObject envelope = new JSONObject()
                .put("payload", payloadText)
                .put("signature", Base64.getEncoder().encodeToString(signer.sign()));
        return new Manifest(envelope.toString(), nextVersion - 1, publicKey, "stable", "adaway");
    }

    private static void publishProgress(
            @NonNull ActivityScenario<UpdateActivity> scenario,
            DownloadStatus status) {
        scenario.onActivity(activity -> {
            UpdateViewModel viewModel = new ViewModelProvider(activity).get(UpdateViewModel.class);
            viewModel.getDownloadProgress().setValue(status);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static void waitForUpdateAvailable(
            @NonNull ActivityScenario<UpdateActivity> scenario,
            @NonNull String changelog) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            boolean[] matched = new boolean[1];
            scenario.onActivity(activity -> {
                TextView header = activity.findViewById(R.id.headerTextView);
                MaterialButton updateButton = activity.findViewById(R.id.update_button);
                TextView changelogView = activity.findViewById(R.id.changelogTextView);
                matched[0] = textEquals(header, activity.getString(
                        R.string.update_update_available_header))
                        && updateButton.getVisibility() == VISIBLE
                        && textEquals(changelogView, changelog);
            });
            if (matched[0]) {
                return;
            }
            SystemClock.sleep(100L);
        }
        throw new AssertionError("UpdateActivity did not render update availability.");
    }

    private static void waitForDownloadState(
            @NonNull ActivityScenario<UpdateActivity> scenario,
            int expectedButtonVisibility,
            int expectedProgressVisibility,
            int expectedProgress,
            @NonNull String expectedText) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            boolean[] matched = new boolean[1];
            scenario.onActivity(activity -> {
                MaterialButton updateButton = activity.findViewById(R.id.update_button);
                ProgressBar progressBar = activity.findViewById(R.id.downloadProgressBar);
                TextView progressText = activity.findViewById(R.id.progressTextView);
                matched[0] = updateButton.getVisibility() == expectedButtonVisibility
                        && progressBar.getVisibility() == expectedProgressVisibility
                        && progressBar.getProgress() == expectedProgress
                        && textEquals(progressText, expectedText);
            });
            if (matched[0]) {
                return;
            }
            SystemClock.sleep(100L);
        }
        throw new AssertionError("UpdateActivity did not render download state: "
                + expectedText);
    }

    private static boolean textEquals(TextView view, String expectedText) {
        return view != null && expectedText.contentEquals(view.getText());
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
