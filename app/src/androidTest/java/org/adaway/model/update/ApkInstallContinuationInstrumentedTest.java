package org.adaway.model.update;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ApkInstallContinuationInstrumentedTest {
    private static final Uri APK = Uri.parse("content://downloads/my_downloads/42");

    @Test
    public void permissionGrantResumesSameVerifiedApkExactlyOnce() {
        Context context = ApplicationProvider.getApplicationContext();
        RecordingReceiver receiver = new RecordingReceiver();
        receiver.installApk(context, APK);
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                receiver.launched.get(0).getAction());
        assertTrue(receiver.hasPendingInstall());

        receiver.permissionGranted = true;
        receiver.resumePendingInstall(context);
        receiver.resumePendingInstall(context);

        assertEquals(1, receiver.verificationCount);
        assertEquals(2, receiver.launched.size());
        Intent install = receiver.launched.get(1);
        assertEquals(Intent.ACTION_INSTALL_PACKAGE, install.getAction());
        assertEquals(APK, install.getData());
        assertTrue((install.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        assertFalse(receiver.hasPendingInstall());
    }

    @Test
    public void deniedPermissionDoesNotLoopSettingsOrLosePendingApk() {
        Context context = ApplicationProvider.getApplicationContext();
        RecordingReceiver receiver = new RecordingReceiver();
        receiver.installApk(context, APK);
        receiver.resumePendingInstall(context);
        assertEquals(1, receiver.launched.size());
        assertEquals(0, receiver.verificationCount);
        assertTrue(receiver.hasPendingInstall());
    }

    @Test
    public void changedApkIsRejectedWhenPermissionReturns() {
        Context context = ApplicationProvider.getApplicationContext();
        RecordingReceiver receiver = new RecordingReceiver();
        receiver.installApk(context, APK);
        receiver.permissionGranted = true;
        receiver.verified = false;
        receiver.resumePendingInstall(context);
        assertEquals(1, receiver.verificationCount);
        assertEquals(1, receiver.launched.size());
        assertFalse(receiver.hasPendingInstall());
    }

    private static final class RecordingReceiver extends ApkDownloadReceiver {
        final List<Intent> launched = new ArrayList<>();
        boolean permissionGranted;
        boolean verified = true;
        int verificationCount;

        RecordingReceiver() {
            super(42L, "test-sha256", "test-certificate");
        }

        @Override
        boolean canRequestPackageInstalls(Context context) {
            return this.permissionGranted;
        }

        @Override
        boolean isVerifiedApk(Context context, Uri apkUri) {
            assertEquals(APK, apkUri);
            this.verificationCount++;
            return this.verified;
        }

        @Override
        boolean startActivityIfResolvable(Context context, Intent intent, String target) {
            this.launched.add(intent);
            return true;
        }
    }
}
