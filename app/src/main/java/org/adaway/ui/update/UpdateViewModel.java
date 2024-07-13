package org.adaway.ui.update;

import static android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR;
import static android.app.DownloadManager.COLUMN_STATUS;
import static android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES;
import static android.app.DownloadManager.STATUS_FAILED;
import static android.app.DownloadManager.STATUS_RUNNING;
import static android.app.DownloadManager.STATUS_SUCCESSFUL;

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
        while (!finishDownload) {
            // Add wait before querying download manager
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Timber.d(e, "Failed to wait before querying download manager.");
                Thread.currentThread().interrupt();
            }
            // Query download manager
            try (Cursor cursor = downloadManager.query(query)) {
                if (!cursor.moveToFirst()) {
                    Timber.d("Download item was not found");
                    continue;
                }
                // Check download status
                int statusColumnIndex = cursor.getColumnIndex(COLUMN_STATUS);
                int status = cursor.getInt(statusColumnIndex);
                switch (status) {
                    case STATUS_FAILED:
                        finishDownload = true;
                        this.downloadProgress.postValue(null);
                        break;
                    case STATUS_RUNNING:
                        int totalSizeColumnIndex = cursor.getColumnIndex(COLUMN_TOTAL_SIZE_BYTES);
                        long total = cursor.getLong(totalSizeColumnIndex);
                        if (total > 0) {
                            int bytesDownloadedColumnIndex = cursor.getColumnIndex(COLUMN_BYTES_DOWNLOADED_SO_FAR);
                            long downloaded = cursor.getLong(bytesDownloadedColumnIndex);
                            this.downloadProgress.postValue(new PendingDownloadStatus(downloaded, total));
                        }
                        break;
                    case STATUS_SUCCESSFUL:
                        this.downloadProgress.postValue(new CompleteDownloadStatus());
                        finishDownload = true;
                        break;
                }
            }
        }
    }
}
