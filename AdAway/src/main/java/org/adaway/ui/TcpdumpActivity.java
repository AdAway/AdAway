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

import java.io.IOException;

import org.adaway.R;
import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.adaway.util.TcpdumpUtils;
import org.sufficientlysecure.rootcommands.Shell;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ToggleButton;

public class TcpdumpActivity extends SherlockActivity {
    private Activity mActivity;
    private ActionBar mActionBar;
    private ToggleButton mTcpdumpToggle;

    private Shell mRootShell;

    /**
     * Instantiate View and initialize fragments for this Activity
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.tcpdump_activity);

        mActivity = this;

        mActionBar = getSupportActionBar();
        mActionBar.setDisplayShowTitleEnabled(true);
        mActionBar.setDisplayHomeAsUpEnabled(true);

        mTcpdumpToggle = (ToggleButton) mActivity.findViewById(R.id.tcpdump_fragment_toggle_button);

        try {
            mRootShell = Shell.startRootShell();
        } catch (Exception e) {
            Log.e(Constants.TAG, "Problem while starting shell!", e);
        }

        // set togglebutton checked if tcpdump is running
        if (TcpdumpUtils.isTcpdumpRunning(mRootShell)) {
            mTcpdumpToggle.setChecked(true);
        } else {
            mTcpdumpToggle.setChecked(false);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        try {
            if (mRootShell != null) {
                mRootShell.close();
            }
        } catch (IOException e) {
            Log.e(Constants.TAG, "Problem while closing shell!", e);
        }
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

    public void tcpdumpToggleOnClick(View view) {
        if (mTcpdumpToggle.isChecked()) {
            // if starting does not work, set back to disabled...
            if (!TcpdumpUtils.startTcpdump(mActivity, mRootShell)) {
                mTcpdumpToggle.setChecked(false);
            }
        }
        if (!mTcpdumpToggle.isChecked()) {
            TcpdumpUtils.stopTcpdump(mActivity, mRootShell);
        }
    }

    public void tcpdumpOpenOnClick(View view) {
        startActivity(new Intent(mActivity, TcpdumpLogActivity.class));
    }

    public void tcpdumpDeleteOnClick(View view) {
        TcpdumpUtils.deleteLog(mActivity);
    }
}
