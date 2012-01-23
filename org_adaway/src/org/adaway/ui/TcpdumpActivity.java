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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ActionBar;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.MenuItem;
import android.view.View;

public class TcpdumpActivity extends FragmentActivity {
    TcpdumpFragment mTcpdumpFragment;
    FragmentManager mFragmentManager;

    Activity mActivity;

    /**
     * Instantiate View and initialize fragments for this Activity
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.tcpdump_activity);

        mActivity = this;

        mFragmentManager = getSupportFragmentManager();
        mTcpdumpFragment = (TcpdumpFragment) mFragmentManager
                .findFragmentById(R.id.tcpdump_fragment);
    }

    /**
     * Set Design of ActionBar
     */
    @Override
    protected void onStart() {
        super.onStart();
        ActionBar actionBar = this.getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
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

        default:
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * hand over onClick events, defined in layout from Activity to Fragment
     */
    public void tcpdumpToggleOnClick(View view) {
        mTcpdumpFragment.tcpdumpToggleOnClick(view);
    }

    /**
     * hand over onClick events, defined in layout from Activity to Fragment
     */
    public void tcpdumpOpenOnClick(View view) {
        mTcpdumpFragment.tcpdumpOpenOnClick(view);
    }

    /**
     * hand over onClick events, defined in layout from Activity to Fragment
     */
    public void tcpdumpDeleteOnClick(View view) {
        mTcpdumpFragment.tcpdumpDeleteOnClick(view);
    }
}
