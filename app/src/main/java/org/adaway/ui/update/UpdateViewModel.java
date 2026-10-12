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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import timber.log.Timber;

/**
 * This class is an {@link AndroidViewModel} for the {@link UpdateActivity} cards.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class UpdateViewModel extends AdwareViewModel {
    private static final long TRACKING_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(15);
    private final UpdateModel updateModel;
    private final MutableLiveData<DownloadStatus> downloadProgress;
    private final AtomicBoolean tracking;

    public UpdateViewModel(@NonNull Application application) {
        super(application);
        this.updateModel = ((AdAwayApplication) application).getUpdateModel();
        this.downloadProgress = new MutableLiveData<>();
        this.tracking = new AtomicBoolean(false);
    }

    public LiveData<Manifest> getAppManifest() {
        return this.updateModel.getManifest();
    }

    public void update() {
        long downloadId = this.updateModel.update();
        if (downloadId != -1) {
            AppExecutors.getInstance().networkIO().execute(() -> trackProgress(downloadId));
        } else {
            this.downloadProgress.postValue(null);
        }
    }

    public MutableLiveData<DownloadStatus> getDownloadProgress() {
        return this.downloadProgress;
    }

    public void resumePendingInstall() {
        if (!this.updateModel.hasPendingInstall()) {
            return;
        }
        AppExecutors.getInstance().networkIO().execute(() -> {
            this.updateModel.resumePendingInstall();
            this.downloadProgress.postValue(null);
        });
    }

    private void trackProgress(long downloadId) {
        // Ensure we don't run multiple progress loops concurrently.
        if (!this.tracking.compareAndSet(false, true)) {
            return;
        }
        DownloadManager downloadManager = getApplication().getSystemService(DownloadManager.class);
        if (downloadManager == null) {
            Timber.w("Download service unavailable; stopping progress tracking.");
            this.downloadProgress.postValue(null);
            this.tracking.set(false);
            return;
        }
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        long startedAt = System.currentTimeMillis();
        int missingCount = 0;
        try {
            boolean finishDownload = false;
            while (!finishDownload) {
                // Hard timeout: avoid infinite background loops.
                if (System.currentTimeMillis() - startedAt > TRACKING_TIMEOUT_MS) {
                    Timber.w("Stopping download progress tracking (timeout).");
                    this.downloadProgress.postValue(null);
                    return;
                }

                // Add wait before querying download manager
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Timber.d(e, "Failed to wait before querying download manager.");
                    Thread.currentThread().interrupt();
                    this.downloadProgress.postValue(null);
                    return;
                }

                // Query download manager
                try (Cursor cursor = downloadManager.query(query)) {
                    if (cursor == null || !cursor.moveToFirst()) {
                        // Download entry may not be immediately available; don't spin forever if it never shows up.
                        missingCount++;
                        if (missingCount > 20) { // ~10s with 500ms sleep
                            Timber.w("Download item was not found after repeated queries; stopping tracking.");
                            this.downloadProgress.postValue(null);
                            return;
                        }
                        continue;
                    }
                    missingCount = 0;

                    // Check download status
                    int statusColumnIndex = cursor.getColumnIndex(COLUMN_STATUS);
                    if (statusColumnIndex < 0) {
                        Timber.w("Download status column missing; stopping progress tracking.");
                        this.downloadProgress.postValue(null);
                        return;
                    }
                    int status = cursor.getInt(statusColumnIndex);
                    switch (status) {
                        case STATUS_FAILED:
                            finishDownload = true;
                            this.downloadProgress.postValue(null);
                            break;
                        case STATUS_RUNNING:
                            int totalSizeColumnIndex = cursor.getColumnIndex(COLUMN_TOTAL_SIZE_BYTES);
                            int bytesDownloadedColumnIndex =
                                    cursor.getColumnIndex(COLUMN_BYTES_DOWNLOADED_SO_FAR);
                            if (totalSizeColumnIndex < 0 || bytesDownloadedColumnIndex < 0) {
                                Timber.w("Download progress columns missing; stopping progress tracking.");
                                this.downloadProgress.postValue(null);
                                return;
                            }
                            long total = cursor.getLong(totalSizeColumnIndex);
                            if (total > 0) {
                                long downloaded = cursor.getLong(bytesDownloadedColumnIndex);
                                this.downloadProgress.postValue(new PendingDownloadStatus(downloaded, total));
                            }
                            break;
                        case STATUS_SUCCESSFUL:
                            this.downloadProgress.postValue(new CompleteDownloadStatus());
                            finishDownload = true;
                            break;
                        default:
                            break;
                    }
                }
            }
        } finally {
            this.tracking.set(false);
        }
    }
}
