package org.adaway.model.update;

import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Settings;

import androidx.annotation.Nullable;

import static android.content.Intent.ACTION_INSTALL_PACKAGE;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.O;
import static android.app.DownloadManager.COLUMN_STATUS;
import static android.app.DownloadManager.STATUS_FAILED;
import static android.app.DownloadManager.STATUS_SUCCESSFUL;

import timber.log.Timber;

/**
 * This class is a {@link BroadcastReceiver} to install downloaded application updates.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class ApkDownloadReceiver extends BroadcastReceiver {
    private final long downloadId;
    private final String expectedSha256;
    private final String expectedSigningCertificateSha256;
    @Nullable
    private final Runnable onTerminal;
    @Nullable
    private volatile Uri pendingInstallUri;

    public ApkDownloadReceiver(
            long downloadId,
            String expectedSha256,
            String expectedSigningCertificateSha256) {
        this(downloadId, expectedSha256, expectedSigningCertificateSha256, null);
    }

    ApkDownloadReceiver(
            long downloadId,
            String expectedSha256,
            String expectedSigningCertificateSha256,
            @Nullable Runnable onTerminal) {
        this.downloadId = downloadId;
        this.expectedSha256 = expectedSha256;
        this.expectedSigningCertificateSha256 = expectedSigningCertificateSha256;
        this.onTerminal = onTerminal;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        //Fetching the download id received with the broadcast
        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
        //Checking if the received broadcast is for our enqueued download by matching download id
        if (this.downloadId == id) {
            handleBroadcastTerminalDownload(context);
        }
    }

    boolean handleTerminalDownload(Context context) {
        DownloadManager downloadManager = context.getSystemService(DownloadManager.class);
        if (downloadManager == null) {
            Timber.w("Download service unavailable for id: %s.", this.downloadId);
            return false;
        }
        if (!isTerminal(downloadManager)) {
            return false;
        }
        processTerminalDownload(context, downloadManager);
        return true;
    }

    private void handleBroadcastTerminalDownload(Context context) {
        DownloadManager downloadManager = context.getSystemService(DownloadManager.class);
        if (downloadManager == null) {
            Timber.w("Download service unavailable for id: %s.", this.downloadId);
            unregister(context);
            return;
        }
        processTerminalDownload(context, downloadManager);
    }

    private boolean isTerminal(DownloadManager downloadManager) {
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(this.downloadId);
        try (Cursor cursor = downloadManager.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return false;
            }
            int statusColumn = cursor.getColumnIndex(COLUMN_STATUS);
            if (statusColumn < 0) {
                Timber.w("Download status column unavailable for id: %s.", this.downloadId);
                return false;
            }
            int status = cursor.getInt(statusColumn);
            return status == STATUS_SUCCESSFUL || status == STATUS_FAILED;
        }
    }

    private void processTerminalDownload(Context context, DownloadManager downloadManager) {
        try {
            Uri apkUri = downloadManager.getUriForDownloadedFile(this.downloadId);
            if (apkUri == null) {
                Timber.w("Failed to download id: %s.", this.downloadId);
            } else if (!ApkIntegrityVerifier.matchesSha256(context, apkUri, this.expectedSha256)) {
                Timber.w("Downloaded APK rejected because SHA-256 does not match manifest.");
            } else if (!ApkIntegrityVerifier.matchesSigningCertificateSha256(
                    context, apkUri, this.expectedSigningCertificateSha256)) {
                Timber.w("Downloaded APK rejected because signing certificate does not match installed app.");
            } else {
                installApk(context, apkUri);
            }
        } finally {
            unregister(context);
        }
    }

    private void unregister(Context context) {
        try {
            context.unregisterReceiver(this);
        } catch (IllegalArgumentException exception) {
            Timber.d(exception, "APK download receiver was already unregistered.");
        }
        if (this.onTerminal != null) {
            this.onTerminal.run();
        }
    }

    void installApk(Context context, Uri apkUri) {
        if (!canRequestPackageInstalls(context)) {
            this.pendingInstallUri = apkUri;
            Intent settings = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName()));
            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (!startActivityIfResolvable(context, settings, "install-permission settings")) {
                return;
            }
            Timber.w("APK install permission is disabled; opened install-permission settings.");
            return;
        }

        Intent install = new Intent(ACTION_INSTALL_PACKAGE);
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        install.setData(apkUri);
        startActivityIfResolvable(context, install, "APK installer");
    }

    boolean hasPendingInstall() {
        return this.pendingInstallUri != null;
    }

    synchronized void resumePendingInstall(Context context) {
        Uri apkUri = this.pendingInstallUri;
        if (apkUri == null || !canRequestPackageInstalls(context)) {
            return;
        }
        this.pendingInstallUri = null;
        // The download may have changed or disappeared while settings was open.
        if (isVerifiedApk(context, apkUri)) {
            installApk(context, apkUri);
        } else {
            Timber.w("Pending APK rejected after returning from install-permission settings.");
        }
    }

    boolean canRequestPackageInstalls(Context context) {
        return SDK_INT < O || context.getPackageManager().canRequestPackageInstalls();
    }

    boolean isVerifiedApk(Context context, Uri apkUri) {
        return ApkIntegrityVerifier.matchesSha256(context, apkUri, this.expectedSha256)
                && ApkIntegrityVerifier.matchesSigningCertificateSha256(
                        context, apkUri, this.expectedSigningCertificateSha256);
    }

    boolean startActivityIfResolvable(Context context, Intent intent, String target) {
        if (intent.resolveActivity(context.getPackageManager()) == null) {
            Timber.w("No activity available to open %s.", target);
            return false;
        }
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException exception) {
            Timber.w(exception, "Failed to open %s.", target);
            return false;
        }
    }
}
