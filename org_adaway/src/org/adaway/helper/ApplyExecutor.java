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

package org.adaway.helper;

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

import org.adaway.R;
import org.adaway.provider.AdAwayContract.HostsSources;
import org.adaway.provider.ProviderHelper;
import org.adaway.ui.BaseActivity;
import org.adaway.ui.BaseFragment;
import org.adaway.ui.HelpActivity;
import org.adaway.util.ApplyUtils;
import org.adaway.util.CommandException;
import org.adaway.util.Constants;
import org.adaway.util.HostsParser;
import org.adaway.util.Log;
import org.adaway.util.NotEnoughSpaceException;
import org.adaway.util.RemountException;
import org.adaway.util.ReturnCodes;
import org.adaway.util.Utils;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.widget.RemoteViews;

public class ApplyExecutor {
    private Context mContext;

    private AsyncTask<Void, String, Integer> mDownloadTask;
    private AsyncTask<Void, String, Integer> mApplyTask;

    private Notification mApplyNotification;
    private NotificationManager mNotificationManager;

    /**
     * Constructor based on fragment
     * 
     * @param baseFragment
     */
    public ApplyExecutor(Context context) {
        super();
        this.mContext = context;
        this.mNotificationManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);
    }

    /**
     * Execute Apply process
     */
    public void apply() {
        runDownloadTask();
    }

    // Notification id
    private static final int PROGRESS_NOTIFICATION_ID = 10;
    private static final int RESULT_NOTIFICATION_ID = 11;

    /**
     * Creates custom made notification with progress
     */
    private void showProgressNotification(String notificationText, String startTitle) {
        // configure the intent
        Intent intent = new Intent(mContext, BaseActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext.getApplicationContext(),
                0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        // add app name to notificationText
        notificationText = mContext.getString(R.string.app_name) + ": " + notificationText;

        // configure the notification
        mApplyNotification = new Notification(R.drawable.status_bar_icon, notificationText,
                System.currentTimeMillis());
        mApplyNotification.flags = mApplyNotification.flags | Notification.FLAG_ONGOING_EVENT
                | Notification.FLAG_ONLY_ALERT_ONCE;
        mApplyNotification.contentView = new RemoteViews(mContext.getPackageName(),
                R.layout.apply_notification);
        mApplyNotification.contentIntent = pendingIntent;

        // add app name to title
        startTitle = mContext.getString(R.string.app_name) + ": " + startTitle;

        mApplyNotification.contentView.setTextViewText(R.id.apply_notification_title, startTitle);

        mNotificationManager.notify(PROGRESS_NOTIFICATION_ID, mApplyNotification);
    }

    private void setProgressNotificationText(String text) {
        mApplyNotification.contentView.setTextViewText(R.id.apply_notification_text, text);

        // inform the progress bar of updates in progress
        mNotificationManager.notify(PROGRESS_NOTIFICATION_ID, mApplyNotification);
    }

    private void cancelProgressNotification() {
        mNotificationManager.cancel(PROGRESS_NOTIFICATION_ID);
    }

    /**
     * Show notification with result defined in params
     * 
     * @param contentTitle
     * @param contentText
     */
    private void showResultNotification(String contentTitle, String contentText,
            int applyingResult, String failingUrl) {
        int icon = R.drawable.status_bar_icon;
        long when = System.currentTimeMillis();

        // add app name to title
        contentTitle = mContext.getString(R.string.app_name) + ": " + contentTitle;

        Notification notification = new Notification(icon, contentTitle, when);
        notification.flags |= Notification.FLAG_AUTO_CANCEL;

        Context context = mContext;
        Intent notificationIntent = new Intent(mContext, BaseActivity.class);

        // give postApplyingStatus with intent
        notificationIntent.putExtra(BaseActivity.EXTRA_APPLYING_RESULT, applyingResult);
        notificationIntent.putExtra(BaseActivity.EXTRA_FAILING_URL, failingUrl);

        PendingIntent contentIntent = PendingIntent.getActivity(mContext, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);

        mNotificationManager.notify(RESULT_NOTIFICATION_ID, notification);
    }

    /**
     * AsyncTask to download hosts files defined in db. In onPostExecute an Apply AsyncTask will be
     * started
     */
    private void runDownloadTask(String... urls) {
        mDownloadTask = new AsyncTask<Void, String, Integer>() {
            Cursor mEnabledHostsSourcesCursor;

            private byte data[];
            private int count;
            private String mCurrentUrl;
            private long mCurrentLastModifiedOnline;

            @Override
            protected Integer doInBackground(Void... unused) {
                int returnCode = ReturnCodes.SUCCESS; // default return code

                if (Utils.isAndroidOnline(mContext)) {
                    // output to write into
                    FileOutputStream out = null;

                    try {
                        out = mContext.openFileOutput(Constants.DOWNLOADED_HOSTS_FILENAME,
                                Context.MODE_PRIVATE);

                        // get cursor over all enabled hosts source
                        mEnabledHostsSourcesCursor = ProviderHelper
                                .getEnabledHostsSourcesCursor(mContext);

                        // iterate over all hosts sources in db with cursor
                        if (mEnabledHostsSourcesCursor.moveToFirst()) {
                            do {

                                // stop if thread canceled
                                if (isCancelled()) {
                                    break;
                                }

                                InputStream is = null;
                                BufferedInputStream bis = null;
                                try {
                                    mCurrentUrl = mEnabledHostsSourcesCursor
                                            .getString(mEnabledHostsSourcesCursor
                                                    .getColumnIndex("url"));

                                    Log.v(Constants.TAG, "Downloading hosts file: " + mCurrentUrl);

                                    /* change URL in download dialog */
                                    publishProgress(mCurrentUrl); // update UI

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
                                    while ((count = bis.read(data)) != -1 && !isCancelled()) {
                                        out.write(data, 0, count);
                                    }

                                    // add line seperator to add files together in one file
                                    out.write(Constants.LINE_SEPERATOR.getBytes());

                                    // save last modified online for later use
                                    mCurrentLastModifiedOnline = connection.getLastModified();

                                    ProviderHelper.updateHostsSourceLastModifiedOnline(mContext,
                                            mEnabledHostsSourcesCursor
                                                    .getInt(mEnabledHostsSourcesCursor
                                                            .getColumnIndex(HostsSources._ID)),
                                            mCurrentLastModifiedOnline);

                                } catch (Exception e) {
                                    Log.e(Constants.TAG, "Exception: " + e);
                                    returnCode = ReturnCodes.DOWNLOAD_FAIL;
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
                                        Log.e(Constants.TAG,
                                                "Exception on flush and closing streams: " + e);
                                        e.printStackTrace();
                                    }
                                }

                            } while (mEnabledHostsSourcesCursor.moveToNext());
                        } else {
                            // cursor empty
                            returnCode = ReturnCodes.EMPTY_HOSTS_SOURCES;
                        }

                        // close cursor in the end
                        if (mEnabledHostsSourcesCursor != null
                                && !mEnabledHostsSourcesCursor.isClosed()) {
                            mEnabledHostsSourcesCursor.close();
                        }
                    } catch (Exception e) {
                        Log.e(Constants.TAG, "Private File can not be created, Exception: " + e);
                        returnCode = ReturnCodes.PRIVATE_FILE_FAIL;
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
                    returnCode = ReturnCodes.NO_CONNECTION;
                }

                return returnCode;
            }

            @Override
            protected void onPreExecute() {
                super.onPreExecute();

                showProgressNotification(mContext.getString(R.string.download_dialog),
                        mContext.getString(R.string.download_dialog));
            }

            @Override
            protected void onProgressUpdate(String... url) {
                setProgressNotificationText(url[0]);
            }

            @Override
            protected void onPostExecute(Integer result) {
                super.onPostExecute(result);
                Log.d(Constants.TAG, "onPostExecute result: " + result);

                if (result == ReturnCodes.SUCCESS) {
                    // Apply files by Apply thread
                    runApplyTask();
                } else if (result == ReturnCodes.DOWNLOAD_FAIL) {
                    // extra information is current url, to show it when it fails
                    showNotificationBasedOnResult(result, mCurrentUrl);
                } else {
                    showNotificationBasedOnResult(result, null);
                }
            }
        };

        mDownloadTask.execute();
    }

    /**
     * AsyncTask to parse downloaded hosts files, build one new merged hosts file out of them using
     * the redirection ip from the preferences and apply them using RootTools.
     */
    private void runApplyTask() {
        mApplyTask = new AsyncTask<Void, String, Integer>() {

            @Override
            protected Integer doInBackground(Void... unused) {
                int returnCode = ReturnCodes.SUCCESS; // default return code

                try {
                    /* PARSE: parse hosts files to sets of hostnames and comments */
                    publishProgress(mContext.getString(R.string.apply_dialog_hostnames));

                    FileInputStream fis = mContext
                            .openFileInput(Constants.DOWNLOADED_HOSTS_FILENAME);

                    BufferedReader reader = new BufferedReader(new InputStreamReader(fis));

                    HostsParser parser = new HostsParser(reader);
                    HashSet<String> hostnames = parser.getBlacklist();

                    fis.close();

                    publishProgress(mContext.getString(R.string.apply_dialog_lists));

                    /* READ DATABSE CONTENT */

                    // get whitelist
                    HashSet<String> whitelist = ProviderHelper
                            .getEnabledWhitelistArrayList(mContext);
                    Log.d(Constants.TAG, "Enabled whitelist: " + whitelist.toString());

                    // get blacklist
                    HashSet<String> blacklist = ProviderHelper
                            .getEnabledBlacklistArrayList(mContext);
                    Log.d(Constants.TAG, "Enabled blacklist: " + blacklist.toString());

                    // get redirection list
                    HashMap<String, String> redirection = ProviderHelper
                            .getEnabledRedirectionListHashMap(mContext);
                    Log.d(Constants.TAG, "Enabled redirection list: " + redirection.toString());

                    // get sources list
                    ArrayList<String> enabledHostsSources = ProviderHelper
                            .getEnabledHostsSourcesArrayList(mContext);
                    Log.d(Constants.TAG,
                            "Enabled hosts sources list: " + enabledHostsSources.toString());

                    /* BLACKLIST AND WHITELIST */
                    // remove whitelist items
                    hostnames.removeAll(whitelist);

                    // add blacklist items
                    hostnames.addAll(blacklist);

                    /* REDIRECTION LIST: remove hostnames that are in redirection list */
                    HashSet<String> redirectionRemove = new HashSet<String>(redirection.keySet());

                    // remove all redirection hostnames
                    hostnames.removeAll(redirectionRemove);

                    /* BUILD: build one hosts file out of sets and preferences */
                    publishProgress(mContext.getString(R.string.apply_dialog_hosts));

                    FileOutputStream fos = mContext.openFileOutput(Constants.HOSTS_FILENAME,
                            Context.MODE_PRIVATE);

                    // add adaway header
                    String header = Constants.HEADER1 + Constants.LINE_SEPERATOR
                            + Constants.HEADER2 + Constants.LINE_SEPERATOR
                            + Constants.HEADER_SOURCES;
                    fos.write(header.getBytes());

                    // write sources into header
                    String source = null;
                    for (String host : enabledHostsSources) {
                        source = Constants.LINE_SEPERATOR + "# " + host;
                        fos.write(source.getBytes());
                    }

                    fos.write(Constants.LINE_SEPERATOR.getBytes());

                    String redirectionIP = PreferencesHelper.getRedirectionIP(mContext);

                    // add "127.0.0.1 localhost" entry
                    String localhost = Constants.LINE_SEPERATOR + Constants.LOCALHOST_IPv4 + " "
                            + Constants.LOCALHOST_HOSTNAME + Constants.LINE_SEPERATOR
                            + Constants.LOCALHOST_IPv6 + " " + Constants.LOCALHOST_HOSTNAME;
                    fos.write(localhost.getBytes());

                    fos.write(Constants.LINE_SEPERATOR.getBytes());

                    // write hostnames
                    String line;
                    for (String hostname : hostnames) {
                        line = Constants.LINE_SEPERATOR + redirectionIP + " " + hostname;
                        fos.write(line.getBytes());
                    }

                    /* REDIRECTION LIST: write redirection items */
                    String redirectionItemHostname;
                    String redirectionItemIP;
                    for (HashMap.Entry<String, String> item : redirection.entrySet()) {
                        redirectionItemHostname = item.getKey();
                        redirectionItemIP = item.getValue();

                        line = Constants.LINE_SEPERATOR + redirectionItemIP + " "
                                + redirectionItemHostname;
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

                    returnCode = ReturnCodes.PRIVATE_FILE_FAIL;
                } catch (IOException e) {
                    Log.e(Constants.TAG, "files can not be written or read");
                    Log.e(Constants.TAG, "Exception: " + e);
                    e.printStackTrace();

                    returnCode = ReturnCodes.PRIVATE_FILE_FAIL;
                }

                // delete downloaded hosts file from private storage
                mContext.deleteFile(Constants.DOWNLOADED_HOSTS_FILENAME);

                /* APPLY: apply hosts file using RootTools in copyHostsFile() */
                publishProgress(mContext.getString(R.string.apply_dialog_apply));

                // copy build hosts file with RootTools, based on target from preferences
                try {
                    if (PreferencesHelper.getApplyMethod(mContext).equals("writeToSystem")) {

                        ApplyUtils.copyHostsFile(mContext, "");
                    } else if (PreferencesHelper.getApplyMethod(mContext).equals("writeToDataData")) {

                        ApplyUtils.copyHostsFile(mContext, Constants.ANDROID_DATA_DATA_HOSTS);
                    } else if (PreferencesHelper.getApplyMethod(mContext).equals("customTarget")) {

                        ApplyUtils.copyHostsFile(mContext,
                                PreferencesHelper.getCustomTarget(mContext));
                    }
                } catch (NotEnoughSpaceException e) {
                    Log.e(Constants.TAG, "Exception: " + e);
                    e.printStackTrace();

                    returnCode = ReturnCodes.NOT_ENOUGH_SPACE;
                } catch (RemountException e) {
                    Log.e(Constants.TAG, "Exception: " + e);
                    e.printStackTrace();

                    returnCode = ReturnCodes.REMOUNT_FAIL;
                } catch (CommandException e) {
                    Log.e(Constants.TAG, "Exception: " + e);
                    e.printStackTrace();

                    returnCode = ReturnCodes.COPY_FAIL;
                }

                // delete generated hosts file from private storage
                mContext.deleteFile(Constants.HOSTS_FILENAME);

                /*
                 * Set last_modified_local dates in database to last_modified_online, got in
                 * download task
                 */
                ProviderHelper.updateAllEnabledHostsSourcesLastModifiedLocalFromOnline(mContext);

                /* check if hosts file is applied with chosen method */
                // check only if everything before was successful
                if (returnCode == ReturnCodes.SUCCESS) {
                    if (PreferencesHelper.getApplyMethod(mContext).equals("writeToSystem")) {

                        /* /system/etc/hosts */

                        if (!ApplyUtils.isHostsFileCorrect(mContext,
                                Constants.ANDROID_SYSTEM_ETC_HOSTS)) {
                            returnCode = ReturnCodes.APPLY_FAIL;
                        }
                    } else if (PreferencesHelper.getApplyMethod(mContext).equals("writeToDataData")) {

                        /* /data/data/hosts */

                        if (!ApplyUtils.isHostsFileCorrect(mContext,
                                Constants.ANDROID_DATA_DATA_HOSTS)) {
                            returnCode = ReturnCodes.APPLY_FAIL;
                        } else {
                            if (!ApplyUtils.isSymlinkCorrect(Constants.ANDROID_DATA_DATA_HOSTS)) {
                                returnCode = ReturnCodes.SYMLINK_MISSING;
                            }
                        }
                    } else if (PreferencesHelper.getApplyMethod(mContext).equals("customTarget")) {

                        /* custom target */

                        String customTarget = PreferencesHelper.getCustomTarget(mContext);

                        if (!ApplyUtils.isHostsFileCorrect(mContext, customTarget)) {
                            returnCode = ReturnCodes.APPLY_FAIL;
                        } else {
                            if (!ApplyUtils.isSymlinkCorrect(customTarget)) {
                                returnCode = ReturnCodes.SYMLINK_MISSING;
                            }
                        }
                    }
                }

                return returnCode;
            }

            @Override
            protected void onPreExecute() {
                super.onPreExecute();

                showProgressNotification(mContext.getString(R.string.apply_dialog),
                        mContext.getString(R.string.apply_dialog));
            }

            @Override
            protected void onProgressUpdate(String... status) {
                setProgressNotificationText(status[0]);
            }

            @Override
            protected void onPostExecute(Integer result) {
                super.onPostExecute(result);

                cancelProgressNotification();

                showNotificationBasedOnResult(result, null);
            }
        };

        mApplyTask.execute();
    }

    /**
     * Show notification based on result after processing download and apply
     * 
     * @param result
     */
    private void showNotificationBasedOnResult(int result, String failingUrl) {
        if (result == ReturnCodes.SUCCESS) {
            // only show if reboot dialog is not disabled in preferences
            if (!PreferencesHelper.getNeverReboot(mContext)) {
                // show notification
                showResultNotification(mContext.getString(R.string.apply_success_title),
                        mContext.getString(R.string.apply_success), result, null);
            }
        } else if (result == ReturnCodes.DOWNLOAD_FAIL) {
            // extra information for intent: url
            showResultNotification(mContext.getString(R.string.download_fail_title),
                    mContext.getString(R.string.download_fail), result, failingUrl);
        } else {
            String postTitle = "";
            String postMessage = "";
            switch (result) {
            case ReturnCodes.SYMLINK_MISSING:
                postTitle = mContext.getString(R.string.apply_symlink_missing_title);
                postMessage = mContext.getString(R.string.apply_symlink_missing);
                break;
            case ReturnCodes.NO_CONNECTION:
                postTitle = mContext.getString(R.string.no_connection_title);
                postMessage = mContext.getString(R.string.no_connection);
                break;
            case ReturnCodes.EMPTY_HOSTS_SOURCES:
                postTitle = mContext.getString(R.string.no_sources_title);
                postMessage = mContext.getString(R.string.no_sources);
                break;
            case ReturnCodes.APPLY_FAIL:
                postTitle = mContext.getString(R.string.apply_fail_title);
                postMessage = mContext.getString(R.string.apply_fail);
                break;
            case ReturnCodes.PRIVATE_FILE_FAIL:
                postTitle = mContext.getString(R.string.apply_private_file_fail_title);
                postMessage = mContext.getString(R.string.apply_private_file_fail);
                break;
            case ReturnCodes.NOT_ENOUGH_SPACE:
                postTitle = mContext.getString(R.string.apply_not_enough_space_title);
                postMessage = mContext.getString(R.string.apply_not_enough_space);
                break;
            case ReturnCodes.REMOUNT_FAIL:
                postTitle = mContext.getString(R.string.apply_remount_fail_title);
                postMessage = mContext.getString(R.string.apply_remount_fail);
                break;
            case ReturnCodes.COPY_FAIL:
                postTitle = mContext.getString(R.string.apply_copy_fail_title);
                postMessage = mContext.getString(R.string.apply_copy_fail);
                break;
            }

            // show notification
            showResultNotification(postTitle, postMessage, result, null);
        }
    }

    /**
     * Shows dialog and further information how to proceed after the applying process has ended and
     * the user clicked on the notification. This is based on the result from the apply process.
     * 
     * @param result
     */
    public void processApplyingResult(final BaseFragment baseFragment, int result, String failingUrl) {
        if (result == ReturnCodes.SUCCESS) {
            baseFragment.setStatusEnabled();

            Utils.rebootQuestion(mContext, R.string.apply_success_title, R.string.apply_success);
        } else if (result == ReturnCodes.SYMLINK_MISSING) {
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
            builder.setTitle(R.string.apply_symlink_missing_title);
            builder.setMessage(mContext.getString(R.string.apply_symlink_missing));
            builder.setIcon(android.R.drawable.ic_dialog_info);
            builder.setPositiveButton(mContext.getString(R.string.button_yes),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            tryToCreateSymlink(baseFragment);
                        }
                    });
            builder.setNegativeButton(mContext.getString(R.string.button_no),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();

                            baseFragment.setStatusDisabled();
                        }
                    });
            AlertDialog question = builder.create();
            question.show();
        } else {
            baseFragment.setStatusDisabled();

            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setPositiveButton(mContext.getString(R.string.button_close),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                        }
                    });
            builder.setNegativeButton(mContext.getString(R.string.button_help),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();

                            // go to help
                            mContext.startActivity(new Intent(mContext, HelpActivity.class));
                        }
                    });

            String postTitle = "";
            String postMessage = "";
            switch (result) {
            case ReturnCodes.NO_CONNECTION:
                postTitle = mContext.getString(R.string.no_connection_title);
                postMessage = mContext.getString(R.string.no_connection);
                break;
            case ReturnCodes.DOWNLOAD_FAIL:
                postTitle = mContext.getString(R.string.download_fail_title);
                if (failingUrl != null) {
                    postMessage = mContext.getString(R.string.download_fail) + "\n" + failingUrl;
                } else {
                    postMessage = mContext.getString(R.string.download_fail);
                }
                break;
            case ReturnCodes.EMPTY_HOSTS_SOURCES:
                postTitle = mContext.getString(R.string.no_sources_title);
                postMessage = mContext.getString(R.string.no_sources);
                break;
            case ReturnCodes.APPLY_FAIL:
                postTitle = mContext.getString(R.string.apply_fail_title);
                postMessage = mContext.getString(R.string.apply_fail);
                break;
            case ReturnCodes.PRIVATE_FILE_FAIL:
                postTitle = mContext.getString(R.string.apply_private_file_fail_title);
                postMessage = mContext.getString(R.string.apply_private_file_fail);
                break;
            case ReturnCodes.NOT_ENOUGH_SPACE:
                postTitle = mContext.getString(R.string.apply_not_enough_space_title);
                postMessage = mContext.getString(R.string.apply_not_enough_space);
                break;
            case ReturnCodes.REMOUNT_FAIL:
                postTitle = mContext.getString(R.string.apply_remount_fail_title);
                postMessage = mContext.getString(R.string.apply_remount_fail);
                break;
            case ReturnCodes.COPY_FAIL:
                postTitle = mContext.getString(R.string.apply_copy_fail_title);
                postMessage = mContext.getString(R.string.apply_copy_fail);
                break;
            }
            postMessage += "\n\n" + mContext.getString(R.string.apply_help);
            builder.setTitle(postTitle);
            builder.setMessage(postMessage);

            AlertDialog alertDialog = builder.create();
            alertDialog.show();
        }
    }

    /**
     * Trying to create symlink and displays dialogs on fail
     */
    private void tryToCreateSymlink(BaseFragment baseFragment) {
        boolean success = true;

        try {
            // symlink to /system/etc/hosts, based on target
            if (PreferencesHelper.getApplyMethod(mContext).equals("writeToDataData")) {
                ApplyUtils.createSymlink(Constants.ANDROID_DATA_DATA_HOSTS);
            } else if (PreferencesHelper.getApplyMethod(mContext).equals("customTarget")) {
                ApplyUtils.createSymlink(PreferencesHelper.getCustomTarget(mContext));
            }
        } catch (CommandException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();

            success = false;
        } catch (RemountException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();

            success = false;
        }

        if (success) {
            if (ApplyUtils.isHostsFileCorrect(mContext, Constants.ANDROID_SYSTEM_ETC_HOSTS)) {
                success = true;
            } else {
                success = false;
            }
        }

        if (success) {
            baseFragment.setStatusEnabled();

            Utils.rebootQuestion(mContext, R.string.apply_symlink_successful_title,
                    R.string.apply_symlink_successful);
        } else {
            baseFragment.setStatusDisabled();

            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
            builder.setTitle(R.string.apply_symlink_fail_title);
            builder.setMessage(mContext.getString(R.string.apply_symlink_fail) + "\n\n"
                    + mContext.getString(R.string.apply_help));
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setPositiveButton(mContext.getString(R.string.button_close),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                        }
                    });
            builder.setNegativeButton(mContext.getString(R.string.button_help),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();

                            // go to help
                            mContext.startActivity(new Intent(mContext, HelpActivity.class));
                        }
                    });
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
        }
    }
}
