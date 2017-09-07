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

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.adaway.R;
import org.adaway.helper.OpenHelper;
import org.adaway.helper.ResultHelper;
import org.adaway.ui.adware.ScanAdwareFragment;
import org.adaway.ui.home.HomeFragment;
import org.adaway.ui.hosts.HostsSourcesFragment;
import org.adaway.ui.lists.ListsFragment;
import org.adaway.ui.prefs.PrefsFragment;
import org.adaway.ui.tcpdump.TcpdumpFragment;
import org.adaway.util.Constants;
import org.adaway.util.Log;


public class MainActivity extends AppCompatActivity {
    /*
     * Application navigation related.
     */
    /**
     * The navigation drawer layout.
     */
    private DrawerLayout mDrawerLayout;
    /**
     * The navigation drawer toggle.
     */
    private ActionBarDrawerToggle mDrawerToggle;
    /**
     * The navigation drawer list.
     */
    private ListView mDrawerList;
    /**
     * The activity title.
     */
    private CharSequence mTitle;
    /**
     * The navigation drawer title.
     */
    private CharSequence mDrawerTitle;


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

            ResultHelper.showDialogBasedOnResult(this, result, numberOfSuccessfulDownloads);
        }
    }

    /**
     * Instantiate View and initialize fragments for this Activity
     */
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.base_activity_drawer);
        /*
         * Configure navigation drawer.
         */
        // Configure drawer items
        String[] mPlanetTitles = getResources().getStringArray(R.array.drawer_items);
        this.mDrawerList = this.findViewById(R.id.left_drawer);
        this.mDrawerList.setAdapter(new ArrayAdapter<>(this, R.layout.drawer_list_item, mPlanetTitles)
        );
        // Set drawer item listener
        // Set the list's click listener
        this.mDrawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectDrawerMenuItem(position);
            }
        });

        // Configure drawer toggle
        this.mTitle = this.mDrawerTitle = this.getTitle();
        this.mDrawerLayout = this.findViewById(R.id.drawer_layout);
        this.mDrawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                this.mDrawerLayout,         /* DrawerLayout object */
                R.string.drawer_open,  /* "open drawer" description */
                R.string.drawer_close  /* "close drawer" description */
        ) {
            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                ActionBar actionBar = getSupportActionBar();
                if (actionBar != null) {
                    actionBar.setTitle(MainActivity.this.mTitle);
                }
            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                ActionBar actionBar = getSupportActionBar();
                if (actionBar != null) {
                    actionBar.setTitle(MainActivity.this.mDrawerTitle);
                }
            }

        };
        this.mDrawerLayout.addDrawerListener(this.mDrawerToggle);
        /*
         * Configure actionbar.
         */
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
//            actionBar.setSubtitle(R.string.app_subtitle);
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
            // Select home menu item
            this.mDrawerList.setItemChecked(0, true);
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        this.mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        this.mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Pass the event to ActionBarDrawerToggle, if it returns true,
        // then it has handled the app icon touch event
        if (this.mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        // Handle your other action bar items...
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void setTitle(CharSequence title) {
        this.mTitle = title;
        ActionBar actionBar = this.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(this.mTitle);
        }
    }

    /**
     * Swaps fragments in the main content view.
     *
     * @param position The position of the selected item in drawer menu.
     */
    private void selectDrawerMenuItem(int position) {
        Fragment fragment = null;
        switch (position) {
            case 0:
                fragment = new HomeFragment();
                break;
            case 1:
                fragment = new HostsSourcesFragment();
                break;
            case 2:
                fragment = new ListsFragment();
                break;
            case 3:
                OpenHelper.openHostsFile(this);
                break;
            case 4:
                fragment = new TcpdumpFragment();
                break;
            case 5:
                fragment = new ScanAdwareFragment();
                break;
            case 6:
                fragment = new PrefsFragment();
                break;
        }

        // Create a new fragment and specify the planet to show based on position
//        Bundle args = new Bundle();
//        args.putInt(PlanetFragment.ARG_PLANET_NUMBER, position);
//        fragment.setArguments(args);

        if (fragment != null) {
            // Insert the fragment by replacing any existing fragment
            FragmentManager fragmentManager = getSupportFragmentManager();
            fragmentManager.beginTransaction()
                    .replace(R.id.content_frame, fragment)
                    .commit();
        } else {
            return;
        }

        // Highlight the selected item, update the title, and close the drawer
        this.mDrawerList.setItemChecked(position, true);
        String itemName;
        if (position == 0) {
            itemName = getString(R.string.app_name);
        } else {
            itemName = this.getResources().getStringArray(R.array.drawer_items)[position];
        }
        this.setTitle(itemName);
        this.mDrawerLayout.closeDrawer(this.mDrawerList);
    }
}
