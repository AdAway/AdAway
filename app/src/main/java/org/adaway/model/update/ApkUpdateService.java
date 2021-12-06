package org.adaway.model.update;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.adaway.AdAwayApplication;
import org.adaway.helper.NotificationHelper;
import org.adaway.helper.PreferenceHelper;

import static androidx.work.ExistingPeriodicWorkPolicy.KEEP;
import static androidx.work.ExistingPeriodicWorkPolicy.REPLACE;
import static androidx.work.ListenableWorker.Result.success;
import static java.util.concurrent.TimeUnit.DAYS;

import timber.log.Timber;

/**
 * This class is a service to check for application update.<br/>
 * It could be enabled or disabled for periodic check.<br>
 * The implementation is based on WorkManager from Android X.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public final class ApkUpdateService {
    /**
     * The name of the periodic work.
     */
    private static final String WORK_NAME = "ApkUpdateWork";

    /**
     * Private constructor.
     */
    private ApkUpdateService() {

    }

    /**
     * Enable update service.
     *
     * @param context The application context.
     */
    public static void enable(Context context) {
        enqueueWork(context, REPLACE);
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

    static void syncPreferences(Context context) {
        if (PreferenceHelper.getUpdateCheckAppDaily(context)) {
            enqueueWork(context, KEEP);
        } else {
            disable(context);
        }
    }

    private static void enqueueWork(Context context, ExistingPeriodicWorkPolicy workPolicy) {
        // Create work request
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(ApkUpdateWorker.class, 1, DAYS).build();
        // Enqueue work request
        WorkManager workManager = WorkManager.getInstance(context);
        workManager.enqueueUniquePeriodicWork(WORK_NAME, workPolicy, workRequest);
    }

    /**
     * This class is a {@link Worker} to check for application update and notify them if needed.
     *
     * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
     */
    public static class ApkUpdateWorker extends Worker {
        /**
         * Constructor.
         *
         * @param context      The application context.
         * @param workerParams The parameters to setup this worker.
         */
        public ApkUpdateWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        @NonNull
        @Override
        public Result doWork() {
            Timber.i("Starting update worker");
            // Create model
            AdAwayApplication application = (AdAwayApplication) getApplicationContext();
            UpdateModel model = application.getUpdateModel();
            // Check for update
            model.checkForUpdate();
            Manifest manifest = model.getManifest().getValue();
            if (manifest != null && manifest.updateAvailable) {
                // Display update notification
                NotificationHelper.showUpdateApplicationNotification(application);
            }
            // Return as success
            return success();
        }
    }
}
