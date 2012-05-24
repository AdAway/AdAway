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
import org.adaway.helper.OpenHelper;
import org.adaway.service.ApplyService;
import org.adaway.service.RevertService;
import org.adaway.service.UpdateService;
import org.adaway.util.Constants;
import org.adaway.util.StatusCodes;
import org.donations.DonationsActivity;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.commonsware.cwac.wakeful.WakefulIntentService;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class BaseFragment extends SherlockFragment {
    private Activity mActivity;

    private TextView mStatusTitle;
    private TextView mStatusText;
    private ProgressBar mStatusProgress;
    private ImageView mStatusIcon;
    private Button mApplyButton;
    private Button mRevertButton;

    private boolean mCurrentButtonsDisabled;
    private String mCurrentStatusTitle;
    private String mCurrentStatusText;
    private int mCurrentStatusIconStatus;

    /**
     * Set status icon based on StatusCodes
     * 
     * @param iconStatus
     *            Select UPDATE_AVAILABLE, ENABLED, DISABLED, DOWNLOAD_FAIL, or CHECKING from
     *            StatusCodes
     */
    private void setStatusIcon(int iconStatus) {
        switch (iconStatus) {
        case StatusCodes.UPDATE_AVAILABLE:
            mStatusProgress.setVisibility(View.GONE);
            mStatusIcon.setVisibility(View.VISIBLE);
            mStatusIcon.setImageResource(R.drawable.status_update);
            break;
        case StatusCodes.ENABLED:
            mStatusProgress.setVisibility(View.GONE);
            mStatusIcon.setVisibility(View.VISIBLE);
            mStatusIcon.setImageResource(R.drawable.status_enabled);
            break;
        case StatusCodes.DISABLED:
            mStatusProgress.setVisibility(View.GONE);
            mStatusIcon.setVisibility(View.VISIBLE);
            mStatusIcon.setImageResource(R.drawable.status_disabled);
            break;
        case StatusCodes.DOWNLOAD_FAIL:
            mStatusProgress.setVisibility(View.GONE);
            mStatusIcon.setImageResource(R.drawable.status_fail);
            mStatusIcon.setVisibility(View.VISIBLE);
            break;
        case StatusCodes.CHECKING:
            mStatusProgress.setVisibility(View.VISIBLE);
            mStatusIcon.setVisibility(View.GONE);
            break;

        default:
            break;
        }
    }

    /**
     * Set status in Fragment
     * 
     * @param title
     * @param text
     * @param iconStatus
     *            int based on StatusCodes to select icon
     */
    public void setStatus(String title, String text, int iconStatus) {
        mStatusTitle.setText(title);
        mStatusText.setText(text);
        setStatusIcon(iconStatus);

        // save for orientation change
        mCurrentStatusTitle = title;
        mCurrentStatusText = text;
        mCurrentStatusIconStatus = iconStatus;
    }

    public void setButtonsDisabled(boolean buttonsDisabled) {
        mApplyButton.setEnabled(!buttonsDisabled);
        mRevertButton.setEnabled(!buttonsDisabled);

        // save for orientation change
        mCurrentButtonsDisabled = buttonsDisabled;
    }

    /**
     * Save UI state changes to the savedInstanceState. This bundle will be passed to onCreate if
     * the process is killed and restarted like on orientation change.
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // save status on orientation change
        outState.putString("statusTitle", mCurrentStatusTitle);
        outState.putString("statusText", mCurrentStatusText);
        outState.putInt("statusIconStatus", mCurrentStatusIconStatus);
        outState.putBoolean("buttonsEnabled", mCurrentButtonsDisabled);
    }

    /**
     * Inflate Menu
     */
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.base, menu);
    }

    /**
     * Menu Options
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
        case R.id.menu_hosts_sources:
            startActivity(new Intent(mActivity, HostsSourcesActivity.class));
            return true;

        case R.id.menu_lists:
            startActivity(new Intent(mActivity, ListsActivity.class));
            return true;

        case R.id.menu_show_hosts_file:
            OpenHelper.openHostsFile(mActivity);
            return true;

        case R.id.menu_tcpdump:
            startActivity(new Intent(mActivity, TcpdumpActivity.class));
            return true;

        case R.id.menu_preferences:
            startActivity(new Intent(mActivity, PrefsActivity.class));
            return true;

        case R.id.menu_donations:
            startActivity(new Intent(mActivity, DonationsActivity.class));
            return true;

        case R.id.menu_about:
            startActivity(new Intent(mActivity, AboutActivity.class));
            return true;

        case R.id.menu_refresh:
            Intent updateIntent = new Intent(mActivity, UpdateService.class);
            updateIntent.putExtra(UpdateService.EXTRA_BACKGROUND_EXECUTION, false);
            WakefulIntentService.sendWakefulWork(mActivity, updateIntent);
            return true;

        case R.id.menu_help:
            startActivity(new Intent(mActivity, HelpActivity.class));
            return true;

        default:
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Inflate the layout for this fragment
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.base_fragment, container, false);
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActivity = getSherlockActivity();

        mStatusTitle = (TextView) mActivity.findViewById(R.id.status_title);
        mStatusText = (TextView) mActivity.findViewById(R.id.status_text);
        mStatusProgress = (ProgressBar) mActivity.findViewById(R.id.status_progress);
        mStatusIcon = (ImageView) mActivity.findViewById(R.id.status_icon);
        mApplyButton = (Button) mActivity.findViewById(R.id.apply_button);
        mRevertButton = (Button) mActivity.findViewById(R.id.revert_button);

        // get back status state when orientation changes and recreates activity
        if (savedInstanceState != null) {
            Log.d(Constants.TAG, "BaseFragment coming from an orientation change!");

            String title = savedInstanceState.getString("statusTitle");
            String text = savedInstanceState.getString("statusText");
            int iconStatus = savedInstanceState.getInt("statusIconStatus");
            if (title != null && text != null && iconStatus != -1) {
                setStatus(title, text, iconStatus);
            }

            boolean buttonsDisabled = savedInstanceState.getBoolean("buttonsEnabled");
            setButtonsDisabled(buttonsDisabled);
        }

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true); // enable options menu for this fragment
    }

    /**
     * Button Action to download and apply hosts files
     * 
     * @param view
     */
    public void applyOnClick(View view) {
        WakefulIntentService.sendWakefulWork(mActivity, ApplyService.class);
    }

    /**
     * Button Action to Revert to default hosts file
     * 
     * @param view
     */
    public void revertOnClick(View view) {
        WakefulIntentService.sendWakefulWork(mActivity, RevertService.class);
    }

}