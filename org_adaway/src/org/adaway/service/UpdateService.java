package org.adaway.service;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import org.adaway.R;
import org.adaway.helper.PreferencesHelper;
import org.adaway.provider.ProviderHelper;
import org.adaway.provider.AdAwayContract.HostsSources;
import org.adaway.ui.BaseActivity;
import org.adaway.util.ApplyUtils;
import org.adaway.util.Constants;
import org.adaway.util.ReturnCodes;
import org.adaway.util.StatusUtils;
import org.adaway.util.Utils;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import com.commonsware.cwac.wakeful.WakefulIntentService;

/**
 * CheckUpdateService checks every 24 hours at about 9 am for updates of hosts sources, see
 * UpdateListener for scheduling
 */
public class UpdateService extends WakefulIntentService {

    // Intent extras to define whether to apply after checking or not
    public static final String EXTRA_APPLY_AFTER_CHECK = "org.adaway.APPLY_AFTER_CHECK";

    private boolean mApplyAfterChecking;

    private Context mService;

    Cursor mEnabledHostsSourcesCursor;

    private String mCurrentUrl;
    private long mCurrentLastModifiedLocal;
    private long mCurrentLastModifiedOnline;
    private boolean mUpdateAvailable = false;

    // Notification id
    private static final int UPDATE_CHECK_NOTIFICATION_ID = 1;

    public UpdateService() {
        super("AdAwayUpdateService");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mService = this;

        // get from intent extras if UpdateService should apply after checking if enabled in
        // preferences
        mApplyAfterChecking = false;
        Bundle extras = intent.getExtras();
        if (extras != null) {
            if (extras.containsKey(EXTRA_APPLY_AFTER_CHECK)) {
                if (PreferencesHelper.getAutomaticUpdateDaily(mService)) {
                    mApplyAfterChecking = extras.getBoolean(EXTRA_APPLY_AFTER_CHECK);
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
            showPreNotification();
        }

        BaseActivity.updateStatusIconAndTextAndSubtitle(mService, ReturnCodes.CHECKING,
                mService.getString(R.string.status_checking),
                mService.getString(R.string.status_checking_subtitle));

        int result = checkForUpdates();

        Log.d(Constants.TAG, "Update Check result: " + result);

        switch (result) {
        case ReturnCodes.UPDATE_AVAILABLE:
            cancelNotification();

            // if automatic updating is enabled in preferences, do it!
            if (mApplyAfterChecking) {

                // download and apply!
                WakefulIntentService.sendWakefulWork(mService, ApplyService.class);
            } else {
                if (!Utils.isInForeground(mService)) {
                    showPostNotification(getString(R.string.app_name) + ": "
                            + getString(R.string.status_update_available),
                            getString(R.string.status_update_available_subtitle));
                }

                BaseActivity.updateStatusIconAndTextAndSubtitle(mService,
                        ReturnCodes.UPDATE_AVAILABLE,
                        mService.getString(R.string.status_update_available),
                        mService.getString(R.string.status_update_available_subtitle));
            }
            break;
        case ReturnCodes.DOWNLOAD_FAIL:
            if (!Utils.isInForeground(mService)) {
                showPostNotification(getString(R.string.app_name) + ": "
                        + getString(R.string.status_download_fail),
                        getString(R.string.status_download_fail_subtitle) + " " + mCurrentUrl);
            }

            BaseActivity.updateStatusIconAndTextAndSubtitle(mService, ReturnCodes.DOWNLOAD_FAIL,
                    mService.getString(R.string.status_download_fail),
                    mService.getString(R.string.status_download_fail_subtitle) + " " + mCurrentUrl);
            break;
        case ReturnCodes.NO_CONNECTION:
            cancelNotification();

            BaseActivity.updateStatusIconAndTextAndSubtitle(mService, ReturnCodes.DOWNLOAD_FAIL,
                    mService.getString(R.string.status_no_connection),
                    mService.getString(R.string.status_no_connection_subtitle));
            break;
        case ReturnCodes.ENABLED:
            BaseActivity.updateStatusEnabled(mService);
            break;
        case ReturnCodes.DISABLED:
            BaseActivity.updateStatusDisabled(mService);
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

        if (Utils.isAndroidOnline(mService)) {

            // get cursor over all enabled hosts source
            mEnabledHostsSourcesCursor = ProviderHelper.getEnabledHostsSourcesCursor(mService);

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
                        ProviderHelper.updateHostsSourceLastModifiedOnline(mService,
                                mEnabledHostsSourcesCursor.getInt(mEnabledHostsSourcesCursor
                                        .getColumnIndex(HostsSources._ID)),
                                mCurrentLastModifiedOnline);

                    } catch (Exception e) {
                        Log.e(Constants.TAG, "Exception: " + e);
                        returnCode = ReturnCodes.DOWNLOAD_FAIL;
                        break; // stop for-loop
                    }

                } while (mEnabledHostsSourcesCursor.moveToNext());
            }

            // close cursor in the end
            if (mEnabledHostsSourcesCursor != null && !mEnabledHostsSourcesCursor.isClosed()) {
                mEnabledHostsSourcesCursor.close();
            }
        } else {
            returnCode = ReturnCodes.NO_CONNECTION;
        }

        // set return code if update is available
        if (mUpdateAvailable) {
            returnCode = ReturnCodes.UPDATE_AVAILABLE;
        }

        // check if hosts file is applied
        if (!ApplyUtils.isHostsFileCorrect(mService, Constants.ANDROID_SYSTEM_ETC_HOSTS)) {
            returnCode = ReturnCodes.DISABLED;
        }

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