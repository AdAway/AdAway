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
import org.adaway.helper.RevertExecutor;
import org.adaway.helper.StatusChecker;
import org.adaway.helper.UiHelper;
import org.adaway.service.ApplyService;
import org.adaway.service.UpdateListener;
import org.adaway.util.ReturnCodes;
import org.adaway.util.Utils;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class BaseFragment extends Fragment {
    private Activity mActivity;

    private TextView mStatusText;
    private TextView mStatusSubtitle;
    private ProgressBar mStatusProgress;
    private ImageView mStatusIcon;

    private StatusChecker mStatusChecker;
    private RevertExecutor mRevertExecutor;

    public void setStatusIcon(int status) {
        switch (status) {
        case ReturnCodes.UPDATE_AVAILABLE:
            mStatusProgress.setVisibility(View.GONE);
            mStatusIcon.setVisibility(View.VISIBLE);
            mStatusIcon.setImageResource(R.drawable.status_update);
            break;
        case ReturnCodes.ENABLED:
            mStatusProgress.setVisibility(View.GONE);
            mStatusIcon.setVisibility(View.VISIBLE);
            mStatusIcon.setImageResource(R.drawable.status_enabled);
            break;
        case ReturnCodes.DISABLED:
            mStatusProgress.setVisibility(View.GONE);
            mStatusIcon.setVisibility(View.VISIBLE);
            mStatusIcon.setImageResource(R.drawable.status_disabled);
            break;
        case ReturnCodes.DOWNLOAD_FAIL:
            mStatusProgress.setVisibility(View.GONE);
            mStatusIcon.setImageResource(R.drawable.status_fail);
            mStatusIcon.setVisibility(View.VISIBLE);
            break;
        case ReturnCodes.CHECKING:
            mStatusProgress.setVisibility(View.VISIBLE);
            mStatusIcon.setVisibility(View.GONE);
            break;

        default:
            break;
        }
    }

    public void setStatusText(String text) {
        mStatusText.setText(text);
    }

    public void setStatusSubtitle(String subtitle) {
        mStatusSubtitle.setText(subtitle);
    }

    /**
     * Override onDestroy to cancel AsyncTask that checks for updates
     */
    @Override
    public void onDestroy() {
        super.onDestroy();

        // cancel AsyncTask for Update check
        mStatusChecker.cancelStatusCheck();
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
            UiHelper.openHostsFile(mActivity);
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
            mStatusChecker.checkForUpdates();
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

        mActivity = getActivity();

        // Initial Status is disabled
        BaseActivity.updateStatusDisabled(mActivity);

        // Initialize logic
        mStatusChecker = new StatusChecker(this);
        // mApplyExecutor = new ApplyExecutor(this.getActivity());
        mRevertExecutor = new RevertExecutor(this);

        mStatusText = (TextView) mActivity.findViewById(R.id.status_text);
        mStatusSubtitle = (TextView) mActivity.findViewById(R.id.status_subtitle);
        mStatusProgress = (ProgressBar) mActivity.findViewById(R.id.status_progress);
        mStatusIcon = (ImageView) mActivity.findViewById(R.id.status_icon);

        // check for root
        if (Utils.isAndroidRooted(mActivity)) {
            // do background update check
            mStatusChecker.checkForUpdatesOnCreate();

            // schedule CheckUpdateService
            WakefulIntentService.scheduleAlarms(new UpdateListener(), mActivity, false);
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
        mRevertExecutor.revert();
    }

}