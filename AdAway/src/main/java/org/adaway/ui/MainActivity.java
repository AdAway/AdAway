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

package org.adaway.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.adaway.R;
import org.adaway.helper.ResultHelper;
import org.adaway.util.Constants;
import org.adaway.util.Log;


// Documentation about navigation drawer here: https://developer.android.com/training/implementing-navigation/nav-drawer.html#top

public class MainActivity extends AppCompatActivity {


    Activity mActivity;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;


    /**
     * Handle result from applying when clicked on notification
     * http://stackoverflow.com/questions/1198558
     * /how-to-send-parameters-from-a-notification-click-to-an-activity MainActivity launchMode is
     * set to SingleTop for this in AndroidManifest
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(Constants.TAG, "onNewIntent");

        // if a notification is clicked after applying was done, the following is processed
        Bundle extras = intent.getExtras();
        if (extras == null) {
            return;
        }
        if (extras.containsKey(HomeFragment.EXTRA_APPLYING_RESULT)) {
            int result = extras.getInt(HomeFragment.EXTRA_APPLYING_RESULT);
            Log.d(Constants.TAG, "Result from intent extras: " + result);

            // download failed because of url
            String numberOfSuccessfulDownloads = null;
            if (extras.containsKey(HomeFragment.EXTRA_NUMBER_OF_SUCCESSFUL_DOWNLOADS)) {
                numberOfSuccessfulDownloads = extras
                        .getString(HomeFragment.EXTRA_NUMBER_OF_SUCCESSFUL_DOWNLOADS);
                Log.d(Constants.TAG, "Applying information from intent extras: "
                        + numberOfSuccessfulDownloads);
            }

            ResultHelper.showDialogBasedOnResult(mActivity, result, numberOfSuccessfulDownloads);
        }
    }

    /**
     * Instantiate View and initialize fragments for this Activity
     */
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivity = this;
        setContentView(R.layout.base_activity_drawer);
        /*
         * Configure navigation drawer.
         */
        // Configure drawer items
        String[] mPlanetTitles = getResources().getStringArray(R.array.drawer_items);
        ListView mDrawerList = (ListView) findViewById(R.id.left_drawer);
        mDrawerList.setAdapter(
                new ArrayAdapter<>(this, R.layout.drawer_list_item, mPlanetTitles)
        );
        // Set drawer item listener
        // Set the list's click listener
//        mDrawerList.setOnItemClickListener(new DrawerItemClickListener());
// TODO

        // Configure drawer toggle
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                R.string.drawer_open,  /* "open drawer" description */
                R.string.drawer_close  /* "close drawer" description */
        );
        mDrawerLayout.addDrawerListener(mDrawerToggle);
        /*
         * Configure actionbar.
         */
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setSubtitle(R.string.app_subtitle);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
        }
        /*
         * Insert main fragment.
         */
        // Check activity initialization
        if (savedInstanceState == null) {
            // Get fragment manager
            FragmentManager fragmentManager = getSupportFragmentManager();
            // Insert HomeFragment as main content
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
            HomeFragment homeFragment = new HomeFragment();
            fragmentTransaction.replace(R.id.content_frame, homeFragment);
            fragmentTransaction.commit();
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        // Handle your other action bar items...

        return super.onOptionsItemSelected(item);
    }
}
