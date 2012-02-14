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
import org.adaway.util.TcpdumpUtils;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ToggleButton;

public class TcpdumpFragment extends Fragment {
    private Activity mActivity;

    private ToggleButton mTcpdumpToggle;

    /**
     * Inflate the layout for this fragment
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.tcpdump_fragment, container, false);
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActivity = getActivity();
        mTcpdumpToggle = (ToggleButton) mActivity.findViewById(R.id.tcpdump_fragment_toggle_button);

        // set togglebutton checked if tcpdump is running
        if (TcpdumpUtils.isTcpdumpRunning()) {
            mTcpdumpToggle.setChecked(true);
        } else {
            mTcpdumpToggle.setChecked(false);
        }
    }

    public void tcpdumpToggleOnClick(View view) {
        if (mTcpdumpToggle.isChecked() == true) {
            // if starting does not work, set back to disabled...
            if (!TcpdumpUtils.startTcpdump(mActivity)) {
                mTcpdumpToggle.setChecked(false);
            }
        }
        if (mTcpdumpToggle.isChecked() == false) {
            TcpdumpUtils.stopTcpdump(mActivity);
        }
    }

    public void tcpdumpOpenOnClick(View view) {
        startActivity(new Intent(mActivity, TcpdumpLogActivity.class));
    }

    public void tcpdumpDeleteOnClick(View view) {
        TcpdumpUtils.deleteLog(mActivity);
    }
}