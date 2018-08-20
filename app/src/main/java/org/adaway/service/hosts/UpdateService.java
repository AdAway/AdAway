package org.adaway.service.hosts;

import android.content.Context;
import android.support.annotation.NonNull;

import org.adaway.helper.ApplyHelper;
import org.adaway.helper.NotificationHelper;
import org.adaway.helper.PreferenceHelper;
import org.adaway.util.StatusCodes;

import java.util.concurrent.TimeUnit;

import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;

/**
 * This class is a service to check for hosts sources update.<br/>
 * It could be {@link #enable(boolean)} or {@link #disable()} for periodic check.<br>
 * The implementation is based on WorkManager from Android Jetpack.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public final class UpdateService {
    /**
     * The update service work tag.
     */
    private static final String WORKER_TAG = "UpdateServiceWorkTag";

    /**
     * Private constructor.
     */
    private UpdateService() {

    }

    /**
     * Enable update service.
     *
     * @param unmeteredNetworkOnly <code>true</code> if the update should be done on unmetered network only, <code>false</code> otherwise.
     */
    public static void enable(boolean unmeteredNetworkOnly) {
        // Cancel previous work
        WorkManager.getInstance().cancelAllWorkByTag(WORKER_TAG);
        // Create worker constraints
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(unmeteredNetworkOnly ? androidx.work.NetworkType.UNMETERED : NetworkType.NOT_REQUIRED)
                .setRequiresStorageNotLow(true)
                .build();
        // Create work request
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(HostsSourcesUpdateWorker.class, 24, TimeUnit.HOURS)
                .addTag(WORKER_TAG)
                .setConstraints(constraints)
                .build();
        // Enqueue work request
        WorkManager.getInstance().enqueue(workRequest);
    }

    /**
     * Disable update service.
     */
    public static void disable() {
        // Cancel previous work
        WorkManager.getInstance().cancelAllWorkByTag(WORKER_TAG);
    }

    /**
     * This class is a {@link Worker} to fetch hosts sources updates and install them if needed.
     *
     * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
     */
    public static class HostsSourcesUpdateWorker extends Worker {
        @NonNull
        @Override
        public Result doWork() {
            Context context = this.getApplicationContext();
            UpdateResult updateResult = UpdateFetcher.checkForUpdates(context);
            // Check if fetch failed
            if (updateResult.mCode == StatusCodes.DOWNLOAD_FAIL) {
                return Result.RETRY;
            }
            // Check if updates are available
            if (updateResult.mCode == StatusCodes.UPDATE_AVAILABLE) {
                // Chef automatic update
                if (PreferenceHelper.getAutomaticUpdateDaily(context)) {
                    // Install update
                    new ApplyHelper(context).apply();
                } else {
                    // Display update notification
                    NotificationHelper.showUpdateHostsNotification(context);
                }
            }
            // Return as success
            return Result.SUCCESS;
        }
    }
}
