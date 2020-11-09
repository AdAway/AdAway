package org.adaway.ui.update;

import android.app.Application;
import android.app.DownloadManager;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.adaway.AdAwayApplication;
import org.adaway.model.update.Manifest;
import org.adaway.model.update.UpdateModel;
import org.adaway.ui.adware.AdwareViewModel;
import org.adaway.util.AppExecutors;
import org.adaway.util.Log;

import java.util.concurrent.Executor;

import static android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR;
import static android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES;

/**
 * This class is an {@link AndroidViewModel} for the {@link UpdateActivity} cards.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class UpdateViewModel extends AdwareViewModel {
    private static final String TAG = "UpdateViewModel";
    private static final Executor NETWORK_IO = AppExecutors.getInstance().networkIO();
    private final UpdateModel updateModel;
    private final MutableLiveData<DownloadStatus> downloadProgress;

    public UpdateViewModel(@NonNull Application application) {
        super(application);
        this.updateModel = ((AdAwayApplication) application).getUpdateModel();
        this.downloadProgress = new MutableLiveData<>();
    }

    public LiveData<Manifest> getAppManifest() {
        return this.updateModel.getManifest();
    }

    public void update() {
        long downloadId = this.updateModel.update();
        NETWORK_IO.execute(() -> this.trackProgress(downloadId));
    }

    public MutableLiveData<DownloadStatus> getDownloadProgress() {
        return this.downloadProgress;
    }

    private void trackProgress(long downloadId) {
        DownloadManager downloadManager = getApplication().getSystemService(DownloadManager.class);
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        boolean finishDownload = false;
        long total = 0;
        while (!finishDownload) {
            // Add wait before querying download manager
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Log.d(TAG, "Failed to wait before querying download manager.", e);
            }
            // Query download manager
            Cursor cursor = downloadManager.query(query);
            if (!cursor.moveToFirst()) {
                Log.d(TAG, "Download item was not found");
                continue;
            }
            // Check download status
            int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
            switch (status) {
                case DownloadManager.STATUS_FAILED: {
                    finishDownload = true;
                    this.downloadProgress.postValue(null);
                    break;
                }
                case DownloadManager.STATUS_RUNNING: {
                    total = cursor.getLong(cursor.getColumnIndex(COLUMN_TOTAL_SIZE_BYTES));
                    if (total >= 0) {
                        long downloaded = cursor.getLong(cursor.getColumnIndex(COLUMN_BYTES_DOWNLOADED_SO_FAR));
                        this.downloadProgress.postValue(new DownloadStatus(downloaded, total));
                    }
                    break;
                }
                case DownloadManager.STATUS_SUCCESSFUL: {
                    this.downloadProgress.postValue(new DownloadStatus(total, total));
                    finishDownload = true;
                    break;
                }
            }
        }
    }
}
