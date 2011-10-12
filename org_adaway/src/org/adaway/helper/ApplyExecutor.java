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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.database.Cursor;
import android.os.AsyncTask;

public class ApplyExecutor {
    private BaseFragment mBaseFragment;
    private Activity mActivity;
    private DatabaseHelper mDatabaseHelper;

    private AsyncTask<Void, Integer, Integer> mDownloadTask;
    private AsyncTask<Void, String, Integer> mApplyTask;

    /**
     * Constructor based on fragment
     * 
     * @param baseFragment
     */
    public ApplyExecutor(BaseFragment baseFragment) {
        super();
        this.mBaseFragment = baseFragment;
        this.mActivity = baseFragment.getActivity();
    }

    /**
     * Execute Apply process
     */
    public void apply() {
        runDownloadTask();
    }

    /**
     * AsyncTask to download hosts files defined in db. In onPostExecute an Apply AsyncTask will be
     * started
     */
    private void runDownloadTask(String... urls) {
        mDownloadTask = new AsyncTask<Void, Integer, Integer>() {
            Cursor mEnabledHostsSourcesCursor;

            private ProgressDialog mDownloadProgressDialog;

            private int fileSize;
            private byte data[];
            private long total;
            private int count;
            private String mCurrentUrl;
            private boolean mUrlChanged;
            private boolean mIndeterminate;
            private boolean mIndeterminateChanged;
            private long mCurrentLastModifiedOnline;

            @Override
            protected void onPreExecute() {
                super.onPreExecute();

                mDownloadProgressDialog = new ProgressDialog(mActivity);
                mDownloadProgressDialog.setMessage(mBaseFragment
                        .getString(R.string.download_dialog));
                mDownloadProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mDownloadProgressDialog.setCancelable(true);
                mDownloadProgressDialog.setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        cancel(true); // cancel thread, now isCancelled() returns true
                    }
                });

                mDownloadProgressDialog.show();

                mUrlChanged = false;
            }

            @Override
            protected Integer doInBackground(Void... unused) {
                int returnCode = ReturnCodes.SUCCESS; // default return code

                if (Utils.isAndroidOnline(mActivity)) {
                    // output to write into
                    FileOutputStream out = null;

                    try {
                        out = mActivity.openFileOutput(Constants.DOWNLOADED_HOSTS_FILENAME,
                                Context.MODE_PRIVATE);

                        // get cursor over all enabled hosts source
                        mDatabaseHelper = new DatabaseHelper(mActivity);
                        mEnabledHostsSourcesCursor = mDatabaseHelper.getEnabledHostsSourcesCursor();

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
                                    mUrlChanged = true;
                                    publishProgress(0); // update UI

                                    /* build connection */
                                    URL mURL = new URL(mCurrentUrl);
                                    URLConnection connection = mURL.openConnection();

                                    fileSize = connection.getContentLength();
                                    Log.d(Constants.TAG, "fileSize: " + fileSize);

                                    /* set progressBar to indeterminate when fileSize is -1 */
                                    if (fileSize != -1) {
                                        mIndeterminate = false;
                                    } else {
                                        mIndeterminate = true;
                                    }
                                    mIndeterminateChanged = true;
                                    publishProgress(0); // update UI

                                    /* connect */
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

                                    // save last modified online for later use
                                    mCurrentLastModifiedOnline = connection.getLastModified();
                                    mDatabaseHelper.updateHostsSourceLastModifiedOnline(
                                            mEnabledHostsSourcesCursor
                                                    .getInt(mEnabledHostsSourcesCursor
                                                            .getColumnIndex("_id")),
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

                        // close cursor and db helper in the end
                        if (mEnabledHostsSourcesCursor != null
                                && !mEnabledHostsSourcesCursor.isClosed()) {
                            mEnabledHostsSourcesCursor.close();
                        }
                        mDatabaseHelper.close();
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
            protected void onProgressUpdate(Integer... progress) {
                // update dialog with filename and progress
                if (mUrlChanged) {
                    Log.d(Constants.TAG, "urlChanged");
                    mDownloadProgressDialog.setMessage(mActivity
                            .getString(R.string.download_dialog)
                            + Constants.LINE_SEPERATOR
                            + mCurrentUrl);
                    mUrlChanged = false;
                }
                // update progressBar of dialog
                if (mIndeterminateChanged) {
                    Log.d(Constants.TAG, "indeterminateChanged");
                    if (mIndeterminate) {
                        mDownloadProgressDialog.setIndeterminate(true);
                    } else {
                        mDownloadProgressDialog.setIndeterminate(false);
                    }
                    mIndeterminateChanged = false;
                }
                // Log.d(Constants.TAG, "progress: " + progress[0]);
                mDownloadProgressDialog.setProgress(progress[0]);
            }

            @Override
            protected void onPostExecute(Integer result) {
                super.onPostExecute(result);

                Log.d(Constants.TAG, "onPostExecute result: " + result);

                AlertDialog alertDialog;

                if (result == ReturnCodes.SUCCESS) {
                    mDownloadProgressDialog.dismiss();

                    // Apply files by Apply thread
                    runApplyTask();
                } else {
                    mDownloadProgressDialog.dismiss();

                    alertDialog = new AlertDialog.Builder(mActivity).create();
                    alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
                    alertDialog.setButton(mActivity.getString(R.string.button_close),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dlg, int sum) {
                                    dlg.dismiss();
                                }
                            });

                    switch (result) {
                    case ReturnCodes.NO_CONNECTION:
                        alertDialog.setTitle(R.string.no_connection_title);
                        alertDialog.setMessage(mActivity
                                .getString(org.adaway.R.string.no_connection));
                        break;

                    case ReturnCodes.PRIVATE_FILE_FAIL:
                        alertDialog.setTitle(R.string.no_private_file_title);
                        alertDialog.setMessage(mActivity
                                .getString(org.adaway.R.string.no_private_file));
                        break;
                    case ReturnCodes.DOWNLOAD_FAIL:
                        alertDialog.setTitle(R.string.download_fail_title);
                        alertDialog.setMessage(mActivity
                                .getString(org.adaway.R.string.download_fail) + "\n" + mCurrentUrl);
                        break;
                    case ReturnCodes.EMPTY_HOSTS_SOURCES:
                        alertDialog.setTitle(R.string.no_sources_title);
                        alertDialog.setMessage(mActivity.getString(org.adaway.R.string.no_sources));
                        break;
                    }

                    alertDialog.show();
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
            private ProgressDialog mApplyProgressDialog;

            @Override
            protected Integer doInBackground(Void... unused) {
                int returnCode = ReturnCodes.SUCCESS; // default return code

                try {
                    /* PARSE: parse hosts files to sets of hostnames and comments */
                    publishProgress(mActivity.getString(R.string.apply_dialog_hostnames));

                    FileInputStream fis = mActivity
                            .openFileInput(Constants.DOWNLOADED_HOSTS_FILENAME);

                    BufferedReader reader = new BufferedReader(new InputStreamReader(fis));

                    HostsParser parser = new HostsParser(reader);
                    HashSet<String> hostnames = parser.getHostnames();

                    fis.close();

                    publishProgress(mActivity.getString(R.string.apply_dialog_lists));

                    /* READ DATABSE CONTENT */
                    mDatabaseHelper = new DatabaseHelper(mActivity);

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

                    // get sources list
                    ArrayList<String> enabledHostsSources = mDatabaseHelper
                            .getAllEnabledHostsSources();
                    Log.d(Constants.TAG,
                            "Enabled hosts sources list: " + enabledHostsSources.toString());

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
                    publishProgress(mActivity.getString(R.string.apply_dialog_hosts));

                    FileOutputStream fos = mActivity.openFileOutput(Constants.HOSTS_FILENAME,
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

                    String redirectionIP = PreferencesHelper.getRedirectionIP(mActivity);

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
                mActivity.deleteFile(Constants.DOWNLOADED_HOSTS_FILENAME);

                /* APPLY: apply hosts file using RootTools in copyHostsFile() */
                publishProgress(mActivity.getString(R.string.apply_dialog_apply));

                // copy build hosts file with RootTools
                try {
                    if (PreferencesHelper.getApplyMethod(mActivity).equals("writeToSystem")) {
                        ApplyUtils.copyHostsFile(mActivity, false);
                    } else if (PreferencesHelper.getApplyMethod(mActivity)
                            .equals("writeToDataData")) {
                        ApplyUtils.copyHostsFile(mActivity, true);
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
                mActivity.deleteFile(Constants.HOSTS_FILENAME);

                /*
                 * Set last_modified_local dates in database to last_modified_online, got in
                 * download task
                 */
                mDatabaseHelper = new DatabaseHelper(mActivity);
                mDatabaseHelper.updateAllEnabledHostsSourcesLastModifiedLocalFromOnline();
                mDatabaseHelper.close();

                /* check if hosts file is applied with chosen method */
                // check only if everything before was successful
                if (returnCode == ReturnCodes.SUCCESS) {
                    if (PreferencesHelper.getApplyMethod(mActivity).equals("writeToSystem")) {
                        if (!ApplyUtils.isHostsFileApplied(mActivity,
                                Constants.ANDROID_SYSTEM_ETC_PATH)) {
                            returnCode = ReturnCodes.APPLY_FAIL;
                        }
                    } else if (PreferencesHelper.getApplyMethod(mActivity)
                            .equals("writeToDataData")) {
                        if (!ApplyUtils.isHostsFileApplied(mActivity,
                                Constants.ANDROID_DATA_DATA_PATH)) {
                            returnCode = ReturnCodes.APPLY_FAIL;
                        } else {
                            if (!ApplyUtils.isHostsFileApplied(mActivity,
                                    Constants.ANDROID_SYSTEM_ETC_PATH)) {
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
                mApplyProgressDialog = new ProgressDialog(mActivity);
                mApplyProgressDialog.setMessage(mActivity.getString(R.string.apply_dialog));
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

                if (result == ReturnCodes.SUCCESS) {
                    mApplyProgressDialog.dismiss();

                    mBaseFragment.setStatusEnabled();

                    Utils.rebootQuestion(mActivity, R.string.apply_success_title,
                            R.string.apply_success);
                } else if (result == ReturnCodes.SYMLINK_MISSING) {
                    mApplyProgressDialog.dismiss();

                    AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
                    builder.setTitle(R.string.apply_symlink_missing_title);
                    builder.setMessage(mActivity.getString(R.string.apply_symlink_missing));
                    builder.setIcon(android.R.drawable.ic_dialog_info);
                    builder.setPositiveButton(mActivity.getString(R.string.button_yes),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    tryToCreateSymlink();
                                }
                            });
                    builder.setNegativeButton(mActivity.getString(R.string.button_no),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.dismiss();

                                    mBaseFragment.setStatusDisabled();
                                }
                            });
                    AlertDialog question = builder.create();
                    question.show();
                } else {
                    mApplyProgressDialog.dismiss();

                    mBaseFragment.setStatusDisabled();

                    AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
                    builder.setIcon(android.R.drawable.ic_dialog_alert);
                    builder.setPositiveButton(mActivity.getString(R.string.button_close),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.dismiss();
                                }
                            });
                    builder.setNegativeButton(mActivity.getString(R.string.button_help),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.dismiss();

                                    // go to help
                                    mActivity.startActivity(new Intent(mActivity,
                                            HelpActivity.class));
                                }
                            });

                    switch (result) {
                    case ReturnCodes.APPLY_FAIL:
                        builder.setTitle(R.string.apply_fail_title);
                        builder.setMessage(mActivity.getString(org.adaway.R.string.apply_fail)
                                + "\n\n" + mActivity.getString(org.adaway.R.string.apply_help));

                        break;
                    case ReturnCodes.PRIVATE_FILE_FAIL:
                        builder.setTitle(R.string.apply_private_file_fail_title);
                        builder.setMessage(mActivity
                                .getString(org.adaway.R.string.apply_private_file_fail)
                                + "\n\n"
                                + mActivity.getString(org.adaway.R.string.apply_help));
                        break;
                    case ReturnCodes.NOT_ENOUGH_SPACE:
                        builder.setTitle(R.string.apply_not_enough_space_title);
                        builder.setMessage(mActivity
                                .getString(org.adaway.R.string.apply_not_enough_space)
                                + "\n\n"
                                + mActivity.getString(org.adaway.R.string.apply_help));
                        break;
                    case ReturnCodes.REMOUNT_FAIL:
                        builder.setTitle(R.string.apply_remount_fail_title);
                        builder.setMessage(mActivity
                                .getString(org.adaway.R.string.apply_remount_fail)
                                + "\n\n"
                                + mActivity.getString(org.adaway.R.string.apply_help));
                        break;
                    case ReturnCodes.COPY_FAIL:
                        builder.setTitle(R.string.apply_copy_fail_title);
                        builder.setMessage(mActivity.getString(org.adaway.R.string.apply_copy_fail)
                                + "\n\n" + mActivity.getString(org.adaway.R.string.apply_help));
                        break;
                    }

                    AlertDialog alertDialog = builder.create();
                    alertDialog.show();
                }
            }
        };

        mApplyTask.execute();
    }

    /**
     * Trying to create symlink and displays dialogs on fail
     */
    private void tryToCreateSymlink() {
        boolean success = true;

        try {
            ApplyUtils.createSymlink();
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
            if (ApplyUtils.isHostsFileApplied(mActivity, Constants.ANDROID_SYSTEM_ETC_PATH)) {
                success = true;
            } else {
                success = false;
            }
        }

        if (success) {
            mBaseFragment.setStatusEnabled();

            Utils.rebootQuestion(mActivity, R.string.apply_symlink_successful_title,
                    R.string.apply_symlink_successful);
        } else {
            mBaseFragment.setStatusDisabled();

            AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
            builder.setTitle(R.string.apply_symlink_fail_title);
            builder.setMessage(mActivity.getString(org.adaway.R.string.apply_symlink_fail) + "\n\n"
                    + mActivity.getString(org.adaway.R.string.apply_help));
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setPositiveButton(mActivity.getString(R.string.button_close),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                        }
                    });
            builder.setNegativeButton(mActivity.getString(R.string.button_help),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();

                            // go to help
                            mActivity.startActivity(new Intent(mActivity, HelpActivity.class));
                        }
                    });
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
        }
    }
}
