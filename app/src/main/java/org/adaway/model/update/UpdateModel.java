package org.adaway.model.update;

import android.app.DownloadManager;
import android.content.Context;
import android.content.IntentFilter;
import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.adaway.util.Log;
import org.json.JSONException;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE;
import static android.content.Context.DOWNLOAD_SERVICE;
import static org.adaway.BuildConfig.VERSION_CODE;
import static org.adaway.BuildConfig.VERSION_NAME;

/**
 * This class is the model in charge of updating the application.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class UpdateModel {
    static final String TAG = "UpdateModel";
    private static final String STABLE_MANIFEST = "https://gist.githubusercontent.com/PerfectSlayer/a701257ae04a3102feb84734fcdc2d74/raw/c1c35bf3d5680e63a500646bf603959711b01227/stable.json";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:67.0) Gecko/20100101 Firefox/67.0";
    private final Context context;
    private final OkHttpClient client;
    private final MutableLiveData<Manifest> manifest;
    private UpdateDownloadReceiver receiver;

    public UpdateModel(Context context) {
        this.context = context;
        this.manifest = new MutableLiveData<>();
        this.client = buildHttpClient();
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

    // TODO Comment
    public LiveData<Manifest> getManifest() {
        return manifest;
    }

    /**
     * Check if there is an update available.
     */
    public void checkUpdate() {
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
        Request request = new Request.Builder()
                .header("User-Agent", USER_AGENT)
                .url(STABLE_MANIFEST)
                .build();
        try (Response execute = this.client.newCall(request).execute();
             ResponseBody body = execute.body()) {
            return new Manifest(body.string(), (long) VERSION_CODE);
        } catch (IOException | JSONException exception) {
            Log.e(TAG, "Unable to download manifest.", exception);
            // Return failed
            return null;
        }
    }

    public void update() {
        // Check manifest
        Manifest manifest = this.manifest.getValue();
        if (manifest == null) {
            return;
        }
        // Check previous broadcast receiver
        if (this.receiver != null) {
            this.context.unregisterReceiver(this.receiver);
        }
        // Queue download
        long downloadId = download(manifest);
        // Register new broadcast receiver
        this.receiver = new UpdateDownloadReceiver(downloadId);
        this.context.registerReceiver(this.receiver, new IntentFilter(ACTION_DOWNLOAD_COMPLETE));
    }

    private long download(Manifest manifest) {
        Log.i(TAG, "Downloading " + manifest.version + " from " + manifest.link + ".");
        Uri uri = Uri.parse(manifest.link);
        DownloadManager.Request request = new DownloadManager.Request(uri)
                .setTitle("AdAway " + manifest.version);
        DownloadManager downloadManager = (DownloadManager) this.context.getSystemService(DOWNLOAD_SERVICE);
        return downloadManager.enqueue(request);
    }
}
