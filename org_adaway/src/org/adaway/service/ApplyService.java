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

import org.adaway.R;
import org.adaway.helper.PreferencesHelper;
import org.adaway.provider.ProviderHelper;
import org.adaway.provider.AdAwayContract.HostsSources;
import org.adaway.ui.BaseActivity;
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

import com.commonsware.cwac.wakeful.WakefulIntentService;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.widget.RemoteViews;

public class ApplyService extends WakefulIntentService {
    private Context mService;
    private Notification mApplyNotification;
    private NotificationManager mNotificationManager;

    String mCurrentUrl;

    // Notification id
    private static final int PROGRESS_NOTIFICATION_ID = 10;
    private static final int RESULT_NOTIFICATION_ID = 11;

    public ApplyService() {
        super("AdAwayApplyService");
    }

    @Override
    public void doWakefulWork(Intent intent) {
        mService = this;

        mNotificationManager = (NotificationManager) mService.getApplicationContext()
                .getSystemService(Context.NOTIFICATION_SERVICE);

        // download files with download method
        int downloadResult = download();

        Log.d(Constants.TAG, "download result: " + downloadResult);

        if (downloadResult == ReturnCodes.SUCCESS) {
            // Apply files by apply method
            int applyResult = apply();

            cancelProgressNotification();
            showNotificationBasedOnResult(applyResult, null);
        } else if (downloadResult == ReturnCodes.DOWNLOAD_FAIL) {
            cancelProgressNotification();
            // extra information is current url, to show it when it fails
            showNotificationBasedOnResult(downloadResult, mCurrentUrl);
        } else {
            cancelProgressNotification();
            showNotificationBasedOnResult(downloadResult, null);
        }
    }

    private int download() {
        showProgressNotification(mService.getString(R.string.download_dialog),
                mService.getString(R.string.download_dialog));

        Cursor enabledHostsSourcesCursor;

        byte data[];
        int count;
        long currentLastModifiedOnline;

        int returnCode = ReturnCodes.SUCCESS; // default return code

        if (Utils.isAndroidOnline(mService)) {
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
                            setProgressNotificationText(mCurrentUrl); // update UI

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
                                Log.e(Constants.TAG, "Exception on flush and closing streams: " + e);
                                e.printStackTrace();
                            }
                        }

                    } while (enabledHostsSourcesCursor.moveToNext());
                } else {
                    // cursor empty
                    returnCode = ReturnCodes.EMPTY_HOSTS_SOURCES;
                }

                // close cursor in the end
                if (enabledHostsSourcesCursor != null && !enabledHostsSourcesCursor.isClosed()) {
                    enabledHostsSourcesCursor.close();
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

    int apply() {
        showProgressNotification(mService.getString(R.string.apply_dialog),
                mService.getString(R.string.apply_dialog));

        int returnCode = ReturnCodes.SUCCESS; // default return code

        try {
            /* PARSE: parse hosts files to sets of hostnames and comments */
            setProgressNotificationText(mService.getString(R.string.apply_dialog_hostnames));

            FileInputStream fis = mService.openFileInput(Constants.DOWNLOADED_HOSTS_FILENAME);

            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));

            HostsParser parser = new HostsParser(reader);
            HashSet<String> hostnames = parser.getBlacklist();

            fis.close();

            setProgressNotificationText(mService.getString(R.string.apply_dialog_lists));

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

            // get sources list
            ArrayList<String> enabledHostsSources = ProviderHelper
                    .getEnabledHostsSourcesArrayList(mService);
            Log.d(Constants.TAG, "Enabled hosts sources list: " + enabledHostsSources.toString());

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
            setProgressNotificationText(mService.getString(R.string.apply_dialog_hosts));

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

            returnCode = ReturnCodes.PRIVATE_FILE_FAIL;
        } catch (IOException e) {
            Log.e(Constants.TAG, "files can not be written or read");
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();

            returnCode = ReturnCodes.PRIVATE_FILE_FAIL;
        }

        // delete downloaded hosts file from private storage
        mService.deleteFile(Constants.DOWNLOADED_HOSTS_FILENAME);

        /* APPLY: apply hosts file using RootTools in copyHostsFile() */
        setProgressNotificationText(mService.getString(R.string.apply_dialog_apply));

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
        mService.deleteFile(Constants.HOSTS_FILENAME);

        /*
         * Set last_modified_local dates in database to last_modified_online, got in download task
         */
        ProviderHelper.updateAllEnabledHostsSourcesLastModifiedLocalFromOnline(mService);

        /* check if hosts file is applied with chosen method */
        // check only if everything before was successful
        if (returnCode == ReturnCodes.SUCCESS) {
            if (PreferencesHelper.getApplyMethod(mService).equals("writeToSystem")) {

                /* /system/etc/hosts */

                if (!ApplyUtils.isHostsFileCorrect(mService, Constants.ANDROID_SYSTEM_ETC_HOSTS)) {
                    returnCode = ReturnCodes.APPLY_FAIL;
                }
            } else if (PreferencesHelper.getApplyMethod(mService).equals("writeToDataData")) {

                /* /data/data/hosts */

                if (!ApplyUtils.isHostsFileCorrect(mService, Constants.ANDROID_DATA_DATA_HOSTS)) {
                    returnCode = ReturnCodes.APPLY_FAIL;
                } else {
                    if (!ApplyUtils.isSymlinkCorrect(Constants.ANDROID_DATA_DATA_HOSTS)) {
                        returnCode = ReturnCodes.SYMLINK_MISSING;
                    }
                }
            } else if (PreferencesHelper.getApplyMethod(mService).equals("customTarget")) {

                /* custom target */

                String customTarget = PreferencesHelper.getCustomTarget(mService);

                if (!ApplyUtils.isHostsFileCorrect(mService, customTarget)) {
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

    /**
     * Creates custom made notification with progress
     */
    private void showProgressNotification(String notificationText, String startTitle) {
        // configure the intent
        Intent intent = new Intent(mService, BaseActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(mService.getApplicationContext(),
                0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        // add app name to notificationText
        notificationText = mService.getString(R.string.app_name) + ": " + notificationText;

        // configure the notification
        mApplyNotification = new Notification(R.drawable.status_bar_icon, notificationText,
                System.currentTimeMillis());
        mApplyNotification.flags = mApplyNotification.flags | Notification.FLAG_ONGOING_EVENT
                | Notification.FLAG_ONLY_ALERT_ONCE;
        mApplyNotification.contentView = new RemoteViews(mService.getPackageName(),
                R.layout.apply_notification);
        mApplyNotification.contentIntent = pendingIntent;

        // add app name to title
        String startTitleWithAppName = mService.getString(R.string.app_name) + ": " + startTitle;

        mApplyNotification.contentView.setTextViewText(R.id.apply_notification_title,
                startTitleWithAppName);

        // update status in BaseActivity with Broadcast
        BaseActivity.updateStatusIconAndTextAndSubtitle(mService, ReturnCodes.CHECKING, startTitle,
                "");

        mNotificationManager.notify(PROGRESS_NOTIFICATION_ID, mApplyNotification);
    }

    private void setProgressNotificationText(String text) {
        mApplyNotification.contentView.setTextViewText(R.id.apply_notification_text, text);

        // update status in BaseActivity with Broadcast
        BaseActivity.updateStatusSubtitle(mService, text);

        // inform the progress notification of updates in progress
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
        contentTitle = mService.getString(R.string.app_name) + ": " + contentTitle;

        Notification notification = new Notification(icon, contentTitle, when);
        notification.flags |= Notification.FLAG_AUTO_CANCEL;

        Context context = mService;
        Intent notificationIntent = new Intent(mService, BaseActivity.class);

        // give postApplyingStatus with intent
        notificationIntent.putExtra(BaseActivity.EXTRA_APPLYING_RESULT, applyingResult);
        notificationIntent.putExtra(BaseActivity.EXTRA_FAILING_URL, failingUrl);

        PendingIntent contentIntent = PendingIntent.getActivity(mService, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);

        mNotificationManager.notify(RESULT_NOTIFICATION_ID, notification);
    }

    /**
     * Show notification based on result after processing download and apply
     * 
     * @param result
     */
    private void showNotificationBasedOnResult(int result, String failingUrl) {
        if (result == ReturnCodes.SUCCESS) {
            // only show if reboot dialog is not disabled in preferences
            if (!PreferencesHelper.getNeverReboot(mService)) {
                if (Utils.isInForeground(mService)) {
                    // start BaseActivity with result
                    Intent resultIntent = new Intent(mService, BaseActivity.class);
                    resultIntent.putExtra(BaseActivity.EXTRA_APPLYING_RESULT, result);
                    resultIntent.addFlags(Intent.FLAG_FROM_BACKGROUND);
                    resultIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(resultIntent);
                } else {
                    // show notification
                    showResultNotification(mService.getString(R.string.apply_success_title),
                            mService.getString(R.string.apply_success), result, null);
                }

                // update status in BaseActivity with Broadcast
                BaseActivity.updateStatusEnabled(mService);
            }
        } else if (result == ReturnCodes.DOWNLOAD_FAIL) {
            if (Utils.isInForeground(mService)) {
                // start BaseActivity with result
                Intent resultIntent = new Intent(mService, BaseActivity.class);
                resultIntent.putExtra(BaseActivity.EXTRA_APPLYING_RESULT, result);
                resultIntent.putExtra(BaseActivity.EXTRA_FAILING_URL, failingUrl);
                resultIntent.addFlags(Intent.FLAG_FROM_BACKGROUND);
                resultIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(resultIntent);
            } else {
                // extra information for intent: url
                showResultNotification(mService.getString(R.string.download_fail_title),
                        mService.getString(R.string.download_fail), result, failingUrl);
            }

            BaseActivity.updateStatusIconAndTextAndSubtitle(mService, ReturnCodes.DOWNLOAD_FAIL,
                    mService.getString(R.string.status_download_fail),
                    mService.getString(R.string.status_download_fail_subtitle) + " " + failingUrl);
        } else {
            String postTitle = "";
            String postMessage = "";
            switch (result) {
            case ReturnCodes.SYMLINK_MISSING:
                postTitle = mService.getString(R.string.apply_symlink_missing_title);
                postMessage = mService.getString(R.string.apply_symlink_missing);
                break;
            case ReturnCodes.NO_CONNECTION:
                postTitle = mService.getString(R.string.no_connection_title);
                postMessage = mService.getString(R.string.no_connection);
                break;
            case ReturnCodes.EMPTY_HOSTS_SOURCES:
                postTitle = mService.getString(R.string.no_sources_title);
                postMessage = mService.getString(R.string.no_sources);
                break;
            case ReturnCodes.APPLY_FAIL:
                postTitle = mService.getString(R.string.apply_fail_title);
                postMessage = mService.getString(R.string.apply_fail);
                break;
            case ReturnCodes.PRIVATE_FILE_FAIL:
                postTitle = mService.getString(R.string.apply_private_file_fail_title);
                postMessage = mService.getString(R.string.apply_private_file_fail);
                break;
            case ReturnCodes.NOT_ENOUGH_SPACE:
                postTitle = mService.getString(R.string.apply_not_enough_space_title);
                postMessage = mService.getString(R.string.apply_not_enough_space);
                break;
            case ReturnCodes.REMOUNT_FAIL:
                postTitle = mService.getString(R.string.apply_remount_fail_title);
                postMessage = mService.getString(R.string.apply_remount_fail);
                break;
            case ReturnCodes.COPY_FAIL:
                postTitle = mService.getString(R.string.apply_copy_fail_title);
                postMessage = mService.getString(R.string.apply_copy_fail);
                break;
            }

            if (Utils.isInForeground(mService)) {
                // start BaseActivity with result
                Intent resultIntent = new Intent(mService, BaseActivity.class);
                resultIntent.putExtra(BaseActivity.EXTRA_APPLYING_RESULT, result);
                resultIntent.addFlags(Intent.FLAG_FROM_BACKGROUND);
                resultIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(resultIntent);
            } else {
                // show notification
                showResultNotification(postTitle, postMessage, result, null);
            }

            BaseActivity.updateStatusDisabled(mService);
        }
    }

    /**
     * Shows dialog and further information how to proceed after the applying process has ended and
     * the user clicked on the notification. This is based on the result from the apply process.
     * 
     * @param result
     */
    public static void processApplyingResult(final Context context, int result, String failingUrl) {
        if (result == ReturnCodes.SUCCESS) {
            BaseActivity.updateStatusEnabled(context);

            Utils.rebootQuestion(context, R.string.apply_success_title, R.string.apply_success);
        } else if (result == ReturnCodes.SYMLINK_MISSING) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(R.string.apply_symlink_missing_title);
            builder.setMessage(context.getString(R.string.apply_symlink_missing));
            builder.setIcon(android.R.drawable.ic_dialog_info);
            builder.setPositiveButton(context.getString(R.string.button_yes),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            tryToCreateSymlink(context);
                        }
                    });
            builder.setNegativeButton(context.getString(R.string.button_no),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();

                            BaseActivity.updateStatusDisabled(context);
                        }
                    });
            AlertDialog question = builder.create();
            question.show();
        } else {
            BaseActivity.updateStatusDisabled(context);

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setPositiveButton(context.getString(R.string.button_close),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                        }
                    });
            builder.setNegativeButton(context.getString(R.string.button_help),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();

                            // go to help
                            context.startActivity(new Intent(context, HelpActivity.class));
                        }
                    });

            String postTitle = "";
            String postMessage = "";
            switch (result) {
            case ReturnCodes.NO_CONNECTION:
                postTitle = context.getString(R.string.no_connection_title);
                postMessage = context.getString(R.string.no_connection);
                break;
            case ReturnCodes.DOWNLOAD_FAIL:
                postTitle = context.getString(R.string.download_fail_title);
                if (failingUrl != null) {
                    postMessage = context.getString(R.string.download_fail) + "\n" + failingUrl;
                } else {
                    postMessage = context.getString(R.string.download_fail);
                }
                break;
            case ReturnCodes.EMPTY_HOSTS_SOURCES:
                postTitle = context.getString(R.string.no_sources_title);
                postMessage = context.getString(R.string.no_sources);
                break;
            case ReturnCodes.APPLY_FAIL:
                postTitle = context.getString(R.string.apply_fail_title);
                postMessage = context.getString(R.string.apply_fail);
                break;
            case ReturnCodes.PRIVATE_FILE_FAIL:
                postTitle = context.getString(R.string.apply_private_file_fail_title);
                postMessage = context.getString(R.string.apply_private_file_fail);
                break;
            case ReturnCodes.NOT_ENOUGH_SPACE:
                postTitle = context.getString(R.string.apply_not_enough_space_title);
                postMessage = context.getString(R.string.apply_not_enough_space);
                break;
            case ReturnCodes.REMOUNT_FAIL:
                postTitle = context.getString(R.string.apply_remount_fail_title);
                postMessage = context.getString(R.string.apply_remount_fail);
                break;
            case ReturnCodes.COPY_FAIL:
                postTitle = context.getString(R.string.apply_copy_fail_title);
                postMessage = context.getString(R.string.apply_copy_fail);
                break;
            }
            postMessage += "\n\n" + context.getString(R.string.apply_help);
            builder.setTitle(postTitle);
            builder.setMessage(postMessage);

            AlertDialog alertDialog = builder.create();
            alertDialog.show();
        }
    }

    /**
     * Trying to create symlink and displays dialogs on fail
     */
    private static void tryToCreateSymlink(final Context context) {
        boolean success = true;

        try {
            // symlink to /system/etc/hosts, based on target
            if (PreferencesHelper.getApplyMethod(context).equals("writeToDataData")) {
                ApplyUtils.createSymlink(Constants.ANDROID_DATA_DATA_HOSTS);
            } else if (PreferencesHelper.getApplyMethod(context).equals("customTarget")) {
                ApplyUtils.createSymlink(PreferencesHelper.getCustomTarget(context));
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
            if (ApplyUtils.isHostsFileCorrect(context, Constants.ANDROID_SYSTEM_ETC_HOSTS)) {
                success = true;
            } else {
                success = false;
            }
        }

        if (success) {
            BaseActivity.updateStatusEnabled(context);

            Utils.rebootQuestion(context, R.string.apply_symlink_successful_title,
                    R.string.apply_symlink_successful);
        } else {
            BaseActivity.updateStatusDisabled(context);

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(R.string.apply_symlink_fail_title);
            builder.setMessage(context.getString(R.string.apply_symlink_fail) + "\n\n"
                    + context.getString(R.string.apply_help));
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setPositiveButton(context.getString(R.string.button_close),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                        }
                    });
            builder.setNegativeButton(context.getString(R.string.button_help),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();

                            // go to help
                            context.startActivity(new Intent(context, HelpActivity.class));
                        }
                    });
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
        }
    }

}
