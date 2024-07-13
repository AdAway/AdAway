package org.adaway.model.update;

import static android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE;
import static android.os.Build.VERSION.SDK_INT;
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

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.json.JSONException;

import java.io.IOException;

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
    private static final String MANIFEST_URL = "https://app.adaway.org/manifest.json";
    private static final String DOWNLOAD_URL = "https://app.adaway.org/adaway.apk?versionCode=";
    private final Context context;
    private final VersionInfo versionInfo;
    private final OkHttpClient client;
    private final MutableLiveData<Manifest> manifest;
    private ApkDownloadReceiver receiver;

    /**
     * Constructor.
     *
     * @param context The application context.
     */
    public UpdateModel(Context context) {
        this.context = context;
        this.versionInfo = VersionInfo.get(context);
        this.manifest = new MutableLiveData<>();
        this.client = buildHttpClient();
        ApkUpdateService.syncPreferences(context);
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
        Manifest manifest = downloadManifest();
        // Notify update
        if (manifest != null) {
            this.manifest.postValue(manifest);
        }
    }

    private OkHttpClient buildHttpClient() {
        return new OkHttpClient.Builder().build();
    }

    private Manifest downloadManifest() {
        if (!this.versionInfo.isValid()) {
            return null;
        }
        HttpUrl httpUrl = requireNonNull(HttpUrl.parse(MANIFEST_URL), "Failed to parse manifest URL")
                .newBuilder()
                .addQueryParameter("versionCode", Integer.toString(this.versionInfo.code))
                .addQueryParameter("sdkCode", Integer.toString(SDK_INT))
                .addQueryParameter("channel", getChannel())
                .addQueryParameter("store", getStore().getName())
                .build();
        Request request = new Request.Builder()
                .url(httpUrl)
                .build();
        try (Response execute = this.client.newCall(request).execute();
             ResponseBody body = execute.body()) {
            if (execute.isSuccessful() && body != null) {
                return new Manifest(body.string(), this.versionInfo.code);
            } else {
                return null;
            }
        } catch (IOException | JSONException exception) {
            Timber.e(exception, "Unable to download manifest.");
            // Return failed
            return null;
        }
    }

    /**
     * Update the application to the latest version.
     *
     * @return The download identifier ({@code -1} if download was not started).
     */
    public long update() {
        // Check manifest
        Manifest manifest = this.manifest.getValue();
        if (manifest == null) {
            return -1;
        }
        // Check previous broadcast receiver
        if (this.receiver != null) {
            this.context.unregisterReceiver(this.receiver);
        }
        // Queue download
        long downloadId = download(manifest);
        // Register new broadcast receiver
        this.receiver = new ApkDownloadReceiver(downloadId);
        this.context.registerReceiver(this.receiver, new IntentFilter(ACTION_DOWNLOAD_COMPLETE));
        // Return download identifier
        return downloadId;
    }

    private long download(Manifest manifest) {
        Timber.i("Downloading " + manifest.version + ".");
        Uri uri = Uri.parse(DOWNLOAD_URL + manifest.versionCode);
        DownloadManager.Request request = new DownloadManager.Request(uri)
                .setTitle("AdAway " + manifest.version)
                .setDescription(this.context.getString(R.string.update_notification_description));
        DownloadManager downloadManager = this.context.getSystemService(DownloadManager.class);
        return downloadManager.enqueue(request);
    }

    private static class VersionInfo {
        private final int code;
        private final String name;

        private VersionInfo(int code, String name) {
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
