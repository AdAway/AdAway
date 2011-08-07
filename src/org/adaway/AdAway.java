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

package org.adaway;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import org.adaway.utils.Constants;
import org.adaway.utils.DatabaseHelper;
import org.adaway.utils.Helper;
import org.adaway.utils.HostsParser;
import org.adaway.utils.SharedPrefs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.stericson.RootTools.RootTools;

public class AdAway extends Activity {
    private Context mContext;
    private DatabaseHelper mDatabaseHelper;

    private TextView mStatusText;
    private TextView mStatusSubtitle;
    private ProgressBar mStatusProgress;
    private ImageView mStatusIcon;
    AsyncTask<String, Integer, Integer> mStatusTask;

    protected static final int RETURN_SUCCESS = 0;
    protected static final int RETURN_PRIVATE_FILE_FAIL = 1;
    protected static final int RETURN_UPDATE_AVAILABLE = 2;
    protected static final int RETURN_ENABLED = 3;
    protected static final int RETURN_DISABLED = 4;
    protected static final int RETURN_DOWNLOAD_FAIL = 5;
    protected static final int RETURN_NO_CONNECTION = 6;
    protected static final int RETURN_APPLY_FAILED = 7;

    /**
     * Override onDestroy to cancel AsyncTask that checks for updates
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        // cancel task
        if (mStatusTask != null) {
            mStatusTask.cancel(true);
        }
    }

    /**
     * Don't recreate activity on orientation change, it will break AsyncTask. Using possibility 4
     * from http://blog.doityourselfandroid
     * .com/2010/11/14/handling-progress-dialogs-and-screen-orientation-changes/
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setContentView(R.layout.main);

        mStatusText = (TextView) findViewById(R.id.status_text);
        mStatusSubtitle = (TextView) findViewById(R.id.status_subtitle);
        mStatusProgress = (ProgressBar) findViewById(R.id.status_progress);
        mStatusIcon = (ImageView) findViewById(R.id.status_icon);

        // check again for update
        checkOnCreate();
    }

    /**
     * Inflate Menu
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    /**
     * Menu Options
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
        case R.id.menu_hosts_sources:
            startActivity(new Intent(this, HostsSources.class));
            return true;

        case R.id.menu_blacklist:
            startActivity(new Intent(this, Blacklist.class));
            return true;

        case R.id.menu_whitelist:
            startActivity(new Intent(this, Whitelist.class));
            return true;

        case R.id.menu_redirection_list:
            startActivity(new Intent(this, RedirectionList.class));
            return true;

        case R.id.menu_preferences:
            startActivity(new Intent(this, Preferences.class));
            return true;

        case R.id.menu_about:
            showAboutDialog();
            return true;

        default:
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mContext = this;

        mStatusText = (TextView) findViewById(R.id.status_text);
        mStatusSubtitle = (TextView) findViewById(R.id.status_subtitle);
        mStatusProgress = (ProgressBar) findViewById(R.id.status_progress);
        mStatusIcon = (ImageView) findViewById(R.id.status_icon);

        RootTools.debugMode = false;

        // check for root
        if (Helper.isAndroidRooted(this)) {
            // do background update check
            checkOnCreate();
        }
    }

    /**
     * Run status AsyncTask on create
     */
    private void checkOnCreate() {
        mDatabaseHelper = new DatabaseHelper(mContext);

        // get enabled hosts from database
        ArrayList<String> enabledHosts = mDatabaseHelper.getAllEnabledHostsSources();
        Log.d(Constants.TAG, "Enabled hosts: " + enabledHosts.toString());

        mDatabaseHelper.close();

        // build array out of list
        String[] enabledHostsArray = new String[enabledHosts.size()];
        enabledHosts.toArray(enabledHostsArray);

        if (enabledHosts.size() < 1) {
            Log.d(Constants.TAG, "no hosts sources");
        } else {
            // execute downloading of files
            runStatusTask(enabledHostsArray);
        }
    }

    /**
     * Button Action to download and apply hosts files
     * 
     * @param view
     */
    public void applyOnClick(View view) {
        mDatabaseHelper = new DatabaseHelper(mContext);

        // get enabled hosts from databse
        ArrayList<String> enabledHosts = mDatabaseHelper.getAllEnabledHostsSources();
        Log.d(Constants.TAG, "Enabled hosts: " + enabledHosts.toString());

        mDatabaseHelper.close();

        // build array out of list
        String[] enabledHostsArray = new String[enabledHosts.size()];
        enabledHosts.toArray(enabledHostsArray);

        if (enabledHosts.size() < 1) {
            AlertDialog alertDialog = new AlertDialog.Builder(mContext).create();
            alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
            alertDialog.setTitle(R.string.no_sources_title);
            alertDialog.setMessage(getString(org.adaway.R.string.no_sources));
            alertDialog.setButton(getString(R.string.button_close),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dlg, int sum) {
                            dlg.dismiss();
                        }
                    });
            alertDialog.show();
        } else {
            // execute downloading of files
            runDownloadTask(enabledHostsArray);
        }
    }

    /**
     * Button Action to Revert to default hosts file
     * 
     * @param view
     */
    public void revertOnClick(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.button_revert);
        builder.setMessage(getString(R.string.revert_question));
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setCancelable(false);
        builder.setPositiveButton(getString(R.string.button_yes),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // build standard hosts file
                        try {
                            FileOutputStream fos = openFileOutput(Constants.HOSTS_FILENAME,
                                    Context.MODE_PRIVATE);

                            // default localhost
                            String localhost = Constants.LOCALHOST_IPv4 + " "
                                    + Constants.LOCALHOST_HOSTNAME;
                            fos.write(localhost.getBytes());
                            fos.close();

                            // copy hosts file with RootTools
                            if (!copyHostsFile()) {
                                Log.e(Constants.TAG, "revert: problem with copying hosts file");
                                throw new Exception();
                            }

                            // delete generated hosts file after applying it
                            deleteFile(Constants.HOSTS_FILENAME);

                            // set status to disabled
                            mStatusIcon.setImageResource(R.drawable.status_disabled);
                            mStatusText.setText(R.string.status_disabled);
                            mStatusSubtitle.setText(R.string.status_disabled_subtitle);

                            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                            builder.setTitle(R.string.button_revert);
                            builder.setMessage(getString(R.string.revert_successfull));
                            builder.setIcon(android.R.drawable.ic_dialog_info);
                            builder.setPositiveButton(getString(R.string.button_yes),
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            String commandReboot = "reboot";
                                            List<String> output = null;
                                            try {
                                                output = RootTools.sendShell(commandReboot);
                                            } catch (Exception e) {
                                                Log.e(Constants.TAG, "Exception: " + e);
                                                e.printStackTrace();
                                            }
                                            Log.d(Constants.TAG,
                                                    "output of command: " + output.toString());
                                        }
                                    });
                            builder.setNegativeButton(getString(R.string.button_no),
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            dialog.dismiss();
                                        }
                                    });
                            AlertDialog question = builder.create();
                            question.show();

                        } catch (Exception e) {
                            Log.e(Constants.TAG, "Exception: " + e);
                            e.printStackTrace();

                            AlertDialog alertDialog = new AlertDialog.Builder(mContext).create();
                            alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
                            alertDialog.setTitle(R.string.button_revert);
                            alertDialog.setMessage(getString(org.adaway.R.string.revert_problem));
                            alertDialog.setButton(getString(R.string.button_close),
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dlg, int sum) {
                                            dlg.dismiss();
                                        }
                                    });
                            alertDialog.show();
                        }

                    }
                });
        builder.setNegativeButton(getString(R.string.button_no),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog question = builder.create();
        question.show();
    }

    /**
     * About Dialog of AdAway
     */
    private void showAboutDialog() {
        final Dialog dialog = new Dialog(mContext);
        dialog.requestWindowFeature(Window.FEATURE_LEFT_ICON);
        dialog.setContentView(R.layout.about_dialog);
        dialog.setTitle(R.string.about_title);

        TextView versionText = (TextView) dialog.findViewById(R.id.about_version);
        versionText.setText(getString(R.string.about_version) + " " + getVersion());

        Button closeBtn = (Button) dialog.findViewById(R.id.about_close);
        closeBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                dialog.dismiss();
            }
        });

        dialog.show();
        dialog.setFeatureDrawableResource(Window.FEATURE_LEFT_ICON,
                android.R.drawable.ic_dialog_info);
    }

    /**
     * Get the current package version.
     * 
     * @return The current version.
     */
    private String getVersion() {
        String result = "";
        try {
            PackageManager manager = this.getPackageManager();
            PackageInfo info = manager.getPackageInfo(this.getPackageName(), 0);

            result = String.format("%s (%s)", info.versionName, info.versionCode);
        } catch (NameNotFoundException e) {
            Log.w(Constants.TAG, "Unable to get application version: " + e.getMessage());
            result = "Unable to get application version.";
        }

        return result;
    }

    /**
     * Copy hosts file from private storage of AdAway to internal partition using RootTools
     * 
     * @return <code>true</code> if copying was successful, <code>false</code> if there were some
     *         problems like not enough space.
     */
    private boolean copyHostsFile() {
        String privateDir = getFilesDir().getAbsolutePath();
        String privateFile = privateDir + File.separator + Constants.HOSTS_FILENAME;
        String hostsFile = Constants.ANDROID_HOSTS_PATH + File.separator + Constants.HOSTS_FILENAME;

        String commandCopy = Constants.COMMAND_COPY + " " + privateFile + " " + hostsFile;
        String commandChown = Constants.COMMAND_CHOWN + " " + hostsFile;
        String commandChmod = Constants.COMMAND_CHMOD + " " + hostsFile;
        Log.d(Constants.TAG, "commandCopy: " + commandCopy);
        Log.d(Constants.TAG, "commandChown: " + commandChown);
        Log.d(Constants.TAG, "commandChmod: " + commandChmod);

        // do it with RootTools
        try {
            // check for space on partition
            long size = new File(privateFile).length();
            Log.d(Constants.TAG, "size: " + size);
            if (!Helper.hasEnoughSpaceOnPartition(Constants.ANDROID_HOSTS_PATH, size)) {
                throw new Exception();
            }

            // remount for write access
            RootTools.remount(Constants.ANDROID_HOSTS_PATH, "RW");

            List<String> output;
            // copy
            output = RootTools.sendShell(commandCopy);
            Log.d(Constants.TAG, "output of command: " + output.toString());

            // chown
            output = RootTools.sendShell(commandChown);
            Log.d(Constants.TAG, "output of command: " + output.toString());

            // chmod
            output = RootTools.sendShell(commandChmod);
            Log.d(Constants.TAG, "output of command: " + output.toString());

        } catch (Exception e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();

            return false;
        } finally {
            // after all remount back as read only
            RootTools.remount(Constants.ANDROID_HOSTS_PATH, "RO");
        }

        return true;
    }

    /**
     * AsyncTask to check for updates and determine the status of AdAway, can be executed with many
     * urls as params.
     */
    private void runStatusTask(String... urls) {
        mStatusTask = new AsyncTask<String, Integer, Integer>() {
            private String currentURL;
            private int fileSize;
            private long lastModified = 0;
            private long lastModifiedCurrent;

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                mStatusProgress.setVisibility(View.VISIBLE);
                mStatusText.setText(R.string.status_checking);
                mStatusSubtitle.setText(R.string.status_checking_subtitle);
            }

            private boolean isAndroidOnline() {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo netInfo = cm.getActiveNetworkInfo();
                if (netInfo != null && netInfo.isConnectedOrConnecting()) {
                    return true;
                }
                return false;
            }

            @Override
            protected Integer doInBackground(String... urls) {
                int returnCode = RETURN_ENABLED; // default return code

                if (isAndroidOnline()) {
                    for (String url : urls) {

                        // stop if thread canceled
                        if (isCancelled()) {
                            break;
                        }

                        @SuppressWarnings("unused")
                        InputStream is = null;
                        try {
                            Log.v(Constants.TAG, "Checking hosts file: " + url);

                            /* change URL */
                            currentURL = url;

                            /* build connection */
                            URL mURL = new URL(url);
                            URLConnection connection = mURL.openConnection();

                            fileSize = connection.getContentLength();
                            Log.d(Constants.TAG, "fileSize: " + fileSize);

                            lastModifiedCurrent = connection.getLastModified();

                            Log.d(Constants.TAG, "lastModifiedCurrent: " + lastModifiedCurrent
                                    + " (" + Helper.longToDateString(lastModifiedCurrent) + ")");

                            Log.d(Constants.TAG,
                                    "lastModified: " + lastModified + " ("
                                            + Helper.longToDateString(lastModified) + ")");

                            // set lastModified to the maximum of all lastModifieds
                            if (lastModifiedCurrent > lastModified) {
                                lastModified = lastModifiedCurrent;
                            }

                            // check if file is available
                            connection.connect();
                            is = connection.getInputStream();

                        } catch (Exception e) {
                            Log.e(Constants.TAG, "Exception: " + e);
                            returnCode = RETURN_DOWNLOAD_FAIL;
                            break; // stop for-loop
                        }
                    }
                } else {
                    returnCode = RETURN_NO_CONNECTION;
                }

                /* CHECK if update is necessary */
                DatabaseHelper taskDatabaseHelper = new DatabaseHelper(mContext);

                // get last modified from db
                long lastModifiedDatabase = taskDatabaseHelper.getLastModified();

                taskDatabaseHelper.close();

                Log.d(Constants.TAG,
                        "lastModified: " + lastModified + " ("
                                + Helper.longToDateString(lastModified) + ")");

                Log.d(Constants.TAG, "lastModifiedDatabase: " + lastModifiedDatabase + " ("
                        + Helper.longToDateString(lastModifiedDatabase) + ")");

                // check if maximal lastModified is bigger than the ones in database
                if (lastModified > lastModifiedDatabase) {
                    returnCode = RETURN_UPDATE_AVAILABLE;
                }

                // check if hosts file is applied
                if (!Helper.isHostsFileApplied(mContext)) {
                    returnCode = RETURN_DISABLED;
                }

                return returnCode;
            }

            @Override
            protected void onPostExecute(Integer result) {
                super.onPostExecute(result);

                mStatusProgress.setVisibility(View.GONE);

                Log.d(Constants.TAG, "onPostExecute result: " + result);

                switch (result) {
                case RETURN_UPDATE_AVAILABLE:
                    mStatusIcon.setImageResource(R.drawable.status_update);
                    mStatusIcon.setVisibility(View.VISIBLE);

                    mStatusText.setText(R.string.status_update_available);
                    mStatusSubtitle.setText(R.string.status_update_available_subtitle);
                    break;
                case RETURN_DISABLED:
                    mStatusIcon.setImageResource(R.drawable.status_disabled);
                    mStatusIcon.setVisibility(View.VISIBLE);

                    mStatusText.setText(R.string.status_disabled);
                    mStatusSubtitle.setText(R.string.status_disabled_subtitle);
                    break;
                case RETURN_DOWNLOAD_FAIL:
                    mStatusIcon.setImageResource(R.drawable.status_fail);
                    mStatusIcon.setVisibility(View.VISIBLE);

                    mStatusText.setText(R.string.status_download_fail);
                    mStatusSubtitle.setText(getString(R.string.status_download_fail_subtitle) + " "
                            + currentURL);
                    break;
                case RETURN_NO_CONNECTION:
                    mStatusIcon.setImageResource(R.drawable.status_fail);
                    mStatusIcon.setVisibility(View.VISIBLE);

                    mStatusText.setText(R.string.status_no_connection);
                    mStatusSubtitle.setText(R.string.status_no_connection_subtitle);
                    break;

                default:
                    mStatusIcon.setImageResource(R.drawable.status_enabled);
                    mStatusIcon.setVisibility(View.VISIBLE);

                    mStatusText.setText(R.string.status_enabled);
                    mStatusSubtitle.setText(R.string.status_enabled_subtitle);
                    break;
                }
            }
        };

        mStatusTask.execute(urls);
    }

    /**
     * AsyncTask to download hosts files, can be executed with many urls as params. In onPostExecute
     * an Apply AsyncTask will be started
     */
    private void runDownloadTask(String... urls) {
        AsyncTask<String, Integer, Integer> downloadTask = new AsyncTask<String, Integer, Integer>() {
            private ProgressDialog mDownloadProgressDialog;

            private String currentURL;
            private int fileSize;
            private byte data[];
            private long total;
            private int count;
            private boolean urlChanged;

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                mDownloadProgressDialog = new ProgressDialog(mContext);
                mDownloadProgressDialog.setMessage(getString(R.string.download_dialog));
                mDownloadProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mDownloadProgressDialog.setCancelable(true);
                mDownloadProgressDialog.setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        cancel(true); // cancel thread, now isCancelled() returns true
                    }
                });

                mDownloadProgressDialog.show();

                urlChanged = false;
            }

            private boolean isAndroidOnline() {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo netInfo = cm.getActiveNetworkInfo();
                if (netInfo != null && netInfo.isConnectedOrConnecting()) {
                    return true;
                }
                return false;
            }

            @Override
            protected Integer doInBackground(String... urls) {
                int returnCode = RETURN_SUCCESS; // default return code

                if (isAndroidOnline()) {
                    // output to write into
                    FileOutputStream out = null;

                    try {
                        out = openFileOutput(Constants.DOWNLOADED_HOSTS_FILENAME,
                                Context.MODE_PRIVATE);

                        for (String url : urls) {

                            // stop if thread canceled
                            if (isCancelled()) {
                                break;
                            }

                            InputStream is = null;
                            BufferedInputStream bis = null;
                            try {
                                Log.v(Constants.TAG, "Downloading hosts file: " + url);

                                /* change URL in download dialog */
                                currentURL = url;
                                urlChanged = true;
                                publishProgress(0);

                                /* build connection */
                                URL mURL = new URL(url);
                                // if (mURL.getProtocol() == "http") { // TODO: implement SSL
                                // httpsURLConnection
                                URLConnection connection = mURL.openConnection();
                                // } else if (mURL.getProtocol() == "https") {
                                //
                                // } else {
                                // Log.e(TAG, "wrong protocol");
                                // }
                                fileSize = connection.getContentLength();
                                Log.d(Constants.TAG, "fileSize: " + fileSize);

                                connection.connect();

                                is = connection.getInputStream();
                                bis = new BufferedInputStream(is);

                                if (is == null) {
                                    Log.e(Constants.TAG, "Stream is null");
                                }

                                /* download with progress */
                                data = new byte[1024];
                                total = 0;
                                count = 0;

                                // run while only when thread is not cancelled
                                while ((count = bis.read(data)) != -1 && !isCancelled()) {
                                    out.write(data, 0, count);

                                    total += count;

                                    if (fileSize != -1) {
                                        publishProgress((int) ((total * 100) / fileSize));
                                    } else {
                                        publishProgress(50); // no ContentLength was returned
                                    }
                                }

                                // add line seperator to add files together in one file
                                out.write(Constants.LINE_SEPERATOR.getBytes());
                            } catch (Exception e) {
                                Log.e(Constants.TAG, "Exception: " + e);
                                returnCode = RETURN_DOWNLOAD_FAIL;
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
                                    Log.e(Constants.TAG, "Exception on flush and closing streams: "
                                            + e);
                                    e.printStackTrace();
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(Constants.TAG, "Private File can not be created, Exception: " + e);
                        returnCode = RETURN_PRIVATE_FILE_FAIL;
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
                    returnCode = RETURN_NO_CONNECTION;
                }

                return returnCode;
            }

            @Override
            protected void onProgressUpdate(Integer... progress) {
                // update dialog with filename and progress
                if (urlChanged) {
                    Log.d(Constants.TAG, "urlChanged");
                    mDownloadProgressDialog.setMessage(getString(R.string.download_dialog)
                            + Constants.LINE_SEPERATOR + currentURL);
                    urlChanged = false;
                }
                // Log.d(Constants.TAG, "progress: " + progress[0]);
                mDownloadProgressDialog.setProgress(progress[0]);
            }

            @Override
            protected void onPostExecute(Integer result) {
                super.onPostExecute(result);

                Log.d(Constants.TAG, "onPostExecute result: " + result);

                AlertDialog alertDialog;
                switch (result) {
                case RETURN_SUCCESS:
                    mDownloadProgressDialog.dismiss();

                    // Apply files by Apply thread
                    runApplyTask();
                    break;

                case RETURN_NO_CONNECTION:
                    mDownloadProgressDialog.dismiss();

                    alertDialog = new AlertDialog.Builder(mContext).create();
                    alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
                    alertDialog.setTitle(R.string.no_connection_title);
                    alertDialog.setMessage(getString(org.adaway.R.string.no_connection));
                    alertDialog.setButton(getString(R.string.button_close),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dlg, int sum) {
                                    dlg.dismiss();
                                }
                            });
                    alertDialog.show();
                    break;
                case RETURN_PRIVATE_FILE_FAIL:
                    mDownloadProgressDialog.dismiss();

                    alertDialog = new AlertDialog.Builder(mContext).create();
                    alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
                    alertDialog.setTitle(R.string.no_private_file_title);
                    alertDialog.setMessage(getString(org.adaway.R.string.no_private_file));
                    alertDialog.setButton(getString(R.string.button_close),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dlg, int sum) {
                                    dlg.dismiss();
                                }
                            });
                    alertDialog.show();
                    break;
                case RETURN_DOWNLOAD_FAIL:
                    mDownloadProgressDialog.dismiss();

                    alertDialog = new AlertDialog.Builder(mContext).create();
                    alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
                    alertDialog.setTitle(R.string.download_fail_title);
                    alertDialog.setMessage(getString(org.adaway.R.string.download_fail) + "\n"
                            + currentURL);
                    alertDialog.setButton(getString(R.string.button_close),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dlg, int sum) {
                                    dlg.dismiss();
                                }
                            });
                    alertDialog.show();
                    break;

                default:
                    break;
                }

            }
        };

        downloadTask.execute(urls);
    }

    /**
     * AsyncTask to parse downloaded hosts files, build one new merged hosts file out of them using
     * the redirection ip from the preferences and apply them using RootTools.
     */
    private void runApplyTask() {
        AsyncTask<Void, String, Integer> applyTask = new AsyncTask<Void, String, Integer>() {
            private ProgressDialog mApplyProgressDialog;

            @Override
            protected Integer doInBackground(Void... unused) {
                int returnCode = RETURN_SUCCESS; // default return code

                try {
                    /* PARSE: parse hosts files to sets of hostnames and comments */
                    publishProgress(getString(R.string.apply_dialog_hostnames));

                    FileInputStream fis = openFileInput(Constants.DOWNLOADED_HOSTS_FILENAME);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(fis));

                    HostsParser parser = new HostsParser(reader, getApplicationContext());
                    HashSet<String> hostnames = parser.getHostnames();
                    LinkedList<String> comments = parser.getComments();

                    fis.close();

                    publishProgress(getString(R.string.apply_dialog_lists));

                    /* READ DATABSE CONTENT */
                    mDatabaseHelper = new DatabaseHelper(mContext);

                    // get whitelist
                    HashSet<String> whitelist = mDatabaseHelper.getAllEnabledWhitelistItems();
                    Log.d(Constants.TAG, "Enabled whitelist: " + whitelist.toString());

                    // get blacklist
                    HashSet<String> blacklist = mDatabaseHelper.getAllEnabledBlacklistItems();
                    Log.d(Constants.TAG, "Enabled blacklist: " + blacklist.toString());

                    // get redirection list
                    HashMap<String, String> redirection = mDatabaseHelper
                            .getAllEnabledRedirectionItems();
                    Log.d(Constants.TAG, "Enabled redirection list: " + redirection.toString());

                    mDatabaseHelper.close();

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
                    publishProgress(getString(R.string.apply_dialog_hosts));

                    FileOutputStream fos = openFileOutput(Constants.HOSTS_FILENAME,
                            Context.MODE_PRIVATE);

                    // add adaway header
                    String header = Constants.HEADER1 + Constants.LINE_SEPERATOR
                            + Constants.HEADER2 + Constants.LINE_SEPERATOR;
                    fos.write(header.getBytes());

                    // write comments from other files to header
                    if (!SharedPrefs.getStripComments(getApplicationContext())) {
                        String headerComment = "# " + Constants.LINE_SEPERATOR
                                + Constants.HEADER_COMMENT;
                        fos.write(headerComment.getBytes());

                        String line;
                        for (String comment : comments) {
                            line = Constants.LINE_SEPERATOR + comment;
                            fos.write(line.getBytes());
                        }

                        fos.write(Constants.LINE_SEPERATOR.getBytes());
                    }

                    String redirectionIP = SharedPrefs.getRedirectionIP(getApplicationContext());

                    // add "127.0.0.1 localhost" entry
                    String localhost = Constants.LINE_SEPERATOR + Constants.LOCALHOST_IPv4 + " "
                            + Constants.LOCALHOST_HOSTNAME;
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

                    fos.close();

                    // delete downloaded hosts file from private storage
                    deleteFile(Constants.DOWNLOADED_HOSTS_FILENAME);

                    /* APPLY: apply hosts file using RootTools in copyHostsFile() */
                    publishProgress(getString(R.string.apply_dialog_apply));

                    // copy build hosts file with RootTools
                    if (!copyHostsFile()) {
                        throw new Exception();
                    }

                    // delete generated hosts file from private storage
                    deleteFile(Constants.HOSTS_FILENAME);

                    /* Set lastModified date in database to current date */
                    mDatabaseHelper = new DatabaseHelper(mContext);

                    long lastModified = Helper.getCurrentLongDate();
                    mDatabaseHelper.updateLastModified(lastModified);
                    Log.d(Constants.TAG, "Updated all hosts sources with lastModified: "
                            + lastModified + " (" + Helper.longToDateString(lastModified) + ")");

                    mDatabaseHelper.close();
                } catch (Exception e) {
                    Log.e(Constants.TAG, "Exception: " + e);
                    e.printStackTrace();

                    returnCode = RETURN_APPLY_FAILED;
                }

                // check if hosts file is applied
                if (!Helper.isHostsFileApplied(mContext)) {
                    returnCode = RETURN_APPLY_FAILED;
                }

                return returnCode;
            }

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                mApplyProgressDialog = new ProgressDialog(mContext);
                mApplyProgressDialog.setMessage(getString(R.string.apply_dialog));
                mApplyProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                mApplyProgressDialog.setCancelable(false);
                mApplyProgressDialog.show();
            }

            @Override
            protected void onProgressUpdate(String... status) {
                mApplyProgressDialog.setMessage(status[0]);
            }

            @Override
            protected void onPostExecute(Integer result) {
                super.onPostExecute(result);

                AlertDialog alertDialog;
                switch (result) {
                case RETURN_SUCCESS:
                    mApplyProgressDialog.dismiss();

                    mStatusIcon.setImageResource(R.drawable.status_enabled);
                    mStatusText.setText(R.string.status_enabled);
                    mStatusSubtitle.setText(R.string.status_enabled_subtitle);

                    AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                    builder.setTitle(R.string.apply_dialog);
                    builder.setMessage(getString(R.string.apply_success));
                    builder.setIcon(android.R.drawable.ic_dialog_info);
                    builder.setPositiveButton(getString(R.string.button_yes),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    String commandReboot = "reboot";
                                    List<String> output = null;
                                    try {
                                        output = RootTools.sendShell(commandReboot);
                                    } catch (Exception e) {
                                        Log.e(Constants.TAG, "Exception: " + e);
                                        e.printStackTrace();
                                    }
                                    Log.d(Constants.TAG, "output of command: " + output.toString());
                                }
                            });
                    builder.setNegativeButton(getString(R.string.button_no),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.dismiss();
                                }
                            });
                    AlertDialog question = builder.create();
                    question.show();
                    break;

                case RETURN_APPLY_FAILED:
                    Log.d(Constants.TAG, "Problem!");

                    mApplyProgressDialog.dismiss();

                    mStatusIcon.setImageResource(R.drawable.status_disabled);
                    mStatusText.setText(R.string.status_disabled);
                    mStatusSubtitle.setText(R.string.status_disabled_subtitle);

                    alertDialog = new AlertDialog.Builder(mContext).create();
                    alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
                    alertDialog.setTitle(R.string.apply_problem_title);
                    alertDialog.setMessage(getString(org.adaway.R.string.apply_problem));
                    alertDialog.setButton(getString(R.string.button_close),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dlg, int sum) {
                                    dlg.dismiss();
                                }
                            });
                    alertDialog.show();
                    break;

                default:
                    break;
                }
            }
        };

        applyTask.execute();
    }
}