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

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.AlarmManager;
import android.os.SystemClock;
import android.content.Intent;
import android.content.Context;
import android.database.Cursor;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Calendar;

import org.adaway.R;
import org.adaway.helper.DatabaseHelper;
import org.adaway.helper.PreferencesHelper;
import org.adaway.ui.BaseActivity;
import org.adaway.util.Constants;
import org.adaway.util.ReturnCodes;
import org.adaway.util.StatusUtils;
import org.adaway.util.Utils;
import org.adaway.util.Log;

/**
 * CheckUpdateService checks every 24 hours at about 9 am for updates of hosts sources
 */
public class UpdateCheckService extends IntentService {
    private Context mApplicationContext;
    private DatabaseHelper mDatabaseHelper;

    Cursor mEnabledHostsSourcesCursor;

    private String mCurrentUrl;
    private long mCurrentLastModifiedLocal;
    private long mCurrentLastModifiedOnline;
    private boolean mUpdateAvailable = false;

    // Notification id
    private static final int UPDATE_CHECK_NOTIFICATION_ID = 1;

    public UpdateCheckService() {
        super("AdAwayUpdateCheckService");
    }

    /**
     * Sets repeating alarm to execute service daily using AlarmManager
     * 
     * @param context
     */
    public static void registerAlarm(Context context) {
        // register when enabled in preferences
        if (PreferencesHelper.getUpdateCheckDaily(context)) {
            Intent intent = new Intent(context, UpdateCheckAlarmReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent,
                    PendingIntent.FLAG_CANCEL_CURRENT);

            // every day at 9 am
            Calendar calendar = Calendar.getInstance();
            // if it's after or equal 9 am schedule for next day
            if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= 9) {
                calendar.add(Calendar.DAY_OF_YEAR, 1); // add, not set!
            }
            calendar.set(Calendar.HOUR_OF_DAY, 9);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);

            final AlarmManager alarm = (AlarmManager) context
                    .getSystemService(Context.ALARM_SERVICE);

            if (Constants.DEBUG_UPDATE_CHECK_SERVICE) {
                // for debugging execute service every 30 seconds
                alarm.setRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime(),
                        30 * 1000, pendingIntent);
            } else {
                alarm.setRepeating(AlarmManager.RTC, calendar.getTimeInMillis(),
                        AlarmManager.INTERVAL_DAY, pendingIntent);
            }
        }
    }

    /**
     * Cancel alarm for service using AlarmManager
     * 
     * @param context
     */
    public static void unregisterAlarm(Context context) {
        Intent intent = new Intent(context, UpdateCheckAlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);

        final AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarm.cancel(pendingIntent);
    }

    /**
     * Asynchronous background operations of service
     */
    @Override
    public void onHandleIntent(Intent intent) {
        mApplicationContext = getApplicationContext();

        showPreNotification();

        int result = checkForUpdates();

        Log.d(Constants.TAG, "result: " + result);

        switch (result) {
        case ReturnCodes.UPDATE_AVAILABLE:
            showPostNotification(getString(R.string.app_name) + ": "
                    + getString(R.string.status_update_available),
                    getString(R.string.status_update_available_subtitle));
            break;
        // case ReturnCodes.DISABLED:
        // cancelNotification();
        // break;
        case ReturnCodes.DOWNLOAD_FAIL:
            showPostNotification(getString(R.string.app_name) + ": "
                    + getString(R.string.status_download_fail),
                    getString(R.string.status_download_fail_subtitle) + " " + mCurrentUrl);
            break;
        case ReturnCodes.NO_CONNECTION:
            cancelNotification();
            break;
        case ReturnCodes.ENABLED:
            cancelNotification();
            break;
        }
    }

    /**
     * Check for updates of hosts sources
     * 
     * @return return code
     */
    private int checkForUpdates() {
        int returnCode = ReturnCodes.ENABLED; // default return code

        if (Utils.isAndroidOnline(mApplicationContext)) {

            // get cursor over all enabled hosts source
            mDatabaseHelper = new DatabaseHelper(mApplicationContext);
            mEnabledHostsSourcesCursor = mDatabaseHelper.getEnabledHostsSourcesCursor();

            // iterate over all hosts sources in db with cursor
            if (mEnabledHostsSourcesCursor.moveToFirst()) {
                do {
                    // get url and lastModified from db
                    mCurrentUrl = mEnabledHostsSourcesCursor.getString(mEnabledHostsSourcesCursor
                            .getColumnIndex("url"));
                    mCurrentLastModifiedLocal = mEnabledHostsSourcesCursor
                            .getLong(mEnabledHostsSourcesCursor
                                    .getColumnIndex("last_modified_local"));

                    @SuppressWarnings("unused")
                    InputStream is = null;
                    try {
                        Log.v(Constants.TAG, "Checking hosts file: " + mCurrentUrl);

                        /* build connection */
                        URL mURL = new URL(mCurrentUrl);
                        URLConnection connection = mURL.openConnection();

                        mCurrentLastModifiedOnline = connection.getLastModified();

                        Log.d(Constants.TAG,
                                "mConnectionLastModified: " + mCurrentLastModifiedOnline + " ("
                                        + StatusUtils.longToDateString(mCurrentLastModifiedOnline)
                                        + ")");

                        Log.d(Constants.TAG, "mCurrentLastModified: " + mCurrentLastModifiedLocal
                                + " (" + StatusUtils.longToDateString(mCurrentLastModifiedLocal)
                                + ")");

                        // check if file is available
                        connection.connect();
                        is = connection.getInputStream();

                        // check if update available for this hosts file
                        if (mCurrentLastModifiedOnline > mCurrentLastModifiedLocal) {
                            mUpdateAvailable = true;
                        }

                        // save last modified online for later viewing in list
                        mDatabaseHelper.updateHostsSourceLastModifiedOnline(
                                mEnabledHostsSourcesCursor.getInt(mEnabledHostsSourcesCursor
                                        .getColumnIndex("_id")), mCurrentLastModifiedOnline);

                    } catch (Exception e) {
                        Log.e(Constants.TAG, "Exception: " + e);
                        returnCode = ReturnCodes.DOWNLOAD_FAIL;
                        break; // stop for-loop
                    }

                } while (mEnabledHostsSourcesCursor.moveToNext());
            }

            // close cursor and db helper in the end
            if (mEnabledHostsSourcesCursor != null && !mEnabledHostsSourcesCursor.isClosed()) {
                mEnabledHostsSourcesCursor.close();
            }
            mDatabaseHelper.close();

        } else {
            returnCode = ReturnCodes.NO_CONNECTION;
        }

        // set return code if update is available
        if (mUpdateAvailable) {
            returnCode = ReturnCodes.UPDATE_AVAILABLE;
        }

        // check if hosts file is applied
        // if (!ApplyUtils.isHostsFileApplied(mContext, Constants.ANDROID_SYSTEM_ETC_PATH)) {
        // returnCode = ReturnCodes.DISABLED;
        // }

        return returnCode;
    }

    /**
     * Show permanent notification while executing checkForUpdates
     */
    private void showPreNotification() {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        int icon = R.drawable.status_bar_icon;
        CharSequence tickerText = getString(R.string.app_name) + ": "
                + getString(R.string.status_checking);
        long when = System.currentTimeMillis();

        Notification notification = new Notification(icon, tickerText, when);
        notification.flags |= Notification.FLAG_ONGOING_EVENT;

        Context context = getApplicationContext();
        CharSequence contentTitle = getString(R.string.app_name) + ": "
                + getString(R.string.status_checking);
        CharSequence contentText = getString(R.string.status_checking_subtitle);
        Intent notificationIntent = new Intent(this, BaseActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);

        mNotificationManager.notify(UPDATE_CHECK_NOTIFICATION_ID, notification);
    }

    /**
     * Show notification with result defined in params, after executing checkForUpdates
     * 
     * @param contentTitle
     * @param contentText
     */
    private void showPostNotification(CharSequence contentTitle, CharSequence contentText) {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        int icon = R.drawable.status_bar_icon;
        CharSequence tickerText = contentTitle;
        long when = System.currentTimeMillis();

        Notification notification = new Notification(icon, tickerText, when);
        notification.flags |= Notification.FLAG_AUTO_CANCEL;

        Context context = getApplicationContext();
        Intent notificationIntent = new Intent(this, BaseActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);

        mNotificationManager.notify(UPDATE_CHECK_NOTIFICATION_ID, notification);
    }

    /**
     * Cancel Notification
     */
    private void cancelNotification() {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(UPDATE_CHECK_NOTIFICATION_ID);
    }

}
