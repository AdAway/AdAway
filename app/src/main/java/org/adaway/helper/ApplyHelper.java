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

package org.adaway.helper;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.v4.app.NotificationCompat;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.ui.MainActivity;
import org.adaway.ui.home.HomeFragment;
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ApplyHelper {
    private static final int APPLY_NOTIFICATION_ID = 20;
    private final Context mContext;
    private final NotificationManager mNotificationManager;
    private final HostsSourceDao mHostsSourceDao;
    private final HostListItemDao mHostListItemDao;
    private int mNumberOfFailedDownloads;
    private int mNumberOfDownloads;

    /**
     * Constructor.
     *
     * @param context The application context.
     */
    public ApplyHelper(Context context) {
        // Store context
        this.mContext = context;
        // Get dao
        AppDatabase database = AppDatabase.getInstance(context);
        this.mHostsSourceDao = database.hostsSourceDao();
        this.mHostListItemDao = database.hostsListItemDao();
        // Get notification manager
        this.mNotificationManager = (NotificationManager) mContext.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
    }

    /**
     * Call {@link #apply()} asynchronously.
     *
     * @param context The application context.
     */
    public static void applyAsync(Context context) {
        new AsyncApplyHelper(context).execute();
    }

    /**
     * Download and apply enabled hosts file as system hosts file.
     */
    public void apply() {
        String successfulDownloads = null;
        // Download hosts files
        int result = this.download();
        Log.d(Constants.TAG, "Download result: " + result);
        // Check if hosts files was successfully downloaded
        if (result == StatusCodes.SUCCESS) {
            // Apply hosts files
            result = this.applyHostsFile();
            Log.d(Constants.TAG, "Apply result: " + result);
            // Compute successful downloads
            successfulDownloads = (mNumberOfDownloads - mNumberOfFailedDownloads) + "/" + mNumberOfDownloads;
        }
        // Cancel apply notification
        this.cancelApplyNotification();
        // Show result notification
        ResultHelper.showNotificationBasedOnResult(mContext, result, successfulDownloads);
    }

    /**
     * Downloads files from hosts sources
     *
     * @return The resulting {@link StatusCodes}.
     */
    private int download() {
        // Check connection status
        if (!Utils.isAndroidOnline(mContext)) {
            return StatusCodes.NO_CONNECTION;
        }
        // Initialize statuses
        this.mNumberOfFailedDownloads = 0;
        this.mNumberOfDownloads = 0;
        int statusCode = StatusCodes.SUCCESS;
        // Show apply notification
        this.showApplyNotification(
                this.mContext,
                this.mContext.getString(R.string.download_dialog),
                this.mContext.getString(R.string.download_dialog),
                this.mContext.getString(R.string.download_dialog)
        );
        // Open local private file and get cursor to enabled hosts sources
        try (FileOutputStream out = this.mContext.openFileOutput(Constants.DOWNLOADED_HOSTS_FILENAME, Context.MODE_PRIVATE)) {
            // Download each hosts source
            for (HostsSource hostsSource : this.mHostsSourceDao.getEnabled()) {
                this.downloadHost(hostsSource, out);
            }
            // Check if all downloads failed
            if (this.mNumberOfDownloads == mNumberOfFailedDownloads && this.mNumberOfDownloads != 0) {
                // Mark downloads failed
                statusCode = StatusCodes.DOWNLOAD_FAIL;
            }
        } catch (IOException exception) {
            Log.e(Constants.TAG, "Private file can not be created." + exception);
            statusCode = StatusCodes.PRIVATE_FILE_FAIL;
        }
        // Return status code
        return statusCode;
    }

    /**
     * Download an hosts file and write it to a private file.
     *
     * @param hostsSource The hosts source to download.
     * @param out         The output stream to write the hosts file content to.
     */
    private void downloadHost(HostsSource hostsSource, FileOutputStream out) {
        // Increase number downloads
        this.mNumberOfDownloads++;
        // Get hosts file URL
        String hostsFileUrl = hostsSource.getUrl();
        Log.v(Constants.TAG, "Downloading hosts file: " + hostsFileUrl);
        // Notify apply
        this.updateApplyNotification(
                this.mContext,
                this.mContext.getString(R.string.download_dialog),
                hostsFileUrl
        );
        // Create connection
        URLConnection connection;
        try {
            URL mURL = new URL(hostsFileUrl);
            connection = mURL.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.connect();
        } catch (IOException exception) {
            Log.e(Constants.TAG, "Unable to connect to " + hostsFileUrl + ".", exception);
            // Increase number of failed downloads
            this.mNumberOfFailedDownloads++;
            // Update last_modified_online of failed download to 0 (not available)
            this.mHostsSourceDao.updateOnlineModificationDate(hostsFileUrl, null);
            return;
        }
        // Download hosts file content
        try (InputStream is = connection.getInputStream();
             BufferedInputStream bis = new BufferedInputStream(is)) {
            // Read all content into output stream
            byte[] data = new byte[1024];
            int count;
            // run while only when thread is not cancelled
            while ((count = bis.read(data)) != -1) {
                out.write(data, 0, count);
            }
            // add line separator to add files together in one file
            out.write(Constants.LINE_SEPARATOR.getBytes());
            // Save last modified online for later use
            long currentLastModifiedOnline = connection.getLastModified();
            // Update
            this.mHostsSourceDao.updateOnlineModificationDate(hostsFileUrl, new Date(currentLastModifiedOnline));
        } catch (IOException exception) {
            Log.e(
                    Constants.TAG,
                    "Exception while downloading hosts file from " + hostsFileUrl + ".",
                    exception
            );
            // Increase number of failed downloads
            this.mNumberOfFailedDownloads++;
            // Update last_modified_online of failed download to 0 (not available)
            this.mHostsSourceDao.updateOnlineModificationDate(hostsFileUrl, null);
        }
    }

    /**
     * Apply hosts file.
     *
     * @return The resulting {@link StatusCodes}.
     */
    private int applyHostsFile() {
        showApplyNotification(mContext, mContext.getString(R.string.apply_dialog),
                mContext.getString(R.string.apply_dialog),
                mContext.getString(R.string.apply_dialog_hostnames));

        int returnCode = StatusCodes.SUCCESS; // default return code
        BufferedOutputStream bos = null;

        try {
            /* PARSE: parse hosts files to sets of host names and comments */

            FileInputStream fis = mContext.openFileInput(Constants.DOWNLOADED_HOSTS_FILENAME);

            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));

            // Use whitelist and/or redirection rules from hosts sources only if enabled in preferences
            HostsParser parser = new HostsParser(reader, PreferenceHelper.getWhitelistRules(mContext), PreferenceHelper.getRedirectionRules(mContext));

            fis.close();

            updateApplyNotification(mContext, mContext.getString(R.string.apply_dialog),
                    mContext.getString(R.string.apply_dialog_lists));

            /* READ DATABSE CONTENT */


            Set<String> blackListHosts = new HashSet<>(this.mHostListItemDao.getEnabledBlackListHosts());
            Set<String> whiteListHosts = new HashSet<>(this.mHostListItemDao.getEnabledWhiteListHosts());
            Map<String, String> redirectionListHosts = Stream.of(this.mHostListItemDao.getEnabledRedirectionList())
                    .collect(Collectors.toMap(HostListItem::getHost, HostListItem::getRedirection));
            // add whitelist from db
            parser.addWhitelist(blackListHosts);
            // add blacklist from db
            parser.addBlacklist(whiteListHosts);
            // add redirection list from db
            parser.addRedirectionList(redirectionListHosts);

            // get hosts sources list from db
            List<String> enabledHostsSources = Stream.of(this.mHostsSourceDao.getEnabled())
                    .map(HostsSource::getUrl)
                    .collect(Collectors.toList());

            Log.d(Constants.TAG, "Enabled hosts sources list: " + enabledHostsSources.toString());

            // compile lists (removing whitelist entries, etc.)
            parser.compileList();

            /* BUILD: build one hosts file out of sets and preferences */
            updateApplyNotification(mContext, mContext.getString(R.string.apply_dialog),
                    mContext.getString(R.string.apply_dialog_hosts));

            FileOutputStream fos = mContext.openFileOutput(Constants.HOSTS_FILENAME,
                    Context.MODE_PRIVATE);

            bos = new BufferedOutputStream(fos);

            // build current timestamp for header
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            Date now = new Date();

            // add adaway header
            String header = Constants.HEADER1 + Constants.LINE_SEPARATOR + "# " +
                    formatter.format(now) + Constants.LINE_SEPARATOR + Constants.HEADER2 +
                    Constants.LINE_SEPARATOR + Constants.HEADER_SOURCES;
            bos.write(header.getBytes());

            // write sources into header
            String source;
            for (String host : enabledHostsSources) {
                source = Constants.LINE_SEPARATOR + "# " + host;
                bos.write(source.getBytes());
            }

            bos.write(Constants.LINE_SEPARATOR.getBytes());

            String redirectionIP = PreferenceHelper.getRedirectionIP(mContext);

            // add "127.0.0.1 localhost" entry
            String localhost = Constants.LINE_SEPARATOR + Constants.LOCALHOST_IPv4 + " "
                    + Constants.LOCALHOST_HOSTNAME + Constants.LINE_SEPARATOR
                    + Constants.LOCALHOST_IPv6 + " " + Constants.LOCALHOST_HOSTNAME;
            bos.write(localhost.getBytes());

            bos.write(Constants.LINE_SEPARATOR.getBytes());

            // write hostnames
            String line;
            String linev6;
            if (PreferenceHelper.getEnableIpv6(mContext)) {
                for (String hostname : parser.getBlacklist()) {
                    line = Constants.LINE_SEPARATOR + redirectionIP + " " + hostname;
                    linev6 = Constants.LINE_SEPARATOR + "::1" + " " + hostname;
                    bos.write(line.getBytes());
                    bos.write(linev6.getBytes());
                }
            } else {
                for (String hostname : parser.getBlacklist()) {
                    line = Constants.LINE_SEPARATOR + redirectionIP + " " + hostname;
                    bos.write(line.getBytes());
                }
            }

            /* REDIRECTION LIST: write redirection items */
            String redirectionItemHostname;
            String redirectionItemIP;
            for (HashMap.Entry<String, String> item : parser.getRedirectionList().entrySet()) {
                redirectionItemHostname = item.getKey();
                redirectionItemIP = item.getValue();

                line = Constants.LINE_SEPARATOR + redirectionItemIP + " " + redirectionItemHostname;
                bos.write(line.getBytes());
            }

            // hosts file has to end with new line, when not done last entry won't be
            // recognized
            bos.write(Constants.LINE_SEPARATOR.getBytes());

        } catch (FileNotFoundException e) {
            Log.e(Constants.TAG, "file to read or file to write could not be found", e);

            returnCode = StatusCodes.PRIVATE_FILE_FAIL;
        } catch (IOException e) {
            Log.e(Constants.TAG, "files can not be written or read", e);

            returnCode = StatusCodes.PRIVATE_FILE_FAIL;
        } finally {
            try {
                if (bos != null) {
                    bos.flush();
                    bos.close();
                }
            } catch (Exception e) {
                Log.e(Constants.TAG, "Error closing output streams", e);
            }
        }

        // delete downloaded hosts file from private storage
        mContext.deleteFile(Constants.DOWNLOADED_HOSTS_FILENAME);

        /* APPLY: apply hosts file using RootTools in copyHostsFile() */
        updateApplyNotification(mContext, mContext.getString(R.string.apply_dialog),
                mContext.getString(R.string.apply_dialog_apply));

        Shell rootShell = null;
        try {
            rootShell = Shell.startRootShell();
        } catch (Exception e) {
            Log.e(Constants.TAG, "Problem opening a root shell!", e);
        }

        // copy build hosts file with RootTools, based on target from preferences
        try {
            switch (PreferenceHelper.getApplyMethod(mContext)) {
                case "writeToSystem":
                    ApplyUtils.copyHostsFile(mContext, Constants.ANDROID_SYSTEM_ETC_HOSTS, rootShell);
                    break;
                case "writeToDataData":
                    ApplyUtils.copyHostsFile(mContext, Constants.ANDROID_DATA_DATA_HOSTS, rootShell);
                    break;
                case "writeToData":
                    ApplyUtils.copyHostsFile(mContext, Constants.ANDROID_DATA_HOSTS, rootShell);
                    break;
                case "customTarget":
                    String customTarget = PreferenceHelper.getCustomTarget(mContext);
                    ApplyUtils.copyHostsFile(mContext, customTarget, rootShell);
                    break;
                default:
                    break;
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
        mContext.deleteFile(Constants.HOSTS_FILENAME);

        /*
         * Set last_modified_local dates in database to last_modified_online, got in download task
         */
        this.mHostsSourceDao.updateLocalModificationDatesToOnlineDates();

        /* check if hosts file is applied with chosen method */
        // check only if everything before was successful
        if (returnCode == StatusCodes.SUCCESS) {
            switch (PreferenceHelper.getApplyMethod(mContext)) {
                case "writeToSystem":
                    /* /system/etc/hosts */
                    if (!ApplyUtils.isHostsFileCorrect(Constants.ANDROID_SYSTEM_ETC_HOSTS)) {
                        returnCode = StatusCodes.APPLY_FAIL;
                    }
                    break;
                case "writeToDataData":
                    /* /data/data/hosts */
                    if (!ApplyUtils.isHostsFileCorrect(Constants.ANDROID_DATA_DATA_HOSTS)) {
                        returnCode = StatusCodes.APPLY_FAIL;
                    } else {
                        if (!ApplyUtils.isSymlinkCorrect(Constants.ANDROID_DATA_DATA_HOSTS, rootShell)) {
                            returnCode = StatusCodes.SYMLINK_MISSING;
                        }
                    }
                    break;
                case "writeToData":
                    /* /data/data/hosts */
                    if (!ApplyUtils.isHostsFileCorrect(Constants.ANDROID_DATA_HOSTS)) {
                        returnCode = StatusCodes.APPLY_FAIL;
                    } else {
                        if (!ApplyUtils.isSymlinkCorrect(Constants.ANDROID_DATA_HOSTS, rootShell)) {
                            returnCode = StatusCodes.SYMLINK_MISSING;
                        }
                    }
                    break;
                case "customTarget":
                    /* custom target */
                    String customTarget = PreferenceHelper.getCustomTarget(mContext);
                    if (!ApplyUtils.isHostsFileCorrect(customTarget)) {
                        returnCode = StatusCodes.APPLY_FAIL;
                    } else {
                        if (!ApplyUtils.isSymlinkCorrect(customTarget, rootShell)) {
                            returnCode = StatusCodes.SYMLINK_MISSING;
                        }
                    }
                    break;
                default:
                    break;
            }
        }

        try {
            if (rootShell != null) {
                rootShell.close();
            }
        } catch (Exception e) {
            Log.e(Constants.TAG, "Problem closing the root shell!", e);
        }

        /* check if APN proxy is set */
        if (returnCode == StatusCodes.SUCCESS) {
            if (ApplyUtils.isApnProxySet(mContext)) {
                Log.d(Constants.TAG, "APN proxy is set!");
                returnCode = StatusCodes.APN_PROXY;
            }
        }

        return returnCode;
    }

    /**
     * Creates custom made notification with progress
     */
    private void showApplyNotification(Context mContext, String tickerText, String contentTitle,
                                       String contentText) {
        // configure the intent
        Intent intent = new Intent(mContext, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(mContext.getApplicationContext(),
                0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        // add app name to notificationText
        tickerText = mContext.getString(R.string.app_name) + ": " + tickerText;
        int icon = R.drawable.status_bar_icon;
        long when = System.currentTimeMillis();

        // add app name to title
        String contentTitleWithAppName = mContext.getString(R.string.app_name) + ": "
                + contentTitle;

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(mContext)
                .setSmallIcon(icon).setContentTitle(contentTitleWithAppName).setTicker(tickerText)
                .setWhen(when).setOngoing(true).setOnlyAlertOnce(true).setContentText(contentText);

        mNotificationManager.notify(APPLY_NOTIFICATION_ID, mBuilder.build());

        mBuilder.setContentIntent(contentIntent);

        // update status in MainActivity with Broadcast
        HomeFragment.setStatusBroadcast(mContext, contentTitle, contentText, StatusCodes.CHECKING);
    }

    private void updateApplyNotification(Context mContext, String contentTitle, String contentText) {
        // configure the intent
        Intent intent = new Intent(mContext, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(mContext.getApplicationContext(),
                0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        int icon = R.drawable.status_bar_icon;

        // add app name to title
        String contentTitleWithAppName = mContext.getString(R.string.app_name) + ": "
                + contentTitle;

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(mContext)
                .setSmallIcon(icon).setContentTitle(contentTitleWithAppName)
                .setContentText(contentText);

        mNotificationManager.notify(APPLY_NOTIFICATION_ID, mBuilder.build());

        mBuilder.setContentIntent(contentIntent);

        // update status in MainActivity with Broadcast
        HomeFragment.setStatusBroadcast(mContext, contentTitle, contentText, StatusCodes.CHECKING);
    }

    private void cancelApplyNotification() {
        mNotificationManager.cancel(APPLY_NOTIFICATION_ID);
    }

    /**
     * This class is an async task to apply hosts.
     *
     * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
     */
    private static class AsyncApplyHelper extends AsyncTask<Void, Void, Void> {
        /**
         * A weak reference to application context.
         */
        private WeakReference<Context> mWeakContext;

        /**
         * Constructor.
         *
         * @param context The application context.
         */
        private AsyncApplyHelper(Context context) {
            // Store context into weak reference to prevent memory leak
            this.mWeakContext = new WeakReference<>(context);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            // Get context from weak reference
            Context context = this.mWeakContext.get();
            if (context == null) {
                return null;
            }
            // Call apply
            new ApplyHelper(context).apply();
            // Return void
            return null;
        }
    }
}
