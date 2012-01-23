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

package org.adaway.ui;

import org.adaway.R;
import org.adaway.helper.PreferencesHelper;
import org.adaway.helper.ResultHelper;
import org.adaway.service.UpdateListener;
import org.adaway.service.UpdateService;
import org.adaway.util.ApplyUtils;
import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.adaway.util.StatusCodes;
import org.adaway.util.Utils;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.ActionBar;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;

public class BaseActivity extends FragmentActivity {

    // Intent extras to give result of applying process to base activity
    public static final String EXTRA_APPLYING_RESULT = "org.adaway.APPLYING_RESULT";
    public static final String EXTRA_FAILING_URL = "org.adaway.APPLYING_INFORMATION";

    // Intent definitions for LocalBroadcastManager to update status from other threads
    static final String ACTION_UPDATE_STATUS = "org.adaway.UPDATE_STATUS";
    public static final String EXTRA_UPDATE_STATUS_TITLE = "org.adaway.UPDATE_STATUS.TITLE";
    public static final String EXTRA_UPDATE_STATUS_TEXT = "org.adaway.UPDATE_STATUS.TEXT";
    public static final String EXTRA_UPDATE_STATUS_ICON = "org.adaway.UPDATE_STATUS.ICON";

    BaseFragment mBaseFragment;
    WebserverFragment mWebserverFragment;
    FragmentManager mFragmentManager;

    LocalBroadcastManager mLocalBroadcastManager;
    BroadcastReceiver mReceiver;

    Activity mActivity;

    /**
     * Handle result from applying when clicked on notification
     * http://stackoverflow.com/questions/1198558
     * /how-to-send-parameters-from-a-notification-click-to-an-activity BaseActivity launchMode is
     * set to SingleTop for this in AndroidManifest
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.i(Constants.TAG, "onNewIntent");

        // if a notification is clicked after applying was done, the following is processed
        Bundle extras = intent.getExtras();
        if (extras != null) {
            if (extras.containsKey(EXTRA_APPLYING_RESULT)) {
                int result = extras.getInt(EXTRA_APPLYING_RESULT);
                Log.i(Constants.TAG, "Result from intent extras: " + result);

                // download failed because of url
                String failingUrl = null;
                if (extras.containsKey(EXTRA_FAILING_URL)) {
                    failingUrl = extras.getString(EXTRA_FAILING_URL);
                    Log.i(Constants.TAG, "Applying information from intent extras: " + failingUrl);
                }

                ResultHelper.showDialogBasedOnResult(mActivity, result, failingUrl);
            }
        }
    }

    /**
     * Instantiate View and initialize fragments for this Activity
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.base_activity);

        mActivity = this;

        mFragmentManager = getSupportFragmentManager();
        mBaseFragment = (BaseFragment) mFragmentManager.findFragmentById(R.id.base_fragment);

        // We use this to send broadcasts within our local process.
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);

        // We are going to watch for broadcasts with status updates
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_UPDATE_STATUS);
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Bundle extras = intent.getExtras();

                if (intent.getAction().equals(ACTION_UPDATE_STATUS)) {
                    if (extras != null) {
                        if (extras.containsKey(EXTRA_UPDATE_STATUS_TITLE)
                                && extras.containsKey(EXTRA_UPDATE_STATUS_TEXT)
                                && extras.containsKey(EXTRA_UPDATE_STATUS_ICON)) {

                            String title = extras.getString(EXTRA_UPDATE_STATUS_TITLE);
                            String text = extras.getString(EXTRA_UPDATE_STATUS_TEXT);
                            int status = extras.getInt(EXTRA_UPDATE_STATUS_ICON);

                            mBaseFragment.setStatus(title, text, status);
                        }
                    }
                }
            }
        };
        mLocalBroadcastManager.registerReceiver(mReceiver, filter);

        // Initial Status
        BaseActivity.updateStatusDisabled(mActivity);

        // check for root
        if (Utils.isAndroidRooted(mActivity)) {
            // do background update check
            // do only if not disabled in preferences
            if (PreferencesHelper.getUpdateCheck(mActivity)) {
                Intent updateIntent = new Intent(mActivity, UpdateService.class);
                updateIntent.putExtra(UpdateService.EXTRA_APPLY_AFTER_CHECK, false);
                WakefulIntentService.sendWakefulWork(mActivity, updateIntent);
            } else {
                // check if hosts file is applied
                if (ApplyUtils.isHostsFileCorrect(mActivity, Constants.ANDROID_SYSTEM_ETC_HOSTS)) {
                    BaseActivity.updateStatusEnabled(mActivity);
                } else {
                    BaseActivity.updateStatusDisabled(mActivity);
                }
            }

            // schedule CheckUpdateService
            WakefulIntentService.scheduleAlarms(new UpdateListener(), mActivity, false);
        }
    }

    /**
     * Static helper method to send broadcasts to the BaseActivity and update status in frontend
     * 
     * @param context
     * @param title
     * @param text
     * @param iconStatus
     *            Select UPDATE_AVAILABLE, ENABLED, DISABLED, DOWNLOAD_FAIL, or CHECKING from
     *            StatusCodes
     */
    public static void updateStatus(Context context, String title, String text, int iconStatus) {
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(context);

        Intent intent = new Intent(ACTION_UPDATE_STATUS);
        intent.putExtra(EXTRA_UPDATE_STATUS_ICON, iconStatus);
        intent.putExtra(EXTRA_UPDATE_STATUS_TITLE, title);
        intent.putExtra(EXTRA_UPDATE_STATUS_TEXT, text);
        localBroadcastManager.sendBroadcast(intent);
    }

    /**
     * Wrapper to set status to enabled
     * 
     * @param context
     */
    public static void updateStatusEnabled(Context context) {
        updateStatus(context, context.getString(R.string.status_enabled),
                context.getString(R.string.status_enabled_subtitle), StatusCodes.ENABLED);
    }

    /**
     * Wrapper to set status to disabled
     * 
     * @param context
     */
    public static void updateStatusDisabled(Context context) {
        updateStatus(context, context.getString(R.string.status_disabled),
                context.getString(R.string.status_disabled_subtitle), StatusCodes.DISABLED);
    }

    /**
     * Set Design of ActionBar
     */
    @Override
    protected void onStart() {
        super.onStart();
        ActionBar actionBar = this.getSupportActionBar();
        actionBar.setSubtitle(R.string.app_subtitle);

        // add WebserverFragment when enabled in preferences
        if (PreferencesHelper.getWebserverEnabled(this)) {
            FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();

            mWebserverFragment = new WebserverFragment();
            // replace container in view with fragment
            fragmentTransaction.replace(R.id.base_activity_webserver_container, mWebserverFragment);
            fragmentTransaction.commit();
        } else {
            // when disabled in preferences remove fragment if existing
            if (mWebserverFragment != null) {
                FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();

                fragmentTransaction.remove(mWebserverFragment);
                fragmentTransaction.commit();

                mWebserverFragment = null;
            }
        }
    }

    /**
     * hand over onClick events, defined in layout from Activity to Fragment
     */
    public void applyOnClick(View view) {
        mBaseFragment.applyOnClick(view);
    }

    /**
     * hand over onClick events, defined in layout from Activity to Fragment
     */
    public void revertOnClick(View view) {
        mBaseFragment.revertOnClick(view);
    }

    /**
     * hand over onClick events, defined in layout from Activity to Fragment
     */
    public void webserverOnClick(View view) {
        mWebserverFragment.webserverOnClick(view);
    }
}
