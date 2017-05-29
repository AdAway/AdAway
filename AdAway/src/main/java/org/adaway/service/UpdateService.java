/*
 * Copyright (C) 2011-2012 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This file is part of AdAway.
 * 
 * AdAway is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AdAway is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AdAway.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.adaway.service;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobCreator;
import com.evernote.android.job.JobManager;
import com.evernote.android.job.JobRequest;

import org.adaway.R;
import org.adaway.helper.ApplyHelper;
import org.adaway.helper.PreferenceHelper;
import org.adaway.helper.ResultHelper;
import org.adaway.provider.AdAwayContract.HostsSources;
import org.adaway.provider.ProviderHelper;
import org.adaway.ui.BaseActivity;
import org.adaway.util.ApplyUtils;
import org.adaway.util.Constants;
import org.adaway.util.DateUtils;
import org.adaway.util.Log;
import org.adaway.util.StatusCodes;
import org.adaway.util.Utils;

import java.net.URL;
import java.net.URLConnection;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * This class is a service to check for update.<br/>
 * It could be {@link #enable()} or {@link #disable()} for periodic check.<br>
 * It could also be launched manually which {@link #checkAsync(Context)}.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class UpdateService extends Job {
    // TODO Regroup notification identifier
    private static final int UPDATE_NOTIFICATION_ID = 10;
    /**
     * The update service job tag.
     */
    private static final String JOB_TAG = "UpdateServiceJob";

    /**
     * Enable update service.
     */
    public static void enable() {
        /*
         * Compute time frame between (the next) 3 am and 9 am.
         */
        // Get calendar
        Calendar calendar = Calendar.getInstance();
        // Check current time
        if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= 3) {
            // Set the next day if time frame already starts for today
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        // Set start of the time frame
        calendar.set(Calendar.HOUR_OF_DAY, 3);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        // Get start in ms
        long startMs = calendar.getTimeInMillis();
        // Get end in ms
        long endMs = startMs + TimeUnit.HOURS.toMillis(6);
        // Schedule update job
        new JobRequest.Builder(UpdateService.JOB_TAG)
                .setExecutionWindow(startMs, endMs)
                .setRequiredNetworkType(JobRequest.NetworkType.CONNECTED)
                .setRequirementsEnforced(true)
                .setPersisted(true)
                .setUpdateCurrent(true)
                .build()
                .schedule();
    }

    /**
     * Disable update service.
     */
    public static void disable() {
        // Cancel update job
        JobManager.instance().cancelAllForTag(UpdateService.JOB_TAG);
    }

    /**
     * Check if there is hosts file update.<br>
     * The check will be asynchronous.
     *
     * @param context The application context.
     */
    public static void checkAsync(final Context context) {
        AsyncTask<Void, Void, UpdateResult> task = new AsyncTask<Void, Void, UpdateResult>() {
            @Override
            protected void onPreExecute() {
                // Display update checking notification
                showUpdateNotification(context);
                // Notify base activity
                BaseActivity.setStatusBroadcast(context, context.getString(R.string.status_checking),
                        context.getString(R.string.status_checking_subtitle), StatusCodes.CHECKING);
            }

            @Override
            protected UpdateResult doInBackground(Void... params) {
                return UpdateService.checkForUpdates(context);
            }

            @Override
            protected void onPostExecute(UpdateResult result) {
                // Cancel update checking notification
                UpdateService.cancelUpdateNotification(context);
                // Display update checked notification
                UpdateService.displayResultNotification(context, result);
            }
        };
        task.execute();
    }

    /**
     * Check for updates of hosts sources.
     *
     * @param context The application context.
     * @return return code (from {@link StatusCodes}).
     */
    @NonNull
    private static UpdateResult checkForUpdates(Context context) {
        long currentLastModifiedLocal;
        long currentLastModifiedOnline;
        boolean updateAvailable = false;

        // Declare update result
        UpdateResult updateResult = new UpdateResult();
        // Check current connection
        if (!Utils.isAndroidOnline(context)) {
            updateResult.mCode = StatusCodes.NO_CONNECTION;
            return updateResult;
        }

        // Get cursor over all enabled hosts sources
        Cursor enabledHostsSourcesCursor = ProviderHelper.getEnabledHostsSourcesCursor(context);
        // Iterate over all hosts sources in db with cursor
        if (enabledHostsSourcesCursor != null && enabledHostsSourcesCursor.moveToFirst()) {
            do {
                // Increase number of downloads

                updateResult.mNumberOfDownloads++;

                // Get URL and lastModified from db
                String currentUrl = enabledHostsSourcesCursor
                        .getString(enabledHostsSourcesCursor.getColumnIndex("url"));
                currentLastModifiedLocal = enabledHostsSourcesCursor
                        .getLong(enabledHostsSourcesCursor
                                .getColumnIndex("last_modified_local"));

                try {
                    Log.v(Constants.TAG, "Checking hosts file: " + currentUrl);

                        /* build connection */
                    URL mURL = new URL(currentUrl);
                    URLConnection connection = mURL.openConnection();
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(30000);

                    currentLastModifiedOnline = connection.getLastModified();

                    Log.d(Constants.TAG,
                            "mConnectionLastModified: "
                                    + currentLastModifiedOnline
                                    + " ("
                                    + DateUtils.longToDateString(context,
                                    currentLastModifiedOnline) + ")"
                    );

                    Log.d(Constants.TAG,
                            "mCurrentLastModified: "
                                    + currentLastModifiedLocal
                                    + " ("
                                    + DateUtils.longToDateString(context,
                                    currentLastModifiedLocal) + ")"
                    );

                    // Check if file is available
                    connection.connect();
                    connection.getInputStream();

                    // Check if update is available for this hosts file
                    if (currentLastModifiedOnline > currentLastModifiedLocal) {
                        updateAvailable = true;
                    }

                    // Save last modified online for later viewing in list
                    ProviderHelper.updateHostsSourceLastModifiedOnline(context,
                            enabledHostsSourcesCursor.getInt(enabledHostsSourcesCursor
                                    .getColumnIndex(HostsSources._ID)),
                            currentLastModifiedOnline
                    );

                } catch (Exception exception) {
                    Log.e(Constants.TAG, "Exception while downloading from " + currentUrl, exception);

                    // Increase number of failed download
                    updateResult.mNumberOfFailedDownloads++;

                    // Set last_modified_online of failed download to 0 (not available)
                    ProviderHelper.updateHostsSourceLastModifiedOnline(context,
                            enabledHostsSourcesCursor.getInt(enabledHostsSourcesCursor
                                    .getColumnIndex(HostsSources._ID)), 0
                    );
                }

            } while (enabledHostsSourcesCursor.moveToNext());
        }
        // Close cursor in the end
        if (enabledHostsSourcesCursor != null && !enabledHostsSourcesCursor.isClosed()) {
            enabledHostsSourcesCursor.close();
        }
        // Check if all downloads failed
        if (updateResult.mNumberOfDownloads == updateResult.mNumberOfFailedDownloads &&
                updateResult.mNumberOfDownloads != 0) {
            // Return download fails
            updateResult.mCode = StatusCodes.DOWNLOAD_FAIL;
            return updateResult;
        }
        // Check if update is available
        if (updateAvailable) {
            updateResult.mCode = StatusCodes.UPDATE_AVAILABLE;
        }
        // Otherwise, return current status
        else if (ApplyUtils.isHostsFileCorrect(Constants.ANDROID_SYSTEM_ETC_HOSTS)) {
            updateResult.mCode = StatusCodes.ENABLED;
        } else {
            updateResult.mCode = StatusCodes.DISABLED;
        }
        Log.d(Constants.TAG, "Update check result: " + updateResult);
        return updateResult;
    }

    /**
     * Show permanent notification while executing {@link #checkForUpdates(Context)}.
     *
     * @param context The application context.
     */
    private static void showUpdateNotification(Context context) {
        int icon = R.drawable.status_bar_icon;
        CharSequence tickerText = context.getString(R.string.app_name) + ": "
                + context.getString(R.string.status_checking);
        long when = System.currentTimeMillis();


        CharSequence contentTitle = context.getString(R.string.app_name) + ": "
                + context.getString(R.string.status_checking);
        CharSequence contentText = context.getString(R.string.status_checking_subtitle);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
                .setSmallIcon(icon).setContentTitle(contentTitle).setTicker(tickerText)
                .setWhen(when).setOngoing(true).setContentText(contentText);

        Intent notificationIntent = new Intent(context, BaseActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);

        mBuilder.setContentIntent(contentIntent);

        // Get system notification manager
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.notify(UpdateService.UPDATE_NOTIFICATION_ID, mBuilder.build());
    }

    /**
     * Cancel notification.
     *
     * @param context The application context.
     */
    private static void cancelUpdateNotification(Context context) {
        // Get system notification manager
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        // Cancel notification
        notificationManager.cancel(UpdateService.UPDATE_NOTIFICATION_ID);
    }

    /**
     * Display result notification.
     *
     * @param context The application context.
     * @param result  The update result.
     */
    private static void displayResultNotification(Context context, UpdateResult result) {
        // Display update checked notification
        String successfulDownloads = (result.mNumberOfDownloads - result.mNumberOfFailedDownloads)
                + "/" + result.mNumberOfDownloads;
        ResultHelper.showNotificationBasedOnResult(context, result.mCode, successfulDownloads);
    }

    @NonNull
    @Override
    protected Result onRunJob(Params params) {
        // Retrieve application context
        Context context = getContext();
        // Check for update
        UpdateResult result = UpdateService.checkForUpdates(context);
        // Display result notification
        UpdateService.displayResultNotification(context, result);
        // Check if no connection
        if (result.mCode == StatusCodes.NO_CONNECTION) {
            // Reschedule the job
            return Result.RESCHEDULE;
        }
        // Check if update available and automatic update enabled
        else if (result.mCode == StatusCodes.UPDATE_AVAILABLE && PreferenceHelper.getAutomaticUpdateDaily(context)) {
            // Download and apply update
            new ApplyHelper(context).apply();
        }
        // Return job is successful
        return Result.SUCCESS;
    }

    /**
     * This class is a job creator for update jobs.
     *
     * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
     */
    public static class UpdateJobCreator implements JobCreator {
        @Override
        public Job create(String tag) {
            switch (tag) {
                case JOB_TAG:
                    return new UpdateService();
                default:
                    return null;
            }
        }
    }

    /**
     * This class represents the update result.
     *
     * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
     */
    private static class UpdateResult {
        /**
         * The result code (from {@link StatusCodes}).
         */
        private int mCode = StatusCodes.CHECKING;
        /**
         * The number of downloads.
         */
        private int mNumberOfDownloads = 0;
        /**
         * The number of failed downloads.
         */
        private int mNumberOfFailedDownloads = 0;

        @Override
        public String toString() {
            return "UpdateResult{" +
                    "mCode=" + mCode +
                    ", mNumberOfDownloads=" + mNumberOfDownloads +
                    ", mNumberOfFailedDownloads=" + mNumberOfFailedDownloads +
                    '}';
        }
    }
}
