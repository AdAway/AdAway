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
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.adaway.R;
import org.adaway.helper.ImportExportHelper;
import org.adaway.util.Constants;
import org.adaway.util.Log;

public class ListsActivity extends AppCompatActivity {
    private FragmentActivity mActivity;
    private TabLayout.Tab mTab1;
    private TabLayout.Tab mTab2;
    private TabLayout.Tab mTab3;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.lists_activity, menu);
        return true;
    }

    /**
     * Used to get file contents trough intents for import and export
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(Constants.TAG, "Handling onActivityResult...");

        // handle import and export of files in helper
        ImportExportHelper.onActivityResultHandleImport(mActivity, requestCode, resultCode, data);
    }

    /**
     * Menu item to go back home in ActionBar, other menu items are defined in Fragments
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // app icon in Action Bar clicked; go home
                Intent intent = new Intent(mActivity, BaseActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;

            case R.id.menu_import:
                ImportExportHelper.openFileStream(mActivity);
                return true;

            case R.id.menu_export:
                ImportExportHelper.exportLists(mActivity);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Set up Tabs on create
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActivity = this;

        setContentView(R.layout.lists_activity);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        TabLayout tabLayout = (TabLayout) findViewById(R.id.lists_tabs);

        TabLayout.OnTabSelectedListener listener = new MyTabSelectedListener();
        tabLayout.addOnTabSelectedListener(listener);

        mTab1 = tabLayout.getTabAt(0);
        mTab2 = tabLayout.getTabAt(1);
        mTab3 = tabLayout.getTabAt(2);

        setTabTextBasedOnOrientation(getResources().getConfiguration());

        listener.onTabSelected(mTab1);
    }

    private void setTabTextBasedOnOrientation(Configuration config) {
        // longer names for landscape mode or tablets
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE
                || config.screenLayout == Configuration.SCREENLAYOUT_SIZE_XLARGE) {
            mTab1.setText(getString(R.string.lists_tab_blacklist));
            mTab2.setText(getString(R.string.lists_tab_whitelist));
            mTab3.setText(getString(R.string.lists_tab_redirection_list));

        } else {
            mTab1.setText(getString(R.string.lists_tab_blacklist_short));
            mTab2.setText(getString(R.string.lists_tab_whitelist_short));
            mTab3.setText(getString(R.string.lists_tab_redirection_list_short));
        }
    }

    /**
     * Change text on orientation change
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        setTabTextBasedOnOrientation(newConfig);
    }


    public class MyTabSelectedListener implements TabLayout.OnTabSelectedListener {
        private Fragment mFragment;

        @Override
        public void onTabSelected(TabLayout.Tab tab) {
            // bug in compatibility lib:
            // http://stackoverflow.com/questions/8645549/null-fragmenttransaction-being-passed-to-tablistener-ontabselected
            FragmentManager fragMgr = mActivity.getSupportFragmentManager();
            FragmentTransaction ft = fragMgr.beginTransaction();


            Class<? extends Fragment> fragment;
            switch (tab.getPosition()) {
                case 0:
                    fragment = BlacklistFragment.class;
                    break;
                case 1:
                    fragment = WhitelistFragment.class;
                    break;
                case 2:
                    fragment = RedirectionListFragment.class;
                    break;
                default:
                    return;
            }

            mFragment = Fragment.instantiate(mActivity, fragment.getName());
            ft.replace(R.id.lists_tabs_container, mFragment, fragment.getSimpleName());
            ft.commit();
        }

        @Override
        public void onTabUnselected(TabLayout.Tab tab) {
            FragmentManager fragMgr = mActivity.getSupportFragmentManager();
            FragmentTransaction ft = fragMgr.beginTransaction();

            if (mFragment != null) {
                // Remove the fragment
                ft.remove(mFragment);
            }

            ft.commit();
        }

        @Override
        public void onTabReselected(TabLayout.Tab tab) {
            // Nothing special to do
        }
    }
}