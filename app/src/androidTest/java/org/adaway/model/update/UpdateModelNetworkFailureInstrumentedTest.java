/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */
package org.adaway.model.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;

import androidx.lifecycle.Observer;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.testing.InstrumentedTestState;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;

@RunWith(AndroidJUnit4.class)
public class UpdateModelNetworkFailureInstrumentedTest {
    private static final long TIMEOUT_MS = 10_000L;
    private static final int CURRENT_VERSION_CODE = 100;
    private static final String SHA256 =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private Context context;
    private MockWebServer server;
    private KeyPair keyPair;
    private String publicKeyBase64;
    private UpdateModel updateModel;
    private AtomicReference<Manifest> observedManifest;
    private Observer<Manifest> observer;

    @Before
    public void setUp() throws Exception {
        this.context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up update network test");
        this.server = new MockWebServer();
        HeldCertificate localhostCertificate = new HeldCertificate.Builder()
                .addSubjectAlternativeName("localhost")
                .build();
        HandshakeCertificates serverCertificates = new HandshakeCertificates.Builder()
                .heldCertificate(localhostCertificate)
                .build();
        this.server.useHttps(serverCertificates.sslSocketFactory(), false);
        this.server.start();
        HandshakeCertificates clientCertificates = new HandshakeCertificates.Builder()
                .addTrustedCertificate(localhostCertificate.certificate())
                .build();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        this.keyPair = generator.generateKeyPair();
        this.publicKeyBase64 =
                Base64.getEncoder().encodeToString(this.keyPair.getPublic().getEncoded());
        this.updateModel = new UpdateModel(
                this.context,
                new UpdateModel.VersionInfo(CURRENT_VERSION_CODE, "13.5-test"),
                new OkHttpClient.Builder()
                        .sslSocketFactory(
                                clientCertificates.sslSocketFactory(),
                                clientCertificates.trustManager())
                        .build(),
                this.server.url("/manifest.json").toString(),
                true,
                UpdateStore.ADAWAY,
                this.publicKeyBase64);
        this.observedManifest = new AtomicReference<>();
        this.observer = this.observedManifest::set;
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> this.updateModel.getManifest().observeForever(this.observer));
    }

    @After
    public void tearDown() throws Exception {
        try {
            if (this.updateModel != null && this.observer != null) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(
                        () -> this.updateModel.getManifest().removeObserver(this.observer));
            }
            if (this.server != null) {
                this.server.shutdown();
            }
        } finally {
            if (this.context != null) {
                InstrumentedTestState.resetForPassiveRootUi(
                        this.context,
                        "tear down update network test");
            }
        }
    }

    @Test(timeout = 60_000)
    public void failedManifestDownloadClearsPreviouslyAvailableUpdate() throws Exception {
        this.server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(envelope(payload(CURRENT_VERSION_CODE + 1))));

        Manifest checked = this.updateModel.checkForUpdateManifest();
        assertNotNull("Workers must receive the freshly downloaded manifest directly.", checked);
        assertEquals(CURRENT_VERSION_CODE + 1, checked.versionCode);

        Manifest available = waitForManifest();
        assertSame(checked, available);
        assertEquals(CURRENT_VERSION_CODE + 1, available.versionCode);
        assertTrue(available.updateAvailable);
        assertEquals("Network failure regression test", available.changelog);
        RecordedRequest successRequest = this.server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(successRequest);
        assertEquals("/manifest.json", successRequest.getRequestUrl().encodedPath());
        assertEquals(Integer.toString(CURRENT_VERSION_CODE),
                successRequest.getRequestUrl().queryParameter("versionCode"));
        assertEquals(Integer.toString(Build.VERSION.SDK_INT),
                successRequest.getRequestUrl().queryParameter("sdkCode"));
        assertEquals("stable", successRequest.getRequestUrl().queryParameter("channel"));
        assertEquals("adaway", successRequest.getRequestUrl().queryParameter("store"));

        this.server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("server unavailable"));

        assertNull("A failed check must not return the previous available update.",
                this.updateModel.checkForUpdateManifest());

        waitForManifestCleared();
        RecordedRequest failureRequest = this.server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(failureRequest);
        assertEquals("/manifest.json", failureRequest.getRequestUrl().encodedPath());
    }

    private Manifest waitForManifest() {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            Manifest manifest = this.observedManifest.get();
            if (manifest != null) {
                return manifest;
            }
            SystemClock.sleep(100L);
        }
        throw new AssertionError("Update manifest was not published.");
    }

    private void waitForManifestCleared() {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            if (this.observedManifest.get() == null) {
                return;
            }
            SystemClock.sleep(100L);
        }
        assertNull("Failed update checks must clear stale update availability.",
                this.observedManifest.get());
    }

    private String payload(int versionCode) throws Exception {
        return new JSONObject()
                .put("version", "13.5.1-test")
                .put("versionCode", versionCode)
                .put("changelog", "Network failure regression test")
                .put("apkSha256", SHA256)
                .put("signingCertificateSha256", SHA256)
                .put("apkUrl", "https://app.adaway.org/adaway.apk?versionCode=" + versionCode)
                .put("channel", "stable")
                .put("store", "adaway")
                .put("expiresAt", Instant.now().plusSeconds(3600).toString())
                .toString();
    }

    private String envelope(String payload) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(this.keyPair.getPrivate());
        signer.update(payload.getBytes(StandardCharsets.UTF_8));
        return new JSONObject()
                .put("payload", payload)
                .put("signature", Base64.getEncoder().encodeToString(signer.sign()))
                .toString();
    }
}
