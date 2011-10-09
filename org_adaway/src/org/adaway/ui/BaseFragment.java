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
import org.adaway.helper.ApplyExecutor;
import org.adaway.helper.RevertExecutor;
import org.adaway.helper.StatusChecker;
import org.adaway.helper.UiHelper;
import org.adaway.service.UpdateCheckService;
import org.adaway.util.Constants;
import org.adaway.util.ReturnCodes;
import org.adaway.util.Utils;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.stericson.RootTools.RootTools;

public class BaseFragment extends Fragment {
    private Activity mActivity;

    private int mStatus;
    private TextView mStatusText;
    private TextView mStatusSubtitle;
    private ProgressBar mStatusProgress;
    private ImageView mStatusIcon;

    private StatusChecker mStatusChecker;
    private ApplyExecutor mApplyExecutor;
    private RevertExecutor mRevertExecutor;

    public void setStatusUpdateAvailable() {
        mStatus = ReturnCodes.UPDATE_AVAILABLE;

        mStatusProgress.setVisibility(View.GONE);
        mStatusIcon.setVisibility(View.VISIBLE);
        mStatusIcon.setImageResource(R.drawable.status_update);
        mStatusText.setText(R.string.status_update_available);
        mStatusSubtitle.setText(R.string.status_update_available_subtitle);
    }

    public void setStatusEnabled() {
        mStatus = ReturnCodes.ENABLED;

        mStatusProgress.setVisibility(View.GONE);
        mStatusIcon.setVisibility(View.VISIBLE);
        mStatusIcon.setImageResource(R.drawable.status_enabled);
        mStatusText.setText(R.string.status_enabled);
        mStatusSubtitle.setText(R.string.status_enabled_subtitle);
    }

    public void setStatusDisabled() {
        mStatus = ReturnCodes.DISABLED;

        mStatusProgress.setVisibility(View.GONE);
        mStatusIcon.setVisibility(View.VISIBLE);
        mStatusIcon.setImageResource(R.drawable.status_disabled);
        mStatusText.setText(R.string.status_disabled);
        mStatusSubtitle.setText(R.string.status_disabled_subtitle);
    }

    public void setStatusDownloadFail(String currentURL) {
        mStatus = ReturnCodes.DOWNLOAD_FAIL;

        mStatusProgress.setVisibility(View.GONE);
        mStatusIcon.setImageResource(R.drawable.status_fail);
        mStatusIcon.setVisibility(View.VISIBLE);
        mStatusText.setText(R.string.status_download_fail);
        mStatusSubtitle.setText(getString(R.string.status_download_fail_subtitle) + " "
                + currentURL);
    }

    public void setStatusNoConnection() {
        mStatus = ReturnCodes.NO_CONNECTION;

        mStatusProgress.setVisibility(View.GONE);
        mStatusIcon.setImageResource(R.drawable.status_fail);
        mStatusIcon.setVisibility(View.VISIBLE);
        mStatusText.setText(R.string.status_no_connection);
        mStatusSubtitle.setText(R.string.status_no_connection_subtitle);
    }

    public void setStatusChecking() {
        mStatus = ReturnCodes.CHECKING;

        mStatusProgress.setVisibility(View.VISIBLE);
        mStatusIcon.setVisibility(View.GONE);
        mStatusText.setText(R.string.status_checking);
        mStatusSubtitle.setText(R.string.status_checking_subtitle);
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
     * Don't recreate activity on orientation change, it will break AsyncTask. Using possibility 4
     * from http://blog.doityourselfandroid
     * .com/2010/11/14/handling-progress-dialogs-and-screen-orientation-changes/
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        mStatusText = (TextView) mActivity.findViewById(R.id.status_text);
        mStatusSubtitle = (TextView) mActivity.findViewById(R.id.status_subtitle);
        mStatusProgress = (ProgressBar) mActivity.findViewById(R.id.status_progress);
        mStatusIcon = (ImageView) mActivity.findViewById(R.id.status_icon);

        // build old status
        switch (mStatus) {
        case ReturnCodes.UPDATE_AVAILABLE:
            setStatusUpdateAvailable();
            break;
        case ReturnCodes.ENABLED:
            setStatusEnabled();
            break;
        case ReturnCodes.DISABLED:
            setStatusDisabled();
            break;
        case ReturnCodes.DOWNLOAD_FAIL:
            setStatusDownloadFail("");
            break;
        case ReturnCodes.NO_CONNECTION:
            setStatusNoConnection();
            break;
        case ReturnCodes.CHECKING:
            setStatusChecking();
            break;
        default:
            break;
        }
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

        case R.id.menu_preferences:
            // choose preference screen for android 2.x or 3.x (honeycomb)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
                startActivity(new Intent(mActivity, PrefsActivity.class));
            } else {
                startActivity(new Intent(mActivity, PrefsActivityHC.class));
            }
            return true;

        case R.id.menu_donations:
            UiHelper.showDonationsDialog(mActivity);
            return true;

        case R.id.menu_about:
            UiHelper.showAboutDialog(mActivity);
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

        mStatus = ReturnCodes.DISABLED; // initial status

        // Initialize logic
        mStatusChecker = new StatusChecker(this);
        mApplyExecutor = new ApplyExecutor(this);
        mRevertExecutor = new RevertExecutor(this);

        mStatusText = (TextView) mActivity.findViewById(R.id.status_text);
        mStatusSubtitle = (TextView) mActivity.findViewById(R.id.status_subtitle);
        mStatusProgress = (ProgressBar) mActivity.findViewById(R.id.status_progress);
        mStatusIcon = (ImageView) mActivity.findViewById(R.id.status_icon);

        RootTools.debugMode = Constants.DEBUG;

        // check for root
        if (Utils.isAndroidRooted(mActivity)) {
            // do background update check
            mStatusChecker.checkForUpdatesOnCreate();

            // schedule CheckUpdateService
            UpdateCheckService.registerAlarm(mActivity);
            
            
            if (RootTools.killProcess("mongoose")) {
                Log.d("WORKES", "WORKED");
            } else {
                Log.d("WORKES", "NO");
            }
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
        mApplyExecutor.apply();
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