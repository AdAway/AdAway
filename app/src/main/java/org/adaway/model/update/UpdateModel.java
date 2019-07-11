package org.adaway.model.update;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.core.content.FileProvider;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.adaway.R;
import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URL;

import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N;
import static android.os.Build.VERSION_CODES.P;

public class UpdateModel {
    private static final String TAG = "UpdateModel";
    private static final String STABLE_MANIFEST = "https://gist.githubusercontent.com/PerfectSlayer/a701257ae04a3102feb84734fcdc2d74/raw/26d1ed53e53ea6eda65b64120dc4484e255ae182/stable.json";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:67.0) Gecko/20100101 Firefox/67.0";
    private final Context context;
    private final OkHttpClient client;
    private final MutableLiveData<Manifest> manifest;
//    private final MutableLiveData<AppUpdateStatus> updateStatus;
    private long versionCode;
    private String versionName;

    public UpdateModel(Context context) {
        this.context = context;
        this.manifest = new MutableLiveData<>();
//        this.updateStatus = new MutableLiveData<>();
        loadCurrentVersion();
        this.client = buildHttpClient();
    }

    /**
     * Get the current version code.
     *
     * @return The current version code, {@code -1} if could not be retrieve.
     */
    public long getVersionCode() {
        return this.versionCode;
    }

    /**
     * Get the current version name.
     *
     * @return The current version name, empty string if could not be retrieved.
     */
    public String getVersionName() {
        return this.versionName;
    }

    // TODO Comment
    public LiveData<Manifest> getManifest() {
        return manifest;
    }

//    public MutableLiveData<AppUpdateStatus> getUpdateStatus() {
//        return updateStatus;
//    }

    /**
     * Check if there is an update available.
     */
    public void checkUpdate() {
        Manifest manifest = downloadManifest();
        // Notify update
        if (manifest != null) {
            this.manifest.postValue(manifest);
//            this.updateStatus.postValue(manifest.versionCode > this.versionCode ? UPDATE_AVAILABLE : FIRST_RUN);
        }
    }

    private OkHttpClient buildHttpClient() {
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        return new OkHttpClient.Builder()
                .cookieJar(new JavaNetCookieJar(cookieManager))
                .build();
    }

    private void loadCurrentVersion() {
        try {
            PackageManager packageManager = this.context.getPackageManager();
            String packageName = this.context.getPackageName();
            PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
            if (SDK_INT >= P) {
                this.versionCode = packageInfo.getLongVersionCode();
            } else {
                this.versionCode = packageInfo.versionCode;
            }
            this.versionName = packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException exception) {
            Log.w(TAG, "Failed to get application version code.", exception);
            this.versionCode = -1;
            this.versionName = "";
        }
    }

    private Manifest downloadManifest() {
        Request request = new Request.Builder()
                .header("User-Agent", USER_AGENT)
                .url(STABLE_MANIFEST)
                .build();
        try (Response execute = this.client.newCall(request).execute();
             ResponseBody body = execute.body()) {
            return new Manifest(body.string(), this.versionCode);
        } catch (IOException | JSONException exception) {
            Log.e(Constants.TAG, "Unable to download manifest.", exception);
            // Return failed
            return null;
        }
    }

    public void update() {
        Manifest manifest = this.manifest.getValue();
        if (manifest == null) {
            return;
        }
        try {
            URL apkUrl = new URL(manifest.link);
            File apkFile = computeFile(apkUrl);
            downloadApk(apkUrl, apkFile);
            installApk(apkFile);
        } catch (IOException exception) {
            Log.e(Constants.TAG, "Unable to download manifest.", exception);
        }
    }

    private File computeFile(URL apkUrl) throws IOException {
        String apkName = apkUrl.getFile();
        apkName = apkName.substring(apkName.lastIndexOf("/") + 1);

        File updatesFolder = new File(this.context.getFilesDir(), "updates");
        if (!updatesFolder.exists() && !updatesFolder.mkdir()) {
            throw new IOException("Failed to create updates folder.");
        }
        return new File(updatesFolder, apkName);
    }

//    private URL getApkUrl(String json) throws JSONException {
//        JSONObject jsonObject = new JSONObject(json);
//        JSONArray mirrorsArray = jsonObject.getJSONArray("MIRRORS");
//        for (int index = 0; index < mirrorsArray.length(); index++) {
//            JSONObject mirrorObject = mirrorsArray.getJSONObject(index);
//            try {
//                return new URL(mirrorObject.getString("url"));
//            } catch (MalformedURLException exception) {
//                Log.w(TAG, "Failed to parse URL of mirror", exception);
//            }
//        }
//        throw new JSONException("No mirror in JSON");
//    }

    private void downloadApk(URL url, File destinationFile) throws IOException {
        Request request = new Request.Builder()
                .header("User-Agent", USER_AGENT)
                .url(url)
                .build();
        // Skip download if file already exists
        if (destinationFile.exists()) {
            return;
        }
        // Download to destination file
        try (Response execute = this.client.newCall(request).execute();
             ResponseBody body = execute.body();
             InputStream inputStream = body.byteStream();
             FileOutputStream outputStream = new FileOutputStream(destinationFile)) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        } catch (IOException exception) {
            if (destinationFile.exists() && !destinationFile.delete()) {
                Log.w(TAG, "Failed to delete update file.", exception);
            }
            throw exception;
        }
    }

    public void showChangelog(boolean download) {
        Manifest manifest = this.manifest.getValue();
        if (manifest == null) {
            return;
        }

        MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(this.context)
                .setTitle(R.string.update_title)
                .setMessage(manifest.changelog);
        if (download) {
            dialogBuilder
                    .setPositiveButton(R.string.update_update_button, null)
                    .setNegativeButton(R.string.button_close, (dialog, which) -> dialog.dismiss());
        } else {
            dialogBuilder.setNeutralButton(R.string.button_close, (dialog, which) -> dialog.dismiss());
        }
        dialogBuilder.create().show();
    }

    private void installApk(File apkFile) {
        Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (SDK_INT >= N) {
            install.setData(FileProvider.getUriForFile(this.context, this.context.getPackageName() + ".provider", apkFile));
        } else {
            apkFile.setReadable(true, false);
            install.setData(Uri.fromFile(apkFile));
        }
        this.context.startActivity(install);
    }
}
