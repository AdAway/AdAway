package org.adaway.service.hosts;

import android.content.Context;
import android.support.annotation.NonNull;

import org.adaway.helper.NotificationHelper;
import org.adaway.helper.PreferenceHelper;
import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.adaway.model.hostsinstall.HostsInstallException;
import org.adaway.model.hostsinstall.HostsInstallModel;

import java.util.concurrent.TimeUnit;

import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

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
        /**
         * Constructor.
         *
         * @param context      The application context.
         * @param workerParams The parameters to setup this worker.
         */
        public HostsSourcesUpdateWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        @NonNull
        @Override
        public Result doWork() {
            // Create model
            Context context = this.getApplicationContext();
            HostsInstallModel model = new HostsInstallModel(context);
            // Check for update
            boolean hasUpdate;
            try {
                hasUpdate = model.checkForUpdate();
            } catch (HostsInstallException exception) {
                // An error occurred, check will be retried
                Log.e(Constants.TAG, "Failed to check for update. Will retry later.", exception);
                return Result.RETRY;
            }
            if (hasUpdate) {
                // Do update
                try {
                    doUpdate(context, model);
                } catch (HostsInstallException exception) {
                    // Installation failed. Worker failed.
                    Log.e(Constants.TAG, "Failed to apply hosts file during background update.", exception);
                    return Result.FAILURE;
                }
            }
            // Return as success
            return Result.SUCCESS;
        }

        /**
         * Handle update according user preferences.
         *
         * @param context The application context.
         * @param model   The hosts install model.
         * @throws HostsInstallException If the update could not be handled.
         */
        private void doUpdate(Context context, HostsInstallModel model) throws HostsInstallException {
            // Check if automatic update are enabled
            if (PreferenceHelper.getAutomaticUpdateDaily(context)) {
                // Install update
                model.downloadHostsSources();
                model.applyHostsFile();
            } else {
                // Display update notification
                NotificationHelper.showUpdateHostsNotification(context);
            }
        }
    }
}
