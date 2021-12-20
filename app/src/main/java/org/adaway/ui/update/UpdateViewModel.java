package org.adaway.ui.update;

import static android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR;
import static android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES;

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

import java.util.concurrent.Executor;

import timber.log.Timber;

/**
 * This class is an {@link AndroidViewModel} for the {@link UpdateActivity} cards.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class UpdateViewModel extends AdwareViewModel {
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
        NETWORK_IO.execute(() -> trackProgress(downloadId));
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
                Timber.d(e, "Failed to wait before querying download manager.");
                Thread.currentThread().interrupt();
            }
            // Query download manager
            Cursor cursor = downloadManager.query(query);
            if (!cursor.moveToFirst()) {
                Timber.d("Download item was not found");
                continue;
            }
            // Check download status
            int statusColumnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            int status = cursor.getInt(statusColumnIndex);
            switch (status) {
                case DownloadManager.STATUS_FAILED:
                    finishDownload = true;
                    this.downloadProgress.postValue(null);
                    break;
                case DownloadManager.STATUS_RUNNING:
                    int totalSizeColumnIndex = cursor.getColumnIndex(COLUMN_TOTAL_SIZE_BYTES);
                    total = cursor.getLong(totalSizeColumnIndex);
                    if (total >= 0) {
                        int bytesDownloadedColumnIndex = cursor.getColumnIndex(COLUMN_BYTES_DOWNLOADED_SO_FAR);
                        long downloaded = cursor.getLong(bytesDownloadedColumnIndex);
                        this.downloadProgress.postValue(new DownloadStatus(downloaded, total));
                    }
                    break;
                case DownloadManager.STATUS_SUCCESSFUL:
                    this.downloadProgress.postValue(new DownloadStatus(total, total));
                    finishDownload = true;
                    break;
            }
        }
    }
}
