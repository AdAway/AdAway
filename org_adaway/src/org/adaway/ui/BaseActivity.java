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
import org.adaway.helper.ApplyExecutor;
import org.adaway.helper.PreferencesHelper;
import org.adaway.util.Constants;
import org.adaway.util.Log;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ActionBar;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.View;

public class BaseActivity extends FragmentActivity {
    BaseFragment mBaseFragment;
    WebserverFragment mWebserverFragment;
    FragmentManager mFragmentManager;

    // static String that defines intent extra to give result of applying process to base activity
    public static final String EXTRA_APPLYING_RESULT = "APPLYING_RESULT";
    public static final String EXTRA_APPLYING_INFORMATION = "APPLYING_INFORMATION";

    /**
     * Handle result from applying when clicked on notification
     * http://stackoverflow.com/questions/1198558
     * /how-to-send-parameters-from-a-notification-click-to-an-activity BaseActivity launchMode is
     * set to SingleTop for this in AndroidManifest
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        ApplyExecutor applyExecutor = new ApplyExecutor(this);

        // if a notification is clicked after applying was done, the following is processed
        Bundle extras = intent.getExtras();
        if (extras != null) {
            if (extras.containsKey(EXTRA_APPLYING_RESULT)) {
                int result = extras.getInt(EXTRA_APPLYING_RESULT);
                Log.d(Constants.TAG, "Result from intent extras: " + result);

                // if extra information is present use it, like failed url to download
                String extraInformation = "";
                if (extras.containsKey(EXTRA_APPLYING_INFORMATION)) {
                    extraInformation = extras.getString(EXTRA_APPLYING_INFORMATION);
                    Log.d(Constants.TAG, "Applying information from intent extras: "
                            + extraInformation);
                }

                applyExecutor.processApplyingResult(mBaseFragment, result, extraInformation);
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

        mFragmentManager = getSupportFragmentManager();
        mBaseFragment = (BaseFragment) mFragmentManager.findFragmentById(R.id.base_fragment);
    }

    /**
     * On resume of application. Show webserver controls when enabled in preferences
     */
    @Override
    protected void onResume() {
        super.onResume();

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
     * Set Design of ActionBar
     */
    @Override
    protected void onStart() {
        super.onStart();
        ActionBar actionBar = this.getSupportActionBar();
        actionBar.setSubtitle(R.string.app_subtitle);
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
