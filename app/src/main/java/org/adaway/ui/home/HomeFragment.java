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

package org.adaway.ui.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.CardView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.adaway.R;
import org.adaway.helper.ApplyHelper;
import org.adaway.helper.PreferenceHelper;
import org.adaway.helper.RevertHelper;
import org.adaway.service.UpdateService;
import org.adaway.ui.help.HelpActivity;
import org.adaway.util.Constants;
import org.adaway.util.StatusCodes;
import org.adaway.util.WebServerUtils;

public class HomeFragment extends Fragment {
    // Intent extras to give result of applying process to base activity
    public static final String EXTRA_APPLYING_RESULT = "org.adaway.APPLYING_RESULT";
    public static final String EXTRA_NUMBER_OF_SUCCESSFUL_DOWNLOADS = "org.adaway.NUMBER_OF_SUCCESSFUL_DOWNLOADS";
    public static final String EXTRA_UPDATE_STATUS_TITLE = "org.adaway.UPDATE_STATUS.TITLE";
    public static final String EXTRA_UPDATE_STATUS_TEXT = "org.adaway.UPDATE_STATUS.TEXT";
    public static final String EXTRA_UPDATE_STATUS_ICON = "org.adaway.UPDATE_STATUS.ICON";
    // Intent definitions for LocalBroadcastManager to update status from other threads
    static final String ACTION_UPDATE_STATUS = "org.adaway.UPDATE_STATUS";

    private FragmentActivity mActivity;
    private BroadcastReceiver mBroadcastReceiver;

    /*
     * Current statuses.
     */
    /**
     * The status title text.
     */
    private String mCurrentStatusTitle;
    /**
     * The status text.
     */
    private String mCurrentStatusText;
    /**
     * The status icon code.
     */
    private int mCurrentStatusIconStatus;
    /**
     * The status of the hosts files buttons.
     */
    private HostsStatus mHostsButtonStatus;
    /**
     * The web server running status (<code>true</code> if running, <code>false</code> otherwise).
     */
    private boolean mWebServerRunning = false;
    /*
     * Status card views.
     */
    /**
     * The status progress bar (<code>null</code> until view created).
     */
    private ProgressBar mStatusProgressBar;
    /**
     * The status icon image view (<code>null</code> until view created).
     */
    private ImageView mStatusIconImageView;
    /**
     * The status title text view (<code>null</code> until view created).
     */
    private TextView mStatusTitleTextView;
    /**
     * The status title text view (<code>null</code> until view created).
     */
    private TextView mStatusTextView;
    /**
     * The update hosts button (<code>null</code> until view created).
     */
    private Button mUpdateHostsButton;
    /**
     * The revert hosts button (<code>null</code> until view created).
     */
    private Button mRevertHostsButton;
    /*
     * Web server card views.
     */
    /**
     * The web server status text(<code>null</code> until view created).
     */
    private TextView mWebSeverStatusTextView;
    /**
     * The web server status icon(<code>null</code> until view created).
     */
    private ImageView mWebServerStatusImageView;
    /**
     * The enable/disable web server button (<code>null</code> until view created).
     */
    private Button mRunningWebServerButton;

    /**
     * Static helper method to send broadcasts to the MainActivity and update status in frontend
     *
     * @param context    The application context.
     * @param title      The status title.
     * @param text       The status text.
     * @param iconStatus The status icon ({@link StatusCodes#UPDATE_AVAILABLE},
     *                   {@link StatusCodes#ENABLED}, {@link StatusCodes#DISABLED},
     *                   {@link StatusCodes#DOWNLOAD_FAIL} or {@link StatusCodes#CHECKING}.
     */
    public static void setStatusBroadcast(Context context, String title, String text, int iconStatus) {
        // Get local broadcast manager
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(context);
        // Create intent to update status
        Intent intent = new Intent(HomeFragment.ACTION_UPDATE_STATUS);
        intent.putExtra(HomeFragment.EXTRA_UPDATE_STATUS_ICON, iconStatus);
        intent.putExtra(HomeFragment.EXTRA_UPDATE_STATUS_TITLE, title);
        intent.putExtra(HomeFragment.EXTRA_UPDATE_STATUS_TEXT, text);
        // Send intent using local broadcast
        localBroadcastManager.sendBroadcast(intent);
    }

    /**
     * Wrapper to set status to enabled.
     *
     * @param context The application context.
     */
    public static void updateStatusEnabled(Context context) {
        HomeFragment.setStatusBroadcast(
                context,
                context.getString(R.string.status_enabled),
                context.getString(R.string.status_enabled_subtitle),
                StatusCodes.ENABLED
        );
    }

    /**
     * Wrapper to set status to disabled.
     *
     * @param context The application context.
     */
    public static void updateStatusDisabled(Context context) {
        HomeFragment.setStatusBroadcast(
                context,
                context.getString(R.string.status_disabled),
                context.getString(R.string.status_disabled_subtitle),
                StatusCodes.DISABLED
        );
    }

    /**
     * Set status in Fragment
     *
     * @param title      The status title.
     * @param text       The status text.
     * @param iconStatus The status icon ({@link StatusCodes#UPDATE_AVAILABLE},
     *                   {@link StatusCodes#ENABLED}, {@link StatusCodes#DISABLED},
     *                   {@link StatusCodes#DOWNLOAD_FAIL} or {@link StatusCodes#CHECKING}.
     */
    public void setStatus(String title, String text, int iconStatus) {
        // Update status title
        mStatusTitleTextView.setText(title);
        // Update status text
        mStatusTextView.setText(text);
        // Update status icon and progress bar
        switch (iconStatus) {
            case StatusCodes.UPDATE_AVAILABLE:
                mStatusProgressBar.setVisibility(View.GONE);
                mStatusIconImageView.setVisibility(View.VISIBLE);
                mStatusIconImageView.setImageResource(R.drawable.status_update);
                break;
            case StatusCodes.ENABLED:
                mStatusProgressBar.setVisibility(View.GONE);
                mStatusIconImageView.setVisibility(View.VISIBLE);
                mStatusIconImageView.setImageResource(R.drawable.status_enabled);
                break;
            case StatusCodes.DISABLED:
                mStatusProgressBar.setVisibility(View.GONE);
                mStatusIconImageView.setVisibility(View.VISIBLE);
                mStatusIconImageView.setImageResource(R.drawable.status_disabled);
                break;
            case StatusCodes.DOWNLOAD_FAIL:
                mStatusProgressBar.setVisibility(View.GONE);
                mStatusIconImageView.setImageResource(R.drawable.status_fail);
                mStatusIconImageView.setVisibility(View.VISIBLE);
                break;
            case StatusCodes.CHECKING:
                mStatusProgressBar.setVisibility(View.VISIBLE);
                mStatusIconImageView.setVisibility(View.GONE);
                break;
            default:
                break;
        }
        // Save statuses for configuration change
        mCurrentStatusTitle = title;
        mCurrentStatusText = text;
        mCurrentStatusIconStatus = iconStatus;
        // Update update hosts button label
        switch (iconStatus) {
            case StatusCodes.SUCCESS:
            case StatusCodes.ENABLED:
                setHostsButtonStatus(HostsStatus.ENABLED);
                break;
            case StatusCodes.UPDATE_AVAILABLE:
                setHostsButtonStatus(HostsStatus.UPDATE_AVAILABLE);
                break;
            case StatusCodes.DISABLED:
            case StatusCodes.REVERT_SUCCESS:
                setHostsButtonStatus(HostsStatus.DISABLED);
                break;
            default:
                break;
        }
        // Update button enable state
        boolean enabledButton = iconStatus != StatusCodes.CHECKING;
        mUpdateHostsButton.setEnabled(enabledButton);
        mRevertHostsButton.setEnabled(enabledButton);
    }

    /**
     * Save UI state changes to the savedInstanceState. This bundle will be passed to onCreate if
     * the process is killed and restarted like on orientation change.
     */
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Append current statuses
        outState.putString("statusTitle", mCurrentStatusTitle);
        outState.putString("statusText", mCurrentStatusText);
        outState.putInt("statusIconStatus", mCurrentStatusIconStatus);
        outState.putSerializable("hostsButtonStatus", mHostsButtonStatus);
        outState.putBoolean("webServerRunning", mWebServerRunning);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivity = this.getActivity();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate layout
        View view = inflater.inflate(R.layout.home_fragment, container, false);
        /*
         * Retrieve view elements.
         */
        // Get view from help card
        Button showHelpButton = view.findViewById(R.id.home_show_help);
        // Get views from status card
        mStatusProgressBar = view.findViewById(R.id.home_status_progress);
        mStatusIconImageView = view.findViewById(R.id.home_status_icon);
        mStatusTitleTextView = view.findViewById(R.id.home_status_title);
        mStatusTextView = view.findViewById(R.id.home_status_text);
        mUpdateHostsButton = view.findViewById(R.id.home_update_hosts);
        mRevertHostsButton = view.findViewById(R.id.home_revert_hosts);
        // Get views from web server card
        CardView webServerCardView = view.findViewById(R.id.home_webserver_card);
        mWebSeverStatusTextView = view.findViewById(R.id.home_webserver_status);
        mWebServerStatusImageView = view.findViewById(R.id.home_webserver_icon);
        mRunningWebServerButton = view.findViewById(R.id.home_webserver_enable);
        /*
         * Register local broadcast receiver.
         */
        // Get fragment context
        Context context = this.getContext();
        // Check if broadcast is already initialized
        if (mBroadcastReceiver == null && context != null) {
            // We use this to send broadcasts within our local process.
            LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(context);
            // We are going to watch for broadcasts with status updates
            IntentFilter filter = new IntentFilter();
            filter.addAction(HomeFragment.ACTION_UPDATE_STATUS);
            mBroadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Bundle extras = intent.getExtras();
                    String action = intent.getAction();
                    if (action == null || extras == null) {
                        return;
                    }
                    if (action.equals(HomeFragment.ACTION_UPDATE_STATUS)
                            && extras.containsKey(HomeFragment.EXTRA_UPDATE_STATUS_TITLE)
                            && extras.containsKey(HomeFragment.EXTRA_UPDATE_STATUS_TEXT)
                            && extras.containsKey(HomeFragment.EXTRA_UPDATE_STATUS_ICON)) {
                        // Get title, text and status from extras
                        String title = extras.getString(HomeFragment.EXTRA_UPDATE_STATUS_TITLE);
                        String text = extras.getString(HomeFragment.EXTRA_UPDATE_STATUS_TEXT);
                        int status = extras.getInt(HomeFragment.EXTRA_UPDATE_STATUS_ICON);
                        // Update view
                        HomeFragment.this.setStatus(title, text, status);
                    }
                }
            };
            localBroadcastManager.registerReceiver(mBroadcastReceiver, filter);
        }
        /*
         * Initialize statuses and behaviors.
         */
        // Set show help button click listener
        showHelpButton.setOnClickListener(this::showMoreHelp);
        // Set update hosts button click listener
        mUpdateHostsButton.setOnClickListener(this::updateHosts);
        // Set revert hosts button click listener
        mRevertHostsButton.setOnClickListener(this::revertHosts);
        // Update web server card visibility
        boolean webServerCardVisible = context != null && PreferenceHelper.getWebServerEnabled(context);
        if (!webServerCardVisible) {
            webServerCardView.setVisibility(View.GONE);
        }
        // Set running web server button click listener
        mRunningWebServerButton.setOnClickListener(this::toggleWebServer);
        // Check statuses to restore
        if (savedInstanceState == null) {
            // Check hosts status
            new UpdateHostsStatusAsyncTask(this).execute();
            // Request to update host status
            if (webServerCardVisible) {
                // Check web server status
                new UpdateWebServerStatusAsyncTask(this).execute();
            }
        } else {
            // get back status state when orientation changes and recreates activity

            Log.d(Constants.TAG, "HomeFragment coming from an orientation change!");

            HostsStatus hostsButtonStatus = (HostsStatus) savedInstanceState.getSerializable("hostsButtonStatus");
            if (hostsButtonStatus != null) {
                setHostsButtonStatus(hostsButtonStatus);
            }
            String title = savedInstanceState.getString("statusTitle");
            String text = savedInstanceState.getString("statusText");
            int iconStatus = savedInstanceState.getInt("statusIconStatus");
            if (title != null && text != null && iconStatus != -1) {
                setStatus(title, text, iconStatus);
            }
            boolean webServerRunning = savedInstanceState.getBoolean("webServerRunning");
            notifyWebServerRunning(webServerRunning);
        }


        // Return inflated view
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Get fragment context
        Context context = this.getContext();
        // Unregister broadcast receiver
        if (mBroadcastReceiver != null && context != null) {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(mBroadcastReceiver);
            mBroadcastReceiver = null;
        }
    }

    /**
     * Set the hosts button statuses.
     *
     * @param hostsStatus The hosts file status.
     */
    private void setHostsButtonStatus(HostsStatus hostsStatus) {
        // Store hosts status
        mHostsButtonStatus = hostsStatus;
        // Update update hosts button label
        switch (hostsStatus) {
            case ENABLED:
                mUpdateHostsButton.setText(R.string.button_check_update_hosts);
                mRevertHostsButton.setVisibility(View.VISIBLE);
                break;
            case UPDATE_AVAILABLE:
                mUpdateHostsButton.setText(R.string.button_update_hosts);
                break;
            case DISABLED:
                mUpdateHostsButton.setText(R.string.button_enable_hosts);
                mRevertHostsButton.setVisibility(View.GONE);
                break;
        }
    }

    /**
     * Button Action to download and apply hosts files
     *
     * @param view The view which trigger the action.
     */
    private void updateHosts(@Nullable View view) {
        switch (this.mHostsButtonStatus) {
            case UPDATE_AVAILABLE:
            case DISABLED:
                ApplyHelper.applyAsync(mActivity);
                break;
            case ENABLED:
                UpdateService.checkAsync(mActivity);
                break;
        }
    }

    /**
     * Revert to default hosts file.
     *
     * @param view The view which trigger the action.
     */
    private void revertHosts(@Nullable View view) {
        new RevertHelper(mActivity).revert();
    }

    /**
     * Show more help.
     *
     * @param view The view which trigger the action.
     */
    private void showMoreHelp(@Nullable View view) {
        // Start help activity
        this.startActivity(new Intent(mActivity, HelpActivity.class));
    }

    /**
     * Toggle web server running.
     *
     * @param view The view which trigger the action.
     */
    private void toggleWebServer(@Nullable View view) {
        if (mWebServerRunning) {
            WebServerUtils.stopWebServer();
        } else {
            WebServerUtils.startWebServer(mActivity);
        }
        this.notifyWebServerRunning(!mWebServerRunning);
    }

    /**
     * Notify the web server is running.
     *
     * @param running <code>true</code> if the web server is running, <code>false</code> otherwise.
     */
    void notifyWebServerRunning(boolean running) {
        // Check button
        if (mWebSeverStatusTextView == null || mWebServerStatusImageView == null || mRunningWebServerButton == null) {
            return;
        }
        // Store web server running status
        mWebServerRunning = running;
        // Update status text and icon
        mWebSeverStatusTextView.setText(running ?
                R.string.webserver_status_running :
                R.string.webserver_status_stopped
        );
        mWebServerStatusImageView.setImageResource(running ?
                R.drawable.status_enabled :
                R.drawable.status_disabled
        );
        // Update button text
        mRunningWebServerButton.setText(running ?
                R.string.button_disable_webserver :
                R.string.button_enable_webserver
        );
    }
}
