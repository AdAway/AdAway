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

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

import org.adaway.ui.BaseFragment;
import org.adaway.util.ApplyUtils;
import org.adaway.util.Constants;
import org.adaway.util.ReturnCodes.ReturnCode;
import org.adaway.util.StatusUtils;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.util.Log;

public class StatusChecker {
    private BaseFragment mBaseFragment;
    private Activity mActivity;
    private DatabaseHelper mDatabaseHelper;

    private AsyncTask<String, Integer, Enum<ReturnCode>> mStatusTask;

    /**
     * Constructor based on fragment
     * 
     * @param baseFragment
     */
    public StatusChecker(BaseFragment baseFragment) {
        super();
        this.mBaseFragment = baseFragment;
        this.mActivity = baseFragment.getActivity();
    }

    /**
     * Check for updates on create?
     */
    public void checkForUpdatesOnCreate() {
        // do only if not disabled in preferences
        if (PreferencesHelper.getUpdateCheck(mActivity)) {
            checkForUpdates();
        } else {
            // check if hosts file is applied
            if (ApplyUtils.isHostsFileApplied(mActivity, Constants.ANDROID_SYSTEM_ETC_PATH)) {
                mBaseFragment.setStatusEnabled();
            } else {
                mBaseFragment.setStatusDisabled();
            }
        }
    }

    /**
     * Run status AsyncTask to check for updates
     */
    public void checkForUpdates() {
        mDatabaseHelper = new DatabaseHelper(mActivity);

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
     * AsyncTask to check for updates and determine the status of AdAway, can be executed with many
     * urls as params.
     */
    private void runStatusTask(String... urls) {
        mStatusTask = new AsyncTask<String, Integer, Enum<ReturnCode>>() {
            private String currentUrl;
            private int fileSize;
            private long lastModified = 0;
            private long lastModifiedCurrent;

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                mBaseFragment.setStatusChecking();
            }

            private boolean isAndroidOnline() {
                ConnectivityManager cm = (ConnectivityManager) mActivity
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo netInfo = cm.getActiveNetworkInfo();
                if (netInfo != null && netInfo.isConnectedOrConnecting()) {
                    return true;
                }
                return false;
            }

            @Override
            protected Enum<ReturnCode> doInBackground(String... urls) {
                ReturnCode returnCode = ReturnCode.ENABLED; // default return code

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
                            currentUrl = url;

                            /* build connection */
                            URL mURL = new URL(url);
                            URLConnection connection = mURL.openConnection();

                            fileSize = connection.getContentLength();
                            Log.d(Constants.TAG, "fileSize: " + fileSize);

                            lastModifiedCurrent = connection.getLastModified();

                            Log.d(Constants.TAG, "lastModifiedCurrent: " + lastModifiedCurrent
                                    + " (" + StatusUtils.longToDateString(lastModifiedCurrent) + ")");

                            Log.d(Constants.TAG,
                                    "lastModified: " + lastModified + " ("
                                            + StatusUtils.longToDateString(lastModified) + ")");

                            // set lastModified to the maximum of all lastModifieds
                            if (lastModifiedCurrent > lastModified) {
                                lastModified = lastModifiedCurrent;
                            }

                            // check if file is available
                            connection.connect();
                            is = connection.getInputStream();

                        } catch (Exception e) {
                            Log.e(Constants.TAG, "Exception: " + e);
                            returnCode = ReturnCode.DOWNLOAD_FAIL;
                            break; // stop for-loop
                        }
                    }
                } else {
                    returnCode = ReturnCode.NO_CONNECTION;
                }

                /* CHECK if update is necessary */
                DatabaseHelper taskDatabaseHelper = new DatabaseHelper(mActivity);

                // get last modified from db
                long lastModifiedDatabase = taskDatabaseHelper.getLastModified();

                taskDatabaseHelper.close();

                Log.d(Constants.TAG,
                        "lastModified: " + lastModified + " ("
                                + StatusUtils.longToDateString(lastModified) + ")");

                Log.d(Constants.TAG,
                        "lastModifiedDatabase: " + lastModifiedDatabase + " ("
                                + StatusUtils.longToDateString(lastModifiedDatabase) + ")");

                // check if maximal lastModified is bigger than the ones in database
                if (lastModified > lastModifiedDatabase) {
                    returnCode = ReturnCode.UPDATE_AVAILABLE;
                }

                // check if hosts file is applied
                if (!ApplyUtils.isHostsFileApplied(mActivity, Constants.ANDROID_SYSTEM_ETC_PATH)) {
                    returnCode = ReturnCode.DISABLED;
                }

                return returnCode;
            }

            @Override
            protected void onPostExecute(Enum<ReturnCode> result) {
                super.onPostExecute(result);

                Log.d(Constants.TAG, "onPostExecute result: " + result);

                if (result == ReturnCode.UPDATE_AVAILABLE) {
                    mBaseFragment.setStatusUpdateAvailable();
                } else if (result == ReturnCode.DISABLED) {
                    mBaseFragment.setStatusDisabled();
                } else if (result == ReturnCode.DOWNLOAD_FAIL) {
                    mBaseFragment.setStatusDownloadFail(currentUrl);
                } else if (result == ReturnCode.NO_CONNECTION) {
                    mBaseFragment.setStatusNoConnection();
                } else if (result == ReturnCode.ENABLED) {
                    mBaseFragment.setStatusEnabled();
                }
            }
        };

        mStatusTask.execute(urls);
    }

    /**
     * Stop AsyncTask
     */
    public void cancelStatusCheck() {
        // cancel task
        if (mStatusTask != null) {
            mStatusTask.cancel(true);
        }
    }

}
