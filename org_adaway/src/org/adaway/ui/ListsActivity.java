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
import org.adaway.helper.ImportExportHelper;
import org.adaway.util.Constants;
import org.adaway.util.Log;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.ActionBar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ActionBar.Tab;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.view.MenuInflater;

public class ListsActivity extends FragmentActivity implements ActionBar.TabListener {

    /**
     * Set ActionBar to include sign to Home
     */
    @Override
    protected void onStart() {
        super.onStart();
        ActionBar actionBar = this.getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
    }

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
        ImportExportHelper.onActivityResult(this, requestCode, resultCode, data);

        Log.d(Constants.TAG, "on activity result");
    }

    /**
     * Menu item to go back home in ActionBar, other menu items are defined in Fragments
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            // app icon in Action Bar clicked; go home
            Intent intent = new Intent(this, BaseActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
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

        setContentView(R.layout.lists_activity);

        // start with blacklist
        BlacklistFragment blacklistFragment = new BlacklistFragment();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.lists_tabs_container, blacklistFragment).commit();

        // https://github.com/JakeWharton/ActionBarSherlock/issues/68
        // execute transactions before using ActionBar. ActionBar will be null because without this
        // fragment transactions are asynchronous and ActionBar is not ready at once
        getSupportFragmentManager().executePendingTransactions();

        getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        ActionBar.Tab tab1 = getSupportActionBar().newTab();
        ActionBar.Tab tab2 = getSupportActionBar().newTab();
        ActionBar.Tab tab3 = getSupportActionBar().newTab();

        tab1.setTabListener(this);
        tab2.setTabListener(this);
        tab3.setTabListener(this);

        // longer names for landscape mode or tablets
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
                || getResources().getConfiguration().screenLayout == Configuration.SCREENLAYOUT_SIZE_XLARGE) {
            tab1.setText(getString(R.string.lists_tab_blacklist));
            tab2.setText(getString(R.string.lists_tab_whitelist));
            tab3.setText(getString(R.string.lists_tab_redirection_list));

        } else {
            tab1.setText(getString(R.string.lists_tab_blacklist_short));
            tab2.setText(getString(R.string.lists_tab_whitelist_short));
            tab3.setText(getString(R.string.lists_tab_redirection_list_short));
        }

        getSupportActionBar().addTab(tab1);
        getSupportActionBar().addTab(tab2);
        getSupportActionBar().addTab(tab3);
    }

    @Override
    public void onTabReselected(Tab tab, FragmentTransaction ft) {
    }

    /**
     * Open Fragment based on selected Tab
     */
    @Override
    public void onTabSelected(Tab tab, FragmentTransaction ft) {
        // choose current fragment based on tab position
        Fragment fragment = null;
        switch (tab.getPosition()) {
        case 0:
            fragment = new BlacklistFragment();
            break;
        case 1:
            fragment = new WhitelistFragment();
            break;
        case 2:
            fragment = new RedirectionListFragment();
            break;
        default:
            fragment = new BlacklistFragment();
            break;
        }

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.lists_tabs_container, fragment);
        fragmentTransaction.commit();
    }

    @Override
    public void onTabUnselected(Tab tab, FragmentTransaction ft) {
    }
}