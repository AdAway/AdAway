/*
 * Copyright (C) 2011 Dominik Schürmann <dominik@dominikschuermann.de>
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

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import org.adaway.R;
import org.adaway.helper.PreferencesHelper;
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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.PowerManager;

import com.commonsware.cwac.wakeful.WakefulIntentService;

/**
 * CheckUpdateService checks every 24 hours at about 9 am for updates of hosts sources, see
 * UpdateListener for scheduling
 */
public class UpdateService extends WakefulIntentService {
    // Intent extras to define whether to apply after checking or not
    public static final String EXTRA_APPLY_AFTER_CHECK = "org.adaway.APPLY_AFTER_CHECK";

    private Context mService;
    private NotificationManager mNotificationManager;

    private boolean mApplyAfterCheck;

    private String mCurrentUrl;

    private static final int UPDATE_NOTIFICATION_ID = 10;

    public UpdateService() {
        super("AdAwayUpdateService");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mService = this;

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // get from intent extras if UpdateService should apply after checking if enabled in
        // preferences
        mApplyAfterCheck = false;
        Bundle extras = intent.getExtras();
        if (extras != null) {
            if (extras.containsKey(EXTRA_APPLY_AFTER_CHECK)) {
                if (PreferencesHelper.getAutomaticUpdateDaily(mService)) {
                    mApplyAfterCheck = extras.getBoolean(EXTRA_APPLY_AFTER_CHECK);
                }
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
            ResultHelper.showNotificationBasedOnResult(mService, result, mCurrentUrl);
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

            // get cursor over all enabled hosts source
            enabledHostsSourcesCursor = ProviderHelper.getEnabledHostsSourcesCursor(mService);

            // iterate over all hosts sources in db with cursor
            if (enabledHostsSourcesCursor.moveToFirst()) {
                do {

                    // get url and lastModified from db
                    mCurrentUrl = enabledHostsSourcesCursor.getString(enabledHostsSourcesCursor
                            .getColumnIndex("url"));
                    currentLastModifiedLocal = enabledHostsSourcesCursor
                            .getLong(enabledHostsSourcesCursor
                                    .getColumnIndex("last_modified_local"));

                    try {
                        Log.v(Constants.TAG, "Checking hosts file: " + mCurrentUrl);

                        /* build connection */
                        URL mURL = new URL(mCurrentUrl);
                        URLConnection connection = mURL.openConnection();

                        currentLastModifiedOnline = connection.getLastModified();

                        Log.d(Constants.TAG,
                                "mConnectionLastModified: " + currentLastModifiedOnline + " ("
                                        + DateUtils.longToDateString(currentLastModifiedOnline)
                                        + ")");

                        Log.d(Constants.TAG, "mCurrentLastModified: " + currentLastModifiedLocal
                                + " (" + DateUtils.longToDateString(currentLastModifiedLocal) + ")");

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
                                currentLastModifiedOnline);

                    } catch (Exception e) {
                        Log.e(Constants.TAG, "Exception: " + e);
                        returnCode = StatusCodes.DOWNLOAD_FAIL;
                        break; // stop for-loop
                    }

                } while (enabledHostsSourcesCursor.moveToNext());
            }

            // close cursor in the end
            if (enabledHostsSourcesCursor != null && !enabledHostsSourcesCursor.isClosed()) {
                enabledHostsSourcesCursor.close();
            }
        } else {
            returnCode = StatusCodes.NO_CONNECTION;
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

        Notification notification = new Notification(icon, tickerText, when);
        notification.flags = Notification.FLAG_ONGOING_EVENT;

        Context context = getApplicationContext();
        CharSequence contentTitle = getString(R.string.app_name) + ": "
                + getString(R.string.status_checking);
        CharSequence contentText = getString(R.string.status_checking_subtitle);
        Intent notificationIntent = new Intent(this, BaseActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);

        mNotificationManager.notify(UPDATE_NOTIFICATION_ID, notification);
    }

    /**
     * Cancel Notification
     */
    private void cancelUpdateNotification() {
        mNotificationManager.cancel(UPDATE_NOTIFICATION_ID);
    }

}