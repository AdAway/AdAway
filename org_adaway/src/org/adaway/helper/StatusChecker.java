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

import org.adaway.provider.AdAwayDatabase;
import org.adaway.ui.BaseFragment;
import org.adaway.util.ApplyUtils;
import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.adaway.util.ReturnCodes;
import org.adaway.util.StatusUtils;
import org.adaway.util.Utils;


import android.app.Activity;
import android.database.Cursor;
import android.os.AsyncTask;

public class StatusChecker {
    private BaseFragment mBaseFragment;
    private Activity mActivity;
    private AdAwayDatabase mDatabaseHelper;

    private AsyncTask<Void, Integer, Integer> mStatusTask;

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
            if (ApplyUtils.isHostsFileCorrect(mActivity, Constants.ANDROID_SYSTEM_ETC_HOSTS)) {
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
        runStatusTask();
    }

    /**
     * AsyncTask to check for updates and determine the status of AdAway, can be executed with many
     * urls as params.
     */
    private void runStatusTask() {
        mStatusTask = new AsyncTask<Void, Integer, Integer>() {
            Cursor mEnabledHostsSourcesCursor;

            private String mCurrentUrl;
            private long mCurrentLastModifiedLocal;
            private long mCurrentLastModifiedOnline;
            private boolean mUpdateAvailable = false;

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                mBaseFragment.setStatusChecking();
            }

            @Override
            protected Integer doInBackground(Void... unused) {
                int returnCode = ReturnCodes.ENABLED; // default return code

                if (Utils.isAndroidOnline(mActivity)) {

                    // get cursor over all enabled hosts source
                    mDatabaseHelper = new AdAwayDatabase(mActivity);
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
                                        mEnabledHostsSourcesCursor
                                                .getInt(mEnabledHostsSourcesCursor
                                                        .getColumnIndex("_id")),
                                        mCurrentLastModifiedOnline);

                            } catch (Exception e) {
                                Log.e(Constants.TAG, "Exception: " + e);
                                returnCode = ReturnCodes.DOWNLOAD_FAIL;
                                break; // stop for-loop
                            }

                        } while (mEnabledHostsSourcesCursor.moveToNext());
                    }

                    // close cursor and db helper in the end
                    if (mEnabledHostsSourcesCursor != null
                            && !mEnabledHostsSourcesCursor.isClosed()) {
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
                if (!ApplyUtils.isHostsFileCorrect(mActivity, Constants.ANDROID_SYSTEM_ETC_HOSTS)) {
                    returnCode = ReturnCodes.DISABLED;
                }

                return returnCode;
            }

            @Override
            protected void onPostExecute(Integer result) {
                super.onPostExecute(result);

                Log.d(Constants.TAG, "onPostExecute result: " + result);

                switch (result) {
                case ReturnCodes.UPDATE_AVAILABLE:
                    mBaseFragment.setStatusUpdateAvailable();
                    break;
                case ReturnCodes.DISABLED:
                    mBaseFragment.setStatusDisabled();
                    break;
                case ReturnCodes.DOWNLOAD_FAIL:
                    mBaseFragment.setStatusDownloadFail(mCurrentUrl);
                    break;
                case ReturnCodes.NO_CONNECTION:
                    mBaseFragment.setStatusNoConnection();
                    break;
                case ReturnCodes.ENABLED:
                    mBaseFragment.setStatusEnabled();
                    break;
                }
            }
        };

        mStatusTask.execute();
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
