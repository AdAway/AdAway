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

import org.adaway.AdAwayApplication;
import org.adaway.helper.NotificationHelper;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.error.HostErrorException;

import static androidx.work.ExistingPeriodicWorkPolicy.KEEP;
import static androidx.work.ExistingPeriodicWorkPolicy.REPLACE;
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
    private static final String WORK_NAME = "HostsUpdateWork";

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
        enqueueWork(context, REPLACE, unmeteredNetworkOnly);
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
     * Sync service on user preferences.
     *
     * @param context The application context.
     */
    static void syncPreferences(Context context) {
        if (PreferenceHelper.getUpdateCheckHostsDaily(context)) {
            enqueueWork(context, KEEP, PreferenceHelper.getUpdateOnlyOnWifi(context));
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
        return new PeriodicWorkRequest.Builder(HostsSourcesUpdateWorker.class, 6, HOURS)
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
                // Retrieve source updates
                SourceModel sourceModel = application.getSourceModel();
                sourceModel.retrieveHostsSources();
                // Apply source updates
                AdBlockModel adBlockModel = application.getAdBlockModel();
                adBlockModel.apply();
            } else {
                // Display update notification
                NotificationHelper.showUpdateHostsNotification(application);
            }
        }
    }
}
