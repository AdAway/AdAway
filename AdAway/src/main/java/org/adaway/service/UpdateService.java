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

import java.net.URL;
import java.net.URLConnection;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.helper.ResultHelper;
import org.adaway.provider.ProviderHelper;
import org.adaway.provider.AdAwayContract.HostsSources;
import org.adaway.ui.BaseActivity;
import org.adaway.util.ApplyUtils;
import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.adaway.util.StatusCodes;
import org.adaway.util.DateUtils;
import org.adaway.util.Utils;

import android.support.v4.app.NotificationCompat;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;

import com.commonsware.cwac.wakeful.WakefulIntentService;

/**
 * CheckUpdateService checks every 24 hours at about 9 am for updates of hosts sources, see
 * UpdateListener for scheduling
 */
public class UpdateService extends WakefulIntentService {
    // Intent extras to define whether to apply after checking or not
    public static final String EXTRA_BACKGROUND_EXECUTION = "org.adaway.BACKGROUND_EXECUTION";

    private Context mService;
    private NotificationManager mNotificationManager;

    private boolean mApplyAfterCheck;
    private boolean mBackgroundExecution;

    private int mNumberOfFailedDownloads;
    private int mNumberOfDownloads;

    private static final int UPDATE_NOTIFICATION_ID = 10;

    public UpdateService() {
        super("AdAwayUpdateService");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mService = this;

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // get from intent extras if this service is executed in the background
        mBackgroundExecution = false;
        Bundle extras = intent.getExtras();
        if (extras != null) {
            if (extras.containsKey(EXTRA_BACKGROUND_EXECUTION)) {
                mBackgroundExecution = extras.getBoolean(EXTRA_BACKGROUND_EXECUTION);
            }
        }

        // UpdateService should apply after checking if enabled in preferences
        mApplyAfterCheck = false;
        if (mBackgroundExecution) {
            if (PreferenceHelper.getAutomaticUpdateDaily(mService)) {
                mApplyAfterCheck = extras.getBoolean(EXTRA_BACKGROUND_EXECUTION);
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * Asynchronous background operations of service, with wakelock
     */
    @Override
    public void doWakefulWork(Intent intent) {
        if (!Utils.isInForeground(mService)) {
            showUpdateNotification();
        }

        BaseActivity.setStatusBroadcast(mService, mService.getString(R.string.status_checking),
                mService.getString(R.string.status_checking_subtitle), StatusCodes.CHECKING);

        int result = checkForUpdates();

        Log.d(Constants.TAG, "Update Check result: " + result);

        cancelUpdateNotification();

        // If this is run from background and should update after checking...
        if (result == StatusCodes.UPDATE_AVAILABLE && mApplyAfterCheck) {
            // download and apply!
            WakefulIntentService.sendWakefulWork(mService, ApplyService.class);
        } else {
            String successfulDownloads = (mNumberOfDownloads - mNumberOfFailedDownloads) + "/"
                    + mNumberOfDownloads;

            ResultHelper.showNotificationBasedOnResult(mService, result, successfulDownloads);
        }
    }

    /**
     * Check for updates of hosts sources
     *
     * @return return code
     */
    private int checkForUpdates() {
        Cursor enabledHostsSourcesCursor;
        long currentLastModifiedLocal;
        long currentLastModifiedOnline;
        boolean updateAvailable = false;

        int returnCode = StatusCodes.ENABLED; // default return code

        if (Utils.isAndroidOnline(mService)) {

            mNumberOfFailedDownloads = 0;
            mNumberOfDownloads = 0;

            // get cursor over all enabled hosts source
            enabledHostsSourcesCursor = ProviderHelper.getEnabledHostsSourcesCursor(mService);

            // iterate over all hosts sources in db with cursor
            if (enabledHostsSourcesCursor != null && enabledHostsSourcesCursor.moveToFirst()) {
                do {

                    mNumberOfDownloads++;

                    // get url and lastModified from db
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
                                        + DateUtils.longToDateString(mService,
                                        currentLastModifiedOnline) + ")"
                        );

                        Log.d(Constants.TAG,
                                "mCurrentLastModified: "
                                        + currentLastModifiedLocal
                                        + " ("
                                        + DateUtils.longToDateString(mService,
                                        currentLastModifiedLocal) + ")"
                        );

                        // check if file is available
                        connection.connect();
                        connection.getInputStream();

                        // check if update available for this hosts file
                        if (currentLastModifiedOnline > currentLastModifiedLocal) {
                            updateAvailable = true;
                        }

                        // save last modified online for later viewing in list
                        ProviderHelper.updateHostsSourceLastModifiedOnline(mService,
                                enabledHostsSourcesCursor.getInt(enabledHostsSourcesCursor
                                        .getColumnIndex(HostsSources._ID)),
                                currentLastModifiedOnline
                        );

                    } catch (Exception e) {
                        Log.e(Constants.TAG, "Exception while downloading from " + currentUrl, e);

                        mNumberOfFailedDownloads++;

                        // set last_modified_online of failed download to 0 (not available)
                        ProviderHelper.updateHostsSourceLastModifiedOnline(mService,
                                enabledHostsSourcesCursor.getInt(enabledHostsSourcesCursor
                                        .getColumnIndex(HostsSources._ID)), 0
                        );
                    }

                } while (enabledHostsSourcesCursor.moveToNext());
            }

            // close cursor in the end
            if (enabledHostsSourcesCursor != null && !enabledHostsSourcesCursor.isClosed()) {
                enabledHostsSourcesCursor.close();
            }

            // if all downloads failed return download_fail error
            if (mNumberOfDownloads == mNumberOfFailedDownloads && mNumberOfDownloads != 0) {
                returnCode = StatusCodes.DOWNLOAD_FAIL;
            }
        } else {
            // only report no connection when not in background
            if (!mBackgroundExecution) {
                returnCode = StatusCodes.NO_CONNECTION;
            } else {
                Log.e(Constants.TAG,
                        "Should not happen! In background execution is no connection available!");
            }
        }

        // set return code if update is available
        if (updateAvailable) {
            returnCode = StatusCodes.UPDATE_AVAILABLE;
        }

        // check if hosts file is applied
        if (!ApplyUtils.isHostsFileCorrect(mService, Constants.ANDROID_SYSTEM_ETC_HOSTS)) {
            returnCode = StatusCodes.DISABLED;
        }

        return returnCode;
    }

    /**
     * Show permanent notification while executing checkForUpdates
     */
    private void showUpdateNotification() {
        int icon = R.drawable.status_bar_icon;
        CharSequence tickerText = getString(R.string.app_name) + ": "
                + getString(R.string.status_checking);
        long when = System.currentTimeMillis();

        Context context = getApplicationContext();
        CharSequence contentTitle = getString(R.string.app_name) + ": "
                + getString(R.string.status_checking);
        CharSequence contentText = getString(R.string.status_checking_subtitle);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
            .setSmallIcon(icon).setContentTitle(contentTitle).setTicker(tickerText)
                .setWhen(when).setOngoing(true).setContentText(contentText);

        Intent notificationIntent = new Intent(this, BaseActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        mBuilder.setContentIntent(contentIntent);

        mNotificationManager.notify(UPDATE_NOTIFICATION_ID, mBuilder.build());
    }

    /**
     * Cancel Notification
     */
    private void cancelUpdateNotification() {
        mNotificationManager.cancel(UPDATE_NOTIFICATION_ID);
    }

}
