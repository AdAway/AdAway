package org.adaway.model.update;

import static android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE;
import static android.os.Build.VERSION.SDK_INT;
import static org.adaway.model.update.UpdateStore.ADAWAY;
import static org.adaway.model.update.UpdateStore.getApkStore;
import static java.util.Objects.requireNonNull;

import android.app.DownloadManager;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.adaway.BuildConfig;
import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.security.GeneralSecurityException;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import timber.log.Timber;

/**
 * This class is the model in charge of updating the application.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class UpdateModel {
    private static final String DOWNLOAD_URL = "https://app.adaway.org/adaway.apk?versionCode=";
    private final Context context;
    private final VersionInfo versionInfo;
    private final OkHttpClient client;
    private final String manifestUrl;
    private final boolean directApkUpdatesEnabled;
    private final UpdateStore storeOverride;
    private final String manifestPublicKeyBase64;
    private final MutableLiveData<Manifest> manifest;
    private ApkDownloadReceiver receiver;
    private volatile ApkDownloadReceiver pendingInstaller;

    /**
     * Constructor.
     *
     * @param context The application context.
     */
    public UpdateModel(Context context) {
        this(
                context,
                VersionInfo.get(context),
                buildHttpClient(),
                null,
                BuildConfig.DIRECT_APK_UPDATES_ENABLED,
                null,
                null);
        ApkUpdateService.syncPreferences(context);
    }

    UpdateModel(
            Context context,
            VersionInfo versionInfo,
            OkHttpClient client,
            String manifestUrl,
            boolean directApkUpdatesEnabled,
            UpdateStore storeOverride,
            String manifestPublicKeyBase64) {
        this.context = context;
        this.versionInfo = versionInfo;
        this.manifest = new MutableLiveData<>();
        this.client = client;
        this.manifestUrl = manifestUrl;
        this.directApkUpdatesEnabled = directApkUpdatesEnabled;
        this.storeOverride = storeOverride;
        this.manifestPublicKeyBase64 = manifestPublicKeyBase64;
    }

    /**
     * Get the current version code.
     *
     * @return The current version code.
     */
    public int getVersionCode() {
        return this.versionInfo.code;
    }

    /**
     * Get the current version name.
     *
     * @return The current version name.
     */
    public String getVersionName() {
        return this.versionInfo.name;
    }

    /**
     * Get the last version manifest.
     *
     * @return The last version manifest.
     */
    public LiveData<Manifest> getManifest() {
        return this.manifest;
    }

    /**
     * Get the application update store.
     *
     * @return The application update store.
     */
    public UpdateStore getStore() {
        if (this.storeOverride != null) {
            return this.storeOverride;
        }
        return getApkStore(this.context);
    }

    /**
     * Get the application update channel.
     *
     * @return The application update channel.
     */
    public String getChannel() {
        return PreferenceHelper.getIncludeBetaReleases(this.context) ? "beta" : "stable";
    }

    /**
     * Check if there is an update available.
     */
    public void checkForUpdate() {
        checkForUpdateManifest();
    }

    @Nullable
    Manifest checkForUpdateManifest() {
        Manifest manifest = downloadManifest();
        this.manifest.postValue(manifest);
        // Workers need the result before LiveData dispatches it on the main thread.
        return manifest;
    }

    private static OkHttpClient buildHttpClient() {
        return new OkHttpClient.Builder().build();
    }

    private Manifest downloadManifest() {
        if (!canSelfUpdate()) {
            return null;
        }
        if (!this.versionInfo.isValid()) {
            return null;
        }
        String channel = getChannel();
        UpdateStore store = getStore();
        String manifestUrl = this.manifestUrl;
        if (manifestUrl == null) {
            ReleaseManifestSource source = "beta".equals(channel)
                    ? findReleaseManifest(true)
                    : new ReleaseManifestSource(ReleaseManifestSource.STABLE_URL, "stable");
            if (source == null) {
                return null;
            }
            manifestUrl = source.url;
            channel = source.channel;
        }
        HttpUrl httpUrl = requireNonNull(HttpUrl.parse(manifestUrl),
                "Failed to parse manifest URL")
                .newBuilder()
                .addQueryParameter("versionCode", Integer.toString(this.versionInfo.code))
                .addQueryParameter("sdkCode", Integer.toString(SDK_INT))
                .addQueryParameter("channel", channel)
                .addQueryParameter("store", store.getName())
                .build();
        Request request = new Request.Builder()
                .url(httpUrl)
                .build();
        try (Response execute = this.client.newCall(request).execute();
            ResponseBody body = execute.body()) {
            if (execute.isSuccessful() && body != null) {
                return new Manifest(
                        body.string(),
                        this.versionInfo.code,
                        this.manifestPublicKeyBase64 != null
                                ? this.manifestPublicKeyBase64
                                : this.context.getString(R.string.update_manifest_public_key),
                        channel,
                        store.getName());
            } else {
                return null;
            }
        } catch (IOException | JSONException | GeneralSecurityException exception) {
            Timber.e(exception, "Unable to download manifest.");
            // Return failed
            return null;
        }
    }

    @Nullable
    private ReleaseManifestSource findReleaseManifest(boolean includeBeta) {
        Request request = new Request.Builder()
                .url(ReleaseManifestSource.RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .build();
        try (Response response = this.client.newCall(request).execute();
                ResponseBody body = response.body()) {
            if (!response.isSuccessful() || body == null) {
                return null;
            }
            return ReleaseManifestSource.latest(new JSONArray(body.string()), includeBeta);
        } catch (IOException | JSONException exception) {
            Timber.w(exception, "Unable to locate the fork release manifest.");
            return null;
        }
    }

    /**
     * Update the application to the latest version.
     *
     * @return The download identifier ({@code -1} if download was not started).
     */
    public long update() {
        if (!canSelfUpdate()) {
            return -1;
        }
        // Check manifest
        Manifest manifest = this.manifest.getValue();
        if (manifest == null) {
            return -1;
        }
        if (manifest.isExpired()) {
            this.manifest.postValue(null);
            return -1;
        }
        // Check previous broadcast receiver
        unregisterReceiver();
        this.pendingInstaller = null;
        // Queue download
        long downloadId = download(manifest);
        if (downloadId == -1) {
            return -1;
        }
        // Register new broadcast receiver
        this.receiver = new ApkDownloadReceiver(
                downloadId,
                manifest.apkSha256,
                manifest.signingCertificateSha256,
                this::clearReceiver);
        IntentFilter filter = new IntentFilter(ACTION_DOWNLOAD_COMPLETE);
        ContextCompat.registerReceiver(
                this.context, this.receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        this.receiver.handleTerminalDownload(this.context);
        // Return download identifier
        return downloadId;
    }

    private void unregisterReceiver() {
        if (this.receiver == null) {
            return;
        }
        try {
            this.context.unregisterReceiver(this.receiver);
        } catch (IllegalArgumentException exception) {
            Timber.d(exception, "APK download receiver was already unregistered.");
        } finally {
            this.receiver = null;
        }
    }

    private void clearReceiver() {
        if (this.receiver != null && this.receiver.hasPendingInstall()) {
            this.pendingInstaller = this.receiver;
        }
        this.receiver = null;
    }

    public boolean hasPendingInstall() {
        ApkDownloadReceiver installer = this.pendingInstaller;
        return installer != null && installer.hasPendingInstall();
    }

    public void resumePendingInstall() {
        ApkDownloadReceiver installer = this.pendingInstaller;
        if (installer != null && canSelfUpdate()) {
            installer.resumePendingInstall(this.context);
            if (!installer.hasPendingInstall() && this.pendingInstaller == installer) {
                this.pendingInstaller = null;
            }
        }
    }

    private long download(Manifest manifest) {
        Timber.i("Downloading " + manifest.version + ".");
        Uri uri = Uri.parse(manifest.apkUrl != null
                ? manifest.apkUrl
                : DOWNLOAD_URL + manifest.versionCode);
        DownloadManager.Request request = new DownloadManager.Request(uri)
                .setTitle("AdAway " + manifest.version)
                .setDescription(this.context.getString(R.string.update_notification_description));
        DownloadManager downloadManager = this.context.getSystemService(DownloadManager.class);
        if (downloadManager == null) {
            return -1;
        }
        return downloadManager.enqueue(request);
    }

    private boolean canSelfUpdate() {
        return this.directApkUpdatesEnabled && getStore() == ADAWAY;
    }

    static final class VersionInfo {
        private final int code;
        private final String name;

        VersionInfo(int code, String name) {
            this.code = code;
            this.name = name;
        }

        public static VersionInfo get(Context context) {
            try {
                PackageInfo packageInfo = context.getPackageManager()
                        .getPackageInfo(context.getPackageName(), 0);
                return new VersionInfo(packageInfo.versionCode, packageInfo.versionName);
            } catch (PackageManager.NameNotFoundException e) {
                return new VersionInfo(0, "development");
            }
        }

        public boolean isValid() {
            return this.code > 0;
        }
    }
}
