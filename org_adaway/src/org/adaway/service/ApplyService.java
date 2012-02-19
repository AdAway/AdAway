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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.adaway.R;
import org.adaway.helper.PreferencesHelper;
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
import org.adaway.util.RegexUtils;
import org.adaway.util.RemountException;
import org.adaway.util.StatusCodes;
import org.adaway.util.Utils;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

public class ApplyService extends WakefulIntentService {
    private Context mService;
    private Notification mApplyNotification;
    private NotificationManager mNotificationManager;

    String mCurrentUrl;

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
        BaseActivity.setButtonsBroadcast(mService, false);

        // download files with download method
        int downloadResult = download();
        Log.d(Constants.TAG, "Download result: " + downloadResult);

        if (downloadResult == StatusCodes.SUCCESS) {
            // Apply files by apply method
            int applyResult = apply();

            cancelApplyNotification();
            // enable buttons
            BaseActivity.setButtonsBroadcast(mService, true);
            Log.d(Constants.TAG, "Apply result: " + applyResult);

            ResultHelper.showNotificationBasedOnResult(mService, applyResult, null);
        } else if (downloadResult == StatusCodes.DOWNLOAD_FAIL) {
            cancelApplyNotification();
            // enable buttons
            BaseActivity.setButtonsBroadcast(mService, true);
            // extra information is current url, to show it when it fails
            ResultHelper.showNotificationBasedOnResult(mService, downloadResult, mCurrentUrl);
        } else {
            cancelApplyNotification();
            // enable buttons
            BaseActivity.setButtonsBroadcast(mService, true);
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

                // get cursor over all enabled hosts source
                enabledHostsSourcesCursor = ProviderHelper.getEnabledHostsSourcesCursor(mService);

                // iterate over all hosts sources in db with cursor
                if (enabledHostsSourcesCursor.moveToFirst()) {
                    do {

                        InputStream is = null;
                        BufferedInputStream bis = null;
                        try {
                            mCurrentUrl = enabledHostsSourcesCursor
                                    .getString(enabledHostsSourcesCursor.getColumnIndex("url"));

                            Log.v(Constants.TAG, "Downloading hosts file: " + mCurrentUrl);

                            /* change URL in download dialog */
                            updateApplyNotification(mService,
                                    mService.getString(R.string.download_dialog), mCurrentUrl);

                            /* build connection */
                            URL mURL = new URL(mCurrentUrl);
                            URLConnection connection = mURL.openConnection();

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
                                    currentLastModifiedOnline);

                        } catch (Exception e) {
                            Log.e(Constants.TAG, "Exception: " + e);
                            returnCode = StatusCodes.DOWNLOAD_FAIL;
                            break; // stop for-loop
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
                                Log.e(Constants.TAG, "Exception on flush and closing streams: " + e);
                                e.printStackTrace();
                            }
                        }

                    } while (enabledHostsSourcesCursor.moveToNext());
                } else {
                    // cursor empty
                    returnCode = StatusCodes.EMPTY_HOSTS_SOURCES;
                }

                // close cursor in the end
                if (enabledHostsSourcesCursor != null && !enabledHostsSourcesCursor.isClosed()) {
                    enabledHostsSourcesCursor.close();
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
                    Log.e(Constants.TAG, "Exception on close of out: " + e);
                    e.printStackTrace();
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

        try {
            /* PARSE: parse hosts files to sets of hostnames and comments */

            FileInputStream fis = mService.openFileInput(Constants.DOWNLOADED_HOSTS_FILENAME);

            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));

            HostsParser parser = new HostsParser(reader, false);
            HashSet<String> hostsSourcesBlacklist = parser.getBlacklist();

            HashMap<String, String> hostsSourcesRedirectionList = new HashMap<String, String>();
            // Use redirection rules from hosts sources only if enabled in preferences
            if (PreferencesHelper.getRedirectionRules(mService)) {
                hostsSourcesRedirectionList = parser.getRedirectionList();
            }

            fis.close();

            updateApplyNotification(mService, mService.getString(R.string.apply_dialog),
                    mService.getString(R.string.apply_dialog_lists));

            /* READ DATABSE CONTENT */

            // get whitelist
            HashSet<String> whitelist = ProviderHelper.getEnabledWhitelistArrayList(mService);
            Log.d(Constants.TAG, "Enabled whitelist: " + whitelist.toString());

            // get blacklist
            HashSet<String> blacklist = ProviderHelper.getEnabledBlacklistArrayList(mService);
            Log.d(Constants.TAG, "Enabled blacklist: " + blacklist.toString());

            // get redirection list
            HashMap<String, String> redirection = ProviderHelper
                    .getEnabledRedirectionListHashMap(mService);
            Log.d(Constants.TAG, "Enabled redirection list: " + redirection.toString());

            // get hosts sources list
            ArrayList<String> enabledHostsSources = ProviderHelper
                    .getEnabledHostsSourcesArrayList(mService);
            Log.d(Constants.TAG, "Enabled hosts sources list: " + enabledHostsSources.toString());

            /* BLACKLIST */
            // remove whitelist items using regex
            Log.d(Constants.TAG, "Compiling all whitelist regex");

            HashSet<Pattern> whitelistPattern = new HashSet<Pattern>();
            String regexItem;
            for (String item : whitelist) {
                // convert example*.* to regex: ^example.*\\..*$
                regexItem = RegexUtils.wildcardToRegex(item);
                whitelistPattern.add(Pattern.compile(regexItem));
            }

            Log.d(Constants.TAG, "Starting whitelist regex");
            Matcher whitelistMatcher;
            String blacklistHostname;
            // go through all blacklist hostnames from host sources
            for (Iterator<String> iterator = hostsSourcesBlacklist.iterator(); iterator.hasNext();) {
                blacklistHostname = iterator.next();

                // use all whitelist patterns on this hostname
                for (Pattern pattern : whitelistPattern) {
                    whitelistMatcher = pattern.matcher(blacklistHostname);

                    try {
                        if (whitelistMatcher.find()) {
                            // remove item, because regex fits
                            iterator.remove();
                        }
                    } catch (Exception e) {
                        // workaround for some devices that throws jni exceptions: dont use
                        // whitelist
                        Log.e(Constants.TAG, "Error in whitelist regex processing");
                        e.printStackTrace();
                    }
                }
            }
            Log.d(Constants.TAG, "Ending whitelist regex");

            // add blacklist items
            hostsSourcesBlacklist.addAll(blacklist);

            // remove hostnames that are in redirection list
            HashSet<String> redirectionRemove = new HashSet<String>(redirection.keySet());
            hostsSourcesBlacklist.removeAll(redirectionRemove);

            /* REDIRECTION LIST */
            // add all redirection items from your lists
            hostsSourcesRedirectionList.putAll(redirection);

            // TODO:
            // All whitelist items should be removed from HostsSourcesRedirectionList
            // All blacklist items should be removed from HostsSourcesRedirectionList
            // All redirectionList items should be removed from HostsSourcesRedirectionList

            /* BUILD: build one hosts file out of sets and preferences */
            updateApplyNotification(mService, mService.getString(R.string.apply_dialog),
                    mService.getString(R.string.apply_dialog_hosts));

            FileOutputStream fos = mService.openFileOutput(Constants.HOSTS_FILENAME,
                    Context.MODE_PRIVATE);

            // add adaway header
            String header = Constants.HEADER1 + Constants.LINE_SEPERATOR + Constants.HEADER2
                    + Constants.LINE_SEPERATOR + Constants.HEADER_SOURCES;
            fos.write(header.getBytes());

            // write sources into header
            String source = null;
            for (String host : enabledHostsSources) {
                source = Constants.LINE_SEPERATOR + "# " + host;
                fos.write(source.getBytes());
            }

            fos.write(Constants.LINE_SEPERATOR.getBytes());

            String redirectionIP = PreferencesHelper.getRedirectionIP(mService);

            // add "127.0.0.1 localhost" entry
            String localhost = Constants.LINE_SEPERATOR + Constants.LOCALHOST_IPv4 + " "
                    + Constants.LOCALHOST_HOSTNAME + Constants.LINE_SEPERATOR
                    + Constants.LOCALHOST_IPv6 + " " + Constants.LOCALHOST_HOSTNAME;
            fos.write(localhost.getBytes());

            fos.write(Constants.LINE_SEPERATOR.getBytes());

            // write hostnames
            String line;
            for (String hostname : hostsSourcesBlacklist) {
                line = Constants.LINE_SEPERATOR + redirectionIP + " " + hostname;
                fos.write(line.getBytes());
            }

            /* REDIRECTION LIST: write redirection items */
            String redirectionItemHostname;
            String redirectionItemIP;
            for (HashMap.Entry<String, String> item : hostsSourcesRedirectionList.entrySet()) {
                redirectionItemHostname = item.getKey();
                redirectionItemIP = item.getValue();

                line = Constants.LINE_SEPERATOR + redirectionItemIP + " " + redirectionItemHostname;
                fos.write(line.getBytes());
            }

            // hosts file has to end with new line, when not done last entry won't be
            // recognized
            fos.write(Constants.LINE_SEPERATOR.getBytes());

            fos.close();

        } catch (FileNotFoundException e) {
            Log.e(Constants.TAG, "file to read or file to write could not be found");
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();

            returnCode = StatusCodes.PRIVATE_FILE_FAIL;
        } catch (IOException e) {
            Log.e(Constants.TAG, "files can not be written or read");
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();

            returnCode = StatusCodes.PRIVATE_FILE_FAIL;
        }

        // delete downloaded hosts file from private storage
        mService.deleteFile(Constants.DOWNLOADED_HOSTS_FILENAME);

        /* APPLY: apply hosts file using RootTools in copyHostsFile() */
        updateApplyNotification(mService, mService.getString(R.string.apply_dialog),
                mService.getString(R.string.apply_dialog_apply));

        // copy build hosts file with RootTools, based on target from preferences
        try {
            if (PreferencesHelper.getApplyMethod(mService).equals("writeToSystem")) {

                ApplyUtils.copyHostsFile(mService, "");
            } else if (PreferencesHelper.getApplyMethod(mService).equals("writeToDataData")) {

                ApplyUtils.copyHostsFile(mService, Constants.ANDROID_DATA_DATA_HOSTS);
            } else if (PreferencesHelper.getApplyMethod(mService).equals("customTarget")) {

                ApplyUtils.copyHostsFile(mService, PreferencesHelper.getCustomTarget(mService));
            }
        } catch (NotEnoughSpaceException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();

            returnCode = StatusCodes.NOT_ENOUGH_SPACE;
        } catch (RemountException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();

            returnCode = StatusCodes.REMOUNT_FAIL;
        } catch (CommandException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();

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
            if (PreferencesHelper.getApplyMethod(mService).equals("writeToSystem")) {

                /* /system/etc/hosts */

                if (!ApplyUtils.isHostsFileCorrect(mService, Constants.ANDROID_SYSTEM_ETC_HOSTS)) {
                    returnCode = StatusCodes.APPLY_FAIL;
                }
            } else if (PreferencesHelper.getApplyMethod(mService).equals("writeToDataData")) {

                /* /data/data/hosts */

                if (!ApplyUtils.isHostsFileCorrect(mService, Constants.ANDROID_DATA_DATA_HOSTS)) {
                    returnCode = StatusCodes.APPLY_FAIL;
                } else {
                    if (!ApplyUtils.isSymlinkCorrect(Constants.ANDROID_DATA_DATA_HOSTS)) {
                        returnCode = StatusCodes.SYMLINK_MISSING;
                    }
                }
            } else if (PreferencesHelper.getApplyMethod(mService).equals("customTarget")) {

                /* custom target */

                String customTarget = PreferencesHelper.getCustomTarget(mService);

                if (!ApplyUtils.isHostsFileCorrect(mService, customTarget)) {
                    returnCode = StatusCodes.APPLY_FAIL;
                } else {
                    if (!ApplyUtils.isSymlinkCorrect(customTarget)) {
                        returnCode = StatusCodes.SYMLINK_MISSING;
                    }
                }
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

        // configure the notification
        mApplyNotification = new Notification(R.drawable.status_bar_icon, tickerText,
                System.currentTimeMillis());
        mApplyNotification.flags = mApplyNotification.flags | Notification.FLAG_ONGOING_EVENT
                | Notification.FLAG_ONLY_ALERT_ONCE;

        // add app name to title
        String contentTitleWithAppName = mService.getString(R.string.app_name) + ": "
                + contentTitle;

        mApplyNotification.setLatestEventInfo(context, contentTitleWithAppName, contentText,
                contentIntent);

        mNotificationManager.notify(APPLY_NOTIFICATION_ID, mApplyNotification);

        // update status in BaseActivity with Broadcast
        BaseActivity.setStatusBroadcast(mService, contentTitle, contentText, StatusCodes.CHECKING);
    }

    private void updateApplyNotification(Context context, String contentTitle, String contentText) {
        // configure the intent
        Intent intent = new Intent(mService, BaseActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(mService.getApplicationContext(),
                0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        // add app name to title
        String contentTitleWithAppName = mService.getString(R.string.app_name) + ": "
                + contentTitle;

        mApplyNotification.setLatestEventInfo(context, contentTitleWithAppName, contentText,
                contentIntent);

        mNotificationManager.notify(APPLY_NOTIFICATION_ID, mApplyNotification);

        // update status in BaseActivity with Broadcast
        BaseActivity.setStatusBroadcast(mService, contentTitle, contentText, StatusCodes.CHECKING);
    }

    private void cancelApplyNotification() {
        mNotificationManager.cancel(APPLY_NOTIFICATION_ID);
    }

}
