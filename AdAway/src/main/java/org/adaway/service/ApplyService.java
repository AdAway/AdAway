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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Date;
import java.text.SimpleDateFormat;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.helper.ResultHelper;
import org.adaway.provider.ProviderHelper;
import org.adaway.provider.AdAwayContract.HostsSources;
import org.adaway.ui.BaseActivity;
import org.adaway.util.ApplyUtils;
import org.adaway.util.CommandException;
import org.adaway.util.Constants;
import org.adaway.util.HostsParser;
import org.adaway.util.Log;
import org.adaway.util.NotEnoughSpaceException;
import org.adaway.util.RemountException;
import org.adaway.util.StatusCodes;
import org.adaway.util.Utils;
import org.sufficientlysecure.rootcommands.Shell;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import android.support.v4.app.NotificationCompat;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

public class ApplyService extends WakefulIntentService {
    private Context mService;
    private NotificationManager mNotificationManager;

    private int mNumberOfFailedDownloads;
    private int mNumberOfDownloads;

    private static final int APPLY_NOTIFICATION_ID = 20;

    public ApplyService() {
        super("AdAwayApplyService");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mService = this;

        mNotificationManager = (NotificationManager) mService.getApplicationContext()
                .getSystemService(Context.NOTIFICATION_SERVICE);

        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * Asynchronous background operations of service, with wakelock
     */
    @Override
    public void doWakefulWork(Intent intent) {
        // disable buttons
        BaseActivity.setButtonsDisabledBroadcast(mService, true);

        // download files with download method
        int downloadResult = download();
        Log.d(Constants.TAG, "Download result: " + downloadResult);

        if (downloadResult == StatusCodes.SUCCESS) {
            // Apply files by apply method
            int applyResult = apply();

            cancelApplyNotification();
            // enable buttons
            BaseActivity.setButtonsDisabledBroadcast(mService, false);
            Log.d(Constants.TAG, "Apply result: " + applyResult);

            String successfulDownloads = (mNumberOfDownloads - mNumberOfFailedDownloads) + "/"
                    + mNumberOfDownloads;

            ResultHelper.showNotificationBasedOnResult(mService, applyResult, successfulDownloads);
        } else if (downloadResult == StatusCodes.DOWNLOAD_FAIL) {
            cancelApplyNotification();
            // enable buttons
            BaseActivity.setButtonsDisabledBroadcast(mService, false);
            // extra information is current url, to show it when it fails
            ResultHelper.showNotificationBasedOnResult(mService, downloadResult, null);
        } else {
            cancelApplyNotification();
            // enable buttons
            BaseActivity.setButtonsDisabledBroadcast(mService, false);
            ResultHelper.showNotificationBasedOnResult(mService, downloadResult, null);
        }
    }

    /**
     * Downloads files from hosts sources
     *
     * @return return code
     */
    private int download() {
        Cursor enabledHostsSourcesCursor;

        byte data[];
        int count;
        long currentLastModifiedOnline;

        int returnCode = StatusCodes.SUCCESS; // default return code

        if (Utils.isAndroidOnline(mService)) {

            showApplyNotification(mService, mService.getString(R.string.download_dialog),
                    mService.getString(R.string.download_dialog),
                    mService.getString(R.string.download_dialog));

            // output to write into
            FileOutputStream out = null;

            try {
                out = mService.openFileOutput(Constants.DOWNLOADED_HOSTS_FILENAME,
                        Context.MODE_PRIVATE);

                mNumberOfFailedDownloads = 0;
                mNumberOfDownloads = 0;

                // get cursor over all enabled hosts source
                enabledHostsSourcesCursor = ProviderHelper.getEnabledHostsSourcesCursor(mService);

                // iterate over all hosts sources in db with cursor
                if (enabledHostsSourcesCursor.moveToFirst()) {
                    do {

                        mNumberOfDownloads++;

                        InputStream is = null;
                        BufferedInputStream bis = null;
                        String currentUrl = enabledHostsSourcesCursor
                                .getString(enabledHostsSourcesCursor.getColumnIndex("url"));

                        try {
                            Log.v(Constants.TAG, "Downloading hosts file: " + currentUrl);

                            /* change URL in download dialog */
                            updateApplyNotification(mService,
                                    mService.getString(R.string.download_dialog), currentUrl);

                            /* build connection */
                            URL mURL = new URL(currentUrl);
                            URLConnection connection = mURL.openConnection();
                            connection.setConnectTimeout(15000);
                            connection.setReadTimeout(30000);

                            /* connect */
                            connection.connect();
                            is = connection.getInputStream();

                            bis = new BufferedInputStream(is);
                            if (is == null) {
                                Log.e(Constants.TAG, "Stream is null");
                            }

                            /* download with progress */
                            data = new byte[1024];
                            count = 0;

                            // run while only when thread is not cancelled
                            while ((count = bis.read(data)) != -1) {
                                out.write(data, 0, count);
                            }

                            // add line seperator to add files together in one file
                            out.write(Constants.LINE_SEPERATOR.getBytes());

                            // save last modified online for later use
                            currentLastModifiedOnline = connection.getLastModified();

                            ProviderHelper.updateHostsSourceLastModifiedOnline(mService,
                                    enabledHostsSourcesCursor.getInt(enabledHostsSourcesCursor
                                            .getColumnIndex(HostsSources._ID)),
                                    currentLastModifiedOnline
                            );

                        } catch (IOException e) {
                            Log.e(Constants.TAG, "Exception while downloading from " + currentUrl,
                                    e);

                            mNumberOfFailedDownloads++;

                            // set last_modified_online of failed download to 0 (not available)
                            ProviderHelper.updateHostsSourceLastModifiedOnline(mService,
                                    enabledHostsSourcesCursor.getInt(enabledHostsSourcesCursor
                                            .getColumnIndex(HostsSources._ID)), 0
                            );
                        } finally {
                            // flush and close streams
                            try {
                                if (out != null) {
                                    out.flush();
                                }
                                if (bis != null) {
                                    bis.close();
                                }
                                if (is != null) {
                                    is.close();
                                }
                            } catch (Exception e) {
                                Log.e(Constants.TAG, "Exception on flush and closing streams.", e);
                            }
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
            } catch (Exception e) {
                Log.e(Constants.TAG, "Private File can not be created, Exception: " + e);
                returnCode = StatusCodes.PRIVATE_FILE_FAIL;
            } finally {
                try {
                    if (out != null) {
                        out.close();
                    }
                } catch (Exception e) {
                    Log.e(Constants.TAG, "Exception on close of out.", e);
                }
            }
        } else {
            returnCode = StatusCodes.NO_CONNECTION;
        }

        return returnCode;
    }

    /**
     * Apply hosts file
     *
     * @return return code
     */
    int apply() {
        showApplyNotification(mService, mService.getString(R.string.apply_dialog),
                mService.getString(R.string.apply_dialog),
                mService.getString(R.string.apply_dialog_hostnames));

        int returnCode = StatusCodes.SUCCESS; // default return code
        BufferedOutputStream bos = null;

        try {
            /* PARSE: parse hosts files to sets of hostnames and comments */

            FileInputStream fis = mService.openFileInput(Constants.DOWNLOADED_HOSTS_FILENAME);

            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));

            // Use whitelist and/or redirection rules from hosts sources only if enabled in preferences
            HostsParser parser = new HostsParser(reader, PreferenceHelper.getWhitelistRules(mService), PreferenceHelper.getRedirectionRules(mService));

            fis.close();

            updateApplyNotification(mService, mService.getString(R.string.apply_dialog),
                    mService.getString(R.string.apply_dialog_lists));

            /* READ DATABSE CONTENT */

            // add whitelist from db
            parser.addWhitelist(ProviderHelper.getEnabledWhitelistHashSet(mService));
            // add blacklist from db
            parser.addBlacklist(ProviderHelper.getEnabledBlacklistHashSet(mService));
            // add redirection list from db
            parser.addRedirectionList(ProviderHelper.getEnabledRedirectionListHashMap(mService));

            // get hosts sources list from db
            ArrayList<String> enabledHostsSources = ProviderHelper
                    .getEnabledHostsSourcesArrayList(mService);
            Log.d(Constants.TAG, "Enabled hosts sources list: " + enabledHostsSources.toString());

            // compile lists (removing whitelist entries, etc.)
            parser.compileList();

            /* BUILD: build one hosts file out of sets and preferences */
            updateApplyNotification(mService, mService.getString(R.string.apply_dialog),
                    mService.getString(R.string.apply_dialog_hosts));

            FileOutputStream fos = mService.openFileOutput(Constants.HOSTS_FILENAME,
                    Context.MODE_PRIVATE);

            bos = new BufferedOutputStream(fos);

            // build current timestamp for header
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date now = new Date();

            // add adaway header
            String header = Constants.HEADER1 + Constants.LINE_SEPERATOR + "# " +
                    formatter.format(now) + Constants.LINE_SEPERATOR + Constants.HEADER2 +
                    Constants.LINE_SEPERATOR + Constants.HEADER_SOURCES;
            bos.write(header.getBytes());

            // write sources into header
            String source = null;
            for (String host : enabledHostsSources) {
                source = Constants.LINE_SEPERATOR + "# " + host;
                bos.write(source.getBytes());
            }

            bos.write(Constants.LINE_SEPERATOR.getBytes());

            String redirectionIP = PreferenceHelper.getRedirectionIP(mService);

            // add "127.0.0.1 localhost" entry
            String localhost = Constants.LINE_SEPERATOR + Constants.LOCALHOST_IPv4 + " "
                    + Constants.LOCALHOST_HOSTNAME + Constants.LINE_SEPERATOR
                    + Constants.LOCALHOST_IPv6 + " " + Constants.LOCALHOST_HOSTNAME;
            bos.write(localhost.getBytes());

            bos.write(Constants.LINE_SEPERATOR.getBytes());

            // write hostnames
            String line;
            String linev6;
            if (PreferenceHelper.getEnableIpv6(mService)) {
                for (String hostname : parser.getBlacklist()) {
                    line = Constants.LINE_SEPERATOR + redirectionIP + " " + hostname;
                    linev6 = Constants.LINE_SEPERATOR + "::1" + " " + hostname;
                    bos.write(line.getBytes());
                    bos.write(linev6.getBytes());
                }
            } else {
                for (String hostname : parser.getBlacklist()) {
                    line = Constants.LINE_SEPERATOR + redirectionIP + " " + hostname;
                    bos.write(line.getBytes());
                }
            }

            /* REDIRECTION LIST: write redirection items */
            String redirectionItemHostname;
            String redirectionItemIP;
            for (HashMap.Entry<String, String> item : parser.getRedirectionList().entrySet()) {
                redirectionItemHostname = item.getKey();
                redirectionItemIP = item.getValue();

                line = Constants.LINE_SEPERATOR + redirectionItemIP + " " + redirectionItemHostname;
                bos.write(line.getBytes());
            }

            // hosts file has to end with new line, when not done last entry won't be
            // recognized
            bos.write(Constants.LINE_SEPERATOR.getBytes());

        } catch (FileNotFoundException e) {
            Log.e(Constants.TAG, "file to read or file to write could not be found", e);

            returnCode = StatusCodes.PRIVATE_FILE_FAIL;
        } catch (IOException e) {
            Log.e(Constants.TAG, "files can not be written or read", e);

            returnCode = StatusCodes.PRIVATE_FILE_FAIL;
        }
        finally {
            try {
                if (bos != null) {
                    bos.flush();
                    bos.close();
                }
            }
            catch (Exception e) {
                Log.e(Constants.TAG, "Error closing output streams", e);
            }
        }

        // delete downloaded hosts file from private storage
        mService.deleteFile(Constants.DOWNLOADED_HOSTS_FILENAME);

        /* APPLY: apply hosts file using RootTools in copyHostsFile() */
        updateApplyNotification(mService, mService.getString(R.string.apply_dialog),
                mService.getString(R.string.apply_dialog_apply));

        Shell rootShell = null;
        try {
            rootShell = Shell.startRootShell();
        } catch (Exception e) {
            Log.e(Constants.TAG, "Problem opening a root shell!", e);
        }

        // copy build hosts file with RootTools, based on target from preferences
        try {
            if (PreferenceHelper.getApplyMethod(mService).equals("writeToSystem")) {

                ApplyUtils.copyHostsFile(mService, Constants.ANDROID_SYSTEM_ETC_HOSTS, rootShell);
            } else if (PreferenceHelper.getApplyMethod(mService).equals("writeToDataData")) {

                ApplyUtils.copyHostsFile(mService, Constants.ANDROID_DATA_DATA_HOSTS, rootShell);
            } else if (PreferenceHelper.getApplyMethod(mService).equals("writeToData")) {

                ApplyUtils.copyHostsFile(mService, Constants.ANDROID_DATA_HOSTS, rootShell);
            } else if (PreferenceHelper.getApplyMethod(mService).equals("customTarget")) {

                ApplyUtils.copyHostsFile(mService, PreferenceHelper.getCustomTarget(mService),
                        rootShell);
            }
        } catch (NotEnoughSpaceException e) {
            Log.e(Constants.TAG, "Exception: ", e);

            returnCode = StatusCodes.NOT_ENOUGH_SPACE;
        } catch (RemountException e) {
            Log.e(Constants.TAG, "Exception: ", e);

            returnCode = StatusCodes.REMOUNT_FAIL;
        } catch (CommandException e) {
            Log.e(Constants.TAG, "Exception: ", e);

            returnCode = StatusCodes.COPY_FAIL;
        }

        // delete generated hosts file from private storage
        mService.deleteFile(Constants.HOSTS_FILENAME);

        /*
         * Set last_modified_local dates in database to last_modified_online, got in download task
         */
        ProviderHelper.updateAllEnabledHostsSourcesLastModifiedLocalFromOnline(mService);

        /* check if hosts file is applied with chosen method */
        // check only if everything before was successful
        if (returnCode == StatusCodes.SUCCESS) {
            if (PreferenceHelper.getApplyMethod(mService).equals("writeToSystem")) {

                /* /system/etc/hosts */

                if (!ApplyUtils.isHostsFileCorrect(mService, Constants.ANDROID_SYSTEM_ETC_HOSTS)) {
                    returnCode = StatusCodes.APPLY_FAIL;
                }
            } else if (PreferenceHelper.getApplyMethod(mService).equals("writeToDataData")) {

                /* /data/data/hosts */

                if (!ApplyUtils.isHostsFileCorrect(mService, Constants.ANDROID_DATA_DATA_HOSTS)) {
                    returnCode = StatusCodes.APPLY_FAIL;
                } else {
                    if (!ApplyUtils.isSymlinkCorrect(Constants.ANDROID_DATA_DATA_HOSTS, rootShell)) {
                        returnCode = StatusCodes.SYMLINK_MISSING;
                    }
                }
            } else if (PreferenceHelper.getApplyMethod(mService).equals("writeToData")) {

                /* /data/data/hosts */

                if (!ApplyUtils.isHostsFileCorrect(mService, Constants.ANDROID_DATA_HOSTS)) {
                    returnCode = StatusCodes.APPLY_FAIL;
                } else {
                    if (!ApplyUtils.isSymlinkCorrect(Constants.ANDROID_DATA_HOSTS, rootShell)) {
                        returnCode = StatusCodes.SYMLINK_MISSING;
                    }
                }
            } else if (PreferenceHelper.getApplyMethod(mService).equals("customTarget")) {

                /* custom target */

                String customTarget = PreferenceHelper.getCustomTarget(mService);

                if (!ApplyUtils.isHostsFileCorrect(mService, customTarget)) {
                    returnCode = StatusCodes.APPLY_FAIL;
                } else {
                    if (!ApplyUtils.isSymlinkCorrect(customTarget, rootShell)) {
                        returnCode = StatusCodes.SYMLINK_MISSING;
                    }
                }
            }
        }

        try {
            rootShell.close();
        } catch (Exception e) {
            Log.e(Constants.TAG, "Problem closing the root shell!", e);
        }

        /* check if APN proxy is set */
        if (returnCode == StatusCodes.SUCCESS) {
            if (ApplyUtils.isApnProxySet(mService)) {
                Log.d(Constants.TAG, "APN proxy is set!");
                returnCode = StatusCodes.APN_PROXY;
            }
        }

        return returnCode;
    }

    /**
     * Creates custom made notification with progress
     */
    private void showApplyNotification(Context context, String tickerText, String contentTitle,
                                       String contentText) {
        // configure the intent
        Intent intent = new Intent(mService, BaseActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(mService.getApplicationContext(),
                0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        // add app name to notificationText
        tickerText = mService.getString(R.string.app_name) + ": " + tickerText;
        int icon = R.drawable.status_bar_icon;
        long when = System.currentTimeMillis();

        // add app name to title
        String contentTitleWithAppName = mService.getString(R.string.app_name) + ": "
                + contentTitle;

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
                .setSmallIcon(icon).setContentTitle(contentTitleWithAppName).setTicker(tickerText)
                .setWhen(when).setOngoing(true).setOnlyAlertOnce(true).setContentText(contentText);

        mNotificationManager.notify(APPLY_NOTIFICATION_ID, mBuilder.build());

        mBuilder.setContentIntent(contentIntent);

        // update status in BaseActivity with Broadcast
        BaseActivity.setStatusBroadcast(mService, contentTitle, contentText, StatusCodes.CHECKING);
    }

    private void updateApplyNotification(Context context, String contentTitle, String contentText) {
        // configure the intent
        Intent intent = new Intent(mService, BaseActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(mService.getApplicationContext(),
                0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        int icon = R.drawable.status_bar_icon;

        // add app name to title
        String contentTitleWithAppName = mService.getString(R.string.app_name) + ": "
                + contentTitle;

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
                .setSmallIcon(icon).setContentTitle(contentTitleWithAppName)
                .setContentText(contentText);

        mNotificationManager.notify(APPLY_NOTIFICATION_ID, mBuilder.build());

        mBuilder.setContentIntent(contentIntent);

        // update status in BaseActivity with Broadcast
        BaseActivity.setStatusBroadcast(mService, contentTitle, contentText, StatusCodes.CHECKING);
    }

    private void cancelApplyNotification() {
        mNotificationManager.cancel(APPLY_NOTIFICATION_ID);
    }

}
