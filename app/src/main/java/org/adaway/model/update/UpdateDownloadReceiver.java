package org.adaway.model.update;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.adaway.util.Log;

import static android.content.Context.DOWNLOAD_SERVICE;
import static org.adaway.model.update.UpdateModel.TAG;

/**
 * This class is a {@link BroadcastReceiver} to install downloaded application updates.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class UpdateDownloadReceiver extends BroadcastReceiver {
    private final long downloadId;

    public UpdateDownloadReceiver(long downloadId) {
        this.downloadId = downloadId;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        //Fetching the download id received with the broadcast
        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
        //Checking if the received broadcast is for our enqueued download by matching download id
        if (this.downloadId == id) {
            DownloadManager downloadManager = (DownloadManager) context.getSystemService(DOWNLOAD_SERVICE);
            Uri apkUri = downloadManager.getUriForDownloadedFile(id);
            if (apkUri == null) {
                Log.w(TAG, "Failed to download id: " + id);
            } else {
                installApk(context, apkUri);
            }
        }
    }

    private void installApk(Context context, Uri apkUri) {
        Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        install.setData(apkUri);
        context.startActivity(install);
    }
}
