package org.adaway.model.source;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;

import org.adaway.AdAwayApplication;
import org.adaway.helper.NotificationHelper;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.error.HostErrorException;

import static androidx.work.ExistingPeriodicWorkPolicy.UPDATE;
import static androidx.work.ListenableWorker.Result.failure;
import static androidx.work.ListenableWorker.Result.retry;
import static androidx.work.ListenableWorker.Result.success;
import static java.util.concurrent.TimeUnit.HOURS;

import timber.log.Timber;

/**
 * This class is a service to check for hosts sources update.<br/>
 * It could be enabled or disabled for periodic check.<br>
 * The implementation is based on WorkManager from Android X.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public final class SourceUpdateService {
    /**
     * The name of the periodic work.
     */
    static final String WORK_NAME = "HostsUpdateWork";
    /**
     * One-time work name used for "update now" triggers.
     */
    public static final String WORK_NAME_NOW = "HostsUpdateWorkNow";

    /**
     * Private constructor.
     */
    private SourceUpdateService() {

    }

    /**
     * Enable update service.
     *
     * @param context              The application context.
     * @param unmeteredNetworkOnly <code>true</code> if the update should be done on unmetered network only, <code>false</code> otherwise.
     */
    public static void enable(Context context, boolean unmeteredNetworkOnly) {
        enqueueWork(context, UPDATE, unmeteredNetworkOnly);
    }

    /**
     * Disable update service.
     *
     * @param context The application context.
     */
    public static void disable(Context context) {
        // Cancel previous work
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
    }

    /**
     * Enqueue a one-time "update all enabled sources now" run.
     * This is used after large subscribe/import operations.
     */
    public static void enqueueUpdateNow(@NonNull Context context) {
        boolean wifiOnly = PreferenceHelper.getUpdateOnlyOnWifi(context);
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(wifiOnly ? NetworkType.UNMETERED : NetworkType.CONNECTED)
                .setRequiresStorageNotLow(true)
                .build();
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(HostsSourcesImmediateUpdateWorker.class)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME_NOW, ExistingWorkPolicy.REPLACE, req);
    }

    /**
     * Sync service on user preferences.
     *
     * @param context The application context.
     */
    public static void syncPreferences(Context context) {
        if (PreferenceHelper.getUpdateCheckHostsDaily(context)) {
            enqueueWork(context, UPDATE, PreferenceHelper.getUpdateOnlyOnWifi(context));
        } else {
            disable(context);
        }
    }

    private static void enqueueWork(Context context, ExistingPeriodicWorkPolicy workPolicy, boolean unmeteredNetworkOnly) {
        // Create work request
        PeriodicWorkRequest workRequest = getWorkRequest(unmeteredNetworkOnly);
        // Enqueue work request
        WorkManager workManager = WorkManager.getInstance(context);
        workManager.enqueueUniquePeriodicWork(WORK_NAME, workPolicy, workRequest);
    }

    /**
     * Create source update work request.
     *
     * @param unmeteredNetworkOnly <code>true</code> if the update should be done on unmetered network only, <code>false</code> otherwise.
     * @return The source update work request to queue.
     */
    private static PeriodicWorkRequest getWorkRequest(boolean unmeteredNetworkOnly) {
        // Create worker constraints
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(unmeteredNetworkOnly ? NetworkType.UNMETERED : NetworkType.CONNECTED)
                .setRequiresStorageNotLow(true)
                .build();
        // Create work request
        // Run hourly (above WorkManager's 15-min floor; was 24 hours).
        return new PeriodicWorkRequest.Builder(HostsSourcesUpdateWorker.class, 1, HOURS)
                .setConstraints(constraints)
                .setInitialDelay(3, HOURS)
                .build();
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
            Timber.i("Starting update worker");
            // Create model
            AdAwayApplication application = (AdAwayApplication) getApplicationContext();
            SourceModel model = application.getSourceModel();
            // Check for update
            boolean hasUpdate;
            try {
                hasUpdate = model.checkForUpdate();
            } catch (HostErrorException exception) {
                // An error occurred, check will be retried
                Timber.e(exception, "Failed to check for update. Will retry later.");
                return retry();
            }
            if (hasUpdate) {
                // Do update
                try {
                    doUpdate(application);
                } catch (HostErrorException exception) {
                    // Installation failed. Worker failed.
                    Timber.e(exception, "Failed to apply hosts file during background update.");
                    return failure();
                }
            }
            // Return as success
            return success();
        }

        /**
         * Handle update according user preferences.
         *
         * @param application The application.
         * @throws HostErrorException If the update could not be handled.
         */
        private void doUpdate(AdAwayApplication application) throws HostErrorException {
            // Check if automatic update are enabled
            if (PreferenceHelper.getAutomaticUpdateDaily(application)) {
                // Retrieve source updates with adaptive batching
                SourceModel sourceModel = application.getSourceModel();
                if (sourceModel.checkAndRetrieveHostsSources()) {
                    // Apply source updates
                    AdBlockModel adBlockModel = application.getAdBlockModel();
                    adBlockModel.apply();
                }
            } else {
                // Display update notification
                NotificationHelper.showUpdateHostsNotification(application);
            }
        }
    }

    /**
     * One-time worker that always updates all enabled sources and applies immediately.
     */
    public static class HostsSourcesImmediateUpdateWorker extends Worker {
        public HostsSourcesImmediateUpdateWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        @NonNull
        @Override
        public Result doWork() {
            Timber.i("Starting immediate update-all worker");
            AdAwayApplication application = (AdAwayApplication) getApplicationContext();
            try {
                SourceModel sourceModel = application.getSourceModel();
                if (!sourceModel.checkAndRetrieveHostsSources()) {
                    return retry();
                }
                AdBlockModel adBlockModel = application.getAdBlockModel();
                adBlockModel.apply();
                return success();
            } catch (HostErrorException exception) {
                Timber.e(exception, "Failed to update/apply during immediate update-all.");
                return retry();
            }
        }
    }
}
