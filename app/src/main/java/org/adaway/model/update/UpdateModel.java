package org.adaway.model.update;

import android.app.DownloadManager;
import android.content.Context;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.adaway.R;
import org.adaway.util.Log;
import org.json.JSONException;

import java.io.IOException;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE;
import static org.adaway.BuildConfig.VERSION_CODE;
import static org.adaway.BuildConfig.VERSION_NAME;

/**
 * This class is the model in charge of updating the application.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class UpdateModel {
    static final String TAG = "UpdateModel";
    private static final String MANIFEST_URL = "https://app.adaway.org/manifest.json";
    private static final String DOWNLOAD_URL = "https://app.adaway.org/adaway.apk?versionCode=";
    private final Context context;
    private final OkHttpClient client;
    private final MutableLiveData<Manifest> manifest;
    private final MutableLiveData<Long> updateDownloadId;
    private ApkDownloadReceiver receiver;

    /**
     * Constructor.
     *
     * @param context The application context.
     */
    public UpdateModel(Context context) {
        this.context = context;
        this.manifest = new MutableLiveData<>();
        this.updateDownloadId = new MutableLiveData<>();
        this.client = buildHttpClient();
        ApkUpdateService.syncPreferences(context);
    }

    /**
     * Get the current version code.
     *
     * @return The current version code.
     */
    public int getVersionCode() {
        return VERSION_CODE;
    }

    /**
     * Get the current version name.
     *
     * @return The current version name.
     */
    public String getVersionName() {
        return VERSION_NAME;
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
     * Get the update download identifier.
     *
     * @return The update download identifier.
     */
    public MutableLiveData<Long> getUpdateDownloadId() {
        return this.updateDownloadId;
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
        HttpUrl httpUrl = HttpUrl.parse(MANIFEST_URL)
                .newBuilder()
                .addQueryParameter("versionCode", Integer.toString(VERSION_CODE))
                .addQueryParameter("sdkCode", Integer.toString(Build.VERSION.SDK_INT))
                .build();
        Request request = new Request.Builder()
                .url(httpUrl)
                .build();
        try (Response execute = this.client.newCall(request).execute();
             ResponseBody body = execute.body()) {
            return new Manifest(body.string(), VERSION_CODE);
        } catch (IOException | JSONException exception) {
            Log.e(TAG, "Unable to download manifest.", exception);
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
        Log.i(TAG, "Downloading " + manifest.version + ".");
        Uri uri = Uri.parse(DOWNLOAD_URL + manifest.versionCode);
        DownloadManager.Request request = new DownloadManager.Request(uri)
                .setTitle("AdAway " + manifest.version)
                .setDescription(this.context.getString(R.string.update_notification_description));
        DownloadManager downloadManager = this.context.getSystemService(DownloadManager.class);
        return downloadManager.enqueue(request);
    }
}
