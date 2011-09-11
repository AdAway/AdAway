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
import org.adaway.util.CommandException;
import org.adaway.util.WebserverUtils;

import android.os.Bundle;
import android.support.v4.app.ActionBar;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.view.View;

public class BaseActivity extends FragmentActivity {
    BaseFragment mBaseFragment;

    /**
     * Instantiate View and initialize fragments for this Activity
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.base_activity);
        
        FragmentManager fragmentManager = getSupportFragmentManager();
        mBaseFragment = (BaseFragment) fragmentManager.findFragmentById(R.id.base_fragment);
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
    public void startWebserver(View view) {
        try {
            WebserverUtils.startWebserver(this);
        } catch (CommandException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    /**
     * hand over onClick events, defined in layout from Activity to Fragment
     */
    public void stopWebserver(View view) {
    }
}
