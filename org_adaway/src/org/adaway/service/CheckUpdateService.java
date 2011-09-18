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

import android.app.Service;
import android.app.NotificationManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.AlarmManager;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.SystemClock;
import android.content.Intent;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Calendar;
import java.util.GregorianCalendar;

import org.adaway.R;
import org.adaway.helper.DatabaseHelper;
import org.adaway.helper.PreferencesHelper;
import org.adaway.ui.BaseActivity;
import org.adaway.util.Constants;
import org.adaway.util.ReturnCodes;
import org.adaway.util.StatusUtils;
import org.adaway.util.Utils;

/**
 * CheckUpdateService checks every 24 hours if updates are available
 */
public class CheckUpdateService extends Service {
    private Context mContext;
    private DatabaseHelper mDatabaseHelper;
    private CheckForUpdatesTask mCheckForUpdatesTask;

    // Check interval: every 24 hours
    private static long UPDATES_CHECK_INTERVAL = 24 * 60 * 60 * 1000;

    // Notification id
    private static final int UPDATE_NOTIFICATION_ID = 1;

    /**
     * Start AsyncTask onStart of service
     */
    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);

        mContext = this;
        mCheckForUpdatesTask = new CheckForUpdatesTask();

        // execute task to check for updates
        mCheckForUpdatesTask.execute();
    }

    /**
     * Stop AsyncTask onDestroy on service
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mCheckForUpdatesTask != null && !mCheckForUpdatesTask.isCancelled()) {
            // cancel updates task
            mCheckForUpdatesTask.cancel(true);
        }
    }

    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Schedule service for daily execution with AlarmManager
     * 
     * @param context
     */
    public static void schedule(Context context) {
        // schedule when enabled in preferences
        if (PreferencesHelper.getUpdateCheckDaily(context)) {
            final Intent intent = new Intent(context, CheckUpdateService.class);
            final PendingIntent pending = PendingIntent.getService(context, 0, intent, 0);

            // every day at 10 am
            Calendar c = new GregorianCalendar();
            c.add(Calendar.DAY_OF_YEAR, 1);
            c.set(Calendar.HOUR_OF_DAY, 10);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);

            final AlarmManager alarm = (AlarmManager) context
                    .getSystemService(Context.ALARM_SERVICE);
            alarm.cancel(pending);
            if (Constants.DEBUG_MODE) {
                alarm.setRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime(),
                        30 * 1000, pending);
            } else {
                alarm.setRepeating(AlarmManager.RTC, c.getTimeInMillis(), UPDATES_CHECK_INTERVAL,
                        pending);
            }
        }
    }

    /**
     * Cancel scheduled service via alarm manager
     * 
     * @param context
     */
    public static void unschedule(Context context) {
        final Intent intent = new Intent(context, CheckUpdateService.class);
        final PendingIntent pending = PendingIntent.getService(context, 0, intent, 0);

        final AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarm.cancel(pending);
    }

    /**
     * AsyncTask to check for updates and show notifications based on result
     */
    private class CheckForUpdatesTask extends AsyncTask<Void, Integer, Integer> {
        Cursor mEnabledHostsSourcesCursor;

        private String mCurrentUrl;
        private long mCurrentLastModifiedLocal;
        private long mCurrentLastModifiedOnline;
        private boolean mUpdateAvailable = false;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showPreNotification();
        }

        @Override
        protected Integer doInBackground(Void... unused) {
            int returnCode = ReturnCodes.ENABLED; // default return code

            if (Utils.isAndroidOnline(mContext)) {

                // get cursor over all enabled hosts source
                mDatabaseHelper = new DatabaseHelper(mContext);
                mEnabledHostsSourcesCursor = mDatabaseHelper.getEnabledHostsSourcesCursor();

                // iterate over all hosts sources in db with cursor
                if (mEnabledHostsSourcesCursor.moveToFirst()) {
                    do {
                        // get url and lastModified from db
                        mCurrentUrl = mEnabledHostsSourcesCursor
                                .getString(mEnabledHostsSourcesCursor.getColumnIndex("url"));
                        mCurrentLastModifiedLocal = mEnabledHostsSourcesCursor
                                .getLong(mEnabledHostsSourcesCursor
                                        .getColumnIndex("last_modified_local"));

                        // stop if thread canceled
                        if (isCancelled()) {
                            break;
                        }

                        @SuppressWarnings("unused")
                        InputStream is = null;
                        try {
                            Log.v(Constants.TAG, "Checking hosts file: " + mCurrentUrl);

                            /* build connection */
                            URL mURL = new URL(mCurrentUrl);
                            URLConnection connection = mURL.openConnection();

                            mCurrentLastModifiedOnline = connection.getLastModified();

                            Log.d(Constants.TAG,
                                    "mConnectionLastModified: "
                                            + mCurrentLastModifiedOnline
                                            + " ("
                                            + StatusUtils
                                                    .longToDateString(mCurrentLastModifiedOnline)
                                            + ")");

                            Log.d(Constants.TAG,
                                    "mCurrentLastModified: "
                                            + mCurrentLastModifiedLocal
                                            + " ("
                                            + StatusUtils
                                                    .longToDateString(mCurrentLastModifiedLocal)
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

        @Override
        protected void onPostExecute(Integer result) {
            super.onPostExecute(result);

            Log.d(Constants.TAG, "onPostExecute result: " + result);

            switch (result) {
            case ReturnCodes.UPDATE_AVAILABLE:
                showPostNotification(getString(R.string.status_update_available),
                        getString(R.string.status_update_available_subtitle));
                break;
            // case ReturnCodes.DISABLED:
            // cancelNotification();
            // break;
            case ReturnCodes.DOWNLOAD_FAIL:
                showPostNotification(getString(R.string.status_download_fail),
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
    }

    /**
     * Show permanent notification while executing AsyncTask to check for updates
     */
    private void showPreNotification() {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        int icon = R.drawable.status_bar_icon;
        CharSequence tickerText = getString(R.string.status_checking);
        long when = System.currentTimeMillis();

        Notification notification = new Notification(icon, tickerText, when);
        notification.flags |= Notification.FLAG_ONGOING_EVENT;

        Context context = getApplicationContext();
        CharSequence contentTitle = getString(R.string.status_checking);
        CharSequence contentText = getString(R.string.status_checking_subtitle);
        Intent notificationIntent = new Intent(this, BaseActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);

        mNotificationManager.notify(UPDATE_NOTIFICATION_ID, notification);
    }

    /**
     * Show notification with result defined in params, after executing of AsyncTask
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

        mNotificationManager.notify(UPDATE_NOTIFICATION_ID, notification);
    }

    /**
     * Cancel Notification
     */
    private void cancelNotification() {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(UPDATE_NOTIFICATION_ID);
    }

}
