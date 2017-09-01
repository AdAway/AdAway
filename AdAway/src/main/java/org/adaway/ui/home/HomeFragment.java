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
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.adaway.R;
import org.adaway.helper.ApplyHelper;
import org.adaway.helper.OpenHelper;
import org.adaway.helper.PreferenceHelper;
import org.adaway.helper.RevertHelper;
import org.adaway.service.UpdateService;
import org.adaway.ui.adware.ScanAdwareFragment;
import org.adaway.ui.help.HelpActivity;
import org.adaway.ui.prefs.PrefsActivity;
import org.adaway.util.ApplyUtils;
import org.adaway.util.Constants;
import org.adaway.util.StatusCodes;
import org.adaway.util.Utils;

public class HomeFragment extends Fragment {
    // Intent extras to give result of applying process to base activity
    public static final String EXTRA_APPLYING_RESULT = "org.adaway.APPLYING_RESULT";
    public static final String EXTRA_NUMBER_OF_SUCCESSFUL_DOWNLOADS = "org.adaway.NUMBER_OF_SUCCESSFUL_DOWNLOADS";
    public static final String EXTRA_UPDATE_STATUS_TITLE = "org.adaway.UPDATE_STATUS.TITLE";
    public static final String EXTRA_UPDATE_STATUS_TEXT = "org.adaway.UPDATE_STATUS.TEXT";
    public static final String EXTRA_UPDATE_STATUS_ICON = "org.adaway.UPDATE_STATUS.ICON";
    public static final String EXTRA_BUTTONS_DISABLED = "org.adaway.BUTTONS.ENABLED";
    // Intent definitions for LocalBroadcastManager to update status from other threads
    static final String ACTION_UPDATE_STATUS = "org.adaway.UPDATE_STATUS";
    static final String ACTION_BUTTONS = "org.adaway.BUTTONS";
    HomeFragment mHomeFragment;
    WebserverFragment mWebserverFragment;

    private FragmentActivity mActivity;

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
    private BroadcastReceiver mBroadcastReceiver;


    /**
     * Static helper method to send broadcasts to the MainActivity and update status in frontend
     *
     * @param context
     * @param title
     * @param text
     * @param iconStatus Select UPDATE_AVAILABLE, ENABLED, DISABLED, DOWNLOAD_FAIL, or CHECKING from
     *                   StatusCodes
     */
    public static void setStatusBroadcast(Context context, String title, String text, int iconStatus) {
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(context);

        Intent intent = new Intent(ACTION_UPDATE_STATUS);
        intent.putExtra(EXTRA_UPDATE_STATUS_ICON, iconStatus);
        intent.putExtra(EXTRA_UPDATE_STATUS_TITLE, title);
        intent.putExtra(EXTRA_UPDATE_STATUS_TEXT, text);
        localBroadcastManager.sendBroadcast(intent);
    }

    /**
     * Static helper method to send broadcasts to the MainActivity and enable or disable buttons
     *
     * @param context
     * @param buttonsDisabled to enable buttons apply and revert
     */
    public static void setButtonsDisabledBroadcast(Context context, boolean buttonsDisabled) {
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(context);

        Intent intent = new Intent(ACTION_BUTTONS);
        intent.putExtra(EXTRA_BUTTONS_DISABLED, buttonsDisabled);
        localBroadcastManager.sendBroadcast(intent);
    }

    /**
     * Wrapper to set status to enabled
     *
     * @param context
     */
    public static void updateStatusEnabled(Context context) {
        setStatusBroadcast(context, context.getString(R.string.status_enabled),
                context.getString(R.string.status_enabled_subtitle), StatusCodes.ENABLED);
    }

    /**
     * Wrapper to set status to disabled
     *
     * @param context
     */
    public static void updateStatusDisabled(Context context) {
        setStatusBroadcast(context, context.getString(R.string.status_disabled),
                context.getString(R.string.status_disabled_subtitle), StatusCodes.DISABLED);
    }

    /**
     * Set status icon based on StatusCodes
     *
     * @param iconStatus Select UPDATE_AVAILABLE, ENABLED, DISABLED, DOWNLOAD_FAIL, or CHECKING from
     *                   StatusCodes
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
     * @param iconStatus int based on StatusCodes to select icon
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
            case R.id.menu_show_hosts_file:
                OpenHelper.openHostsFile(mActivity);
                return true;

            case R.id.menu_preferences:
                startActivity(new Intent(mActivity, PrefsActivity.class));
                return true;

            case R.id.menu_scan_adware:
                startActivity(new Intent(mActivity, ScanAdwareFragment.class));
                return true;

            case R.id.menu_refresh:
                UpdateService.checkAsync(mActivity);
                return true;

            case R.id.menu_help:
                startActivity(new Intent(mActivity, HelpActivity.class));
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setHasOptionsMenu(true); // enable options menu for this fragment
        mHomeFragment = this;
        mActivity = this.getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate layout
        View view = inflater.inflate(R.layout.home_fragment, container, false);
        // Retrieve view components
        mStatusTitle = view.findViewById(R.id.status_title);
        mStatusText = view.findViewById(R.id.status_text);
        mStatusProgress = view.findViewById(R.id.status_progress);
        mStatusIcon = view.findViewById(R.id.status_icon);
        mApplyButton = view.findViewById(R.id.apply_button);
        mApplyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                applyOnClick(view);
            }
        });
        mRevertButton = view.findViewById(R.id.revert_button);
        mRevertButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                revertOnClick(view);
            }
        });

        /*
         * Register local broadcast receiver.
         */
        if (mBroadcastReceiver == null) {
            // We use this to send broadcasts within our local process.
            LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this.getContext());
            // We are going to watch for broadcasts with status updates
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_UPDATE_STATUS);
            filter.addAction(ACTION_BUTTONS);
            mBroadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Bundle extras = intent.getExtras();
                    if (extras == null) {
                        return;
                    }
                    switch (intent.getAction()) {
                        case ACTION_UPDATE_STATUS:
                            if (extras.containsKey(EXTRA_UPDATE_STATUS_TITLE)
                                    && extras.containsKey(EXTRA_UPDATE_STATUS_TEXT)
                                    && extras.containsKey(EXTRA_UPDATE_STATUS_ICON)) {

                                String title = extras.getString(EXTRA_UPDATE_STATUS_TITLE);
                                String text = extras.getString(EXTRA_UPDATE_STATUS_TEXT);
                                int status = extras.getInt(EXTRA_UPDATE_STATUS_ICON);

                                mHomeFragment.setStatus(title, text, status);
                            }
                            break;
                        case ACTION_BUTTONS:
                            if (extras.containsKey(EXTRA_BUTTONS_DISABLED)) {

                                boolean buttonsDisabled = extras.getBoolean(EXTRA_BUTTONS_DISABLED);

                                mHomeFragment.setButtonsDisabled(buttonsDisabled);
                            }
                            break;
                    }
                }
            };
            localBroadcastManager.registerReceiver(mBroadcastReceiver, filter);
        }

        /*
         * Initialize current status.
         */
        if (savedInstanceState == null) {
            // check for root
            if (Utils.isAndroidRooted(mActivity)) {
                // set status only if not coming from an orientation change
                // check if hosts file is applied
                if (ApplyUtils.isHostsFileCorrect(Constants.ANDROID_SYSTEM_ETC_HOSTS)) {
                    // do background update check
                    // do only if not disabled in preferences
                    if (PreferenceHelper.getUpdateCheck(mActivity)) {
                        UpdateService.checkAsync(this.getContext());
                    } else {
                        HomeFragment.updateStatusEnabled(mActivity);
                    }
                } else {
                    HomeFragment.updateStatusDisabled(mActivity);
                }
                // schedule CheckUpdateService
                UpdateService.enable();
            }
        } else {
            // get back status state when orientation changes and recreates activity

            Log.d(Constants.TAG, "HomeFragment coming from an orientation change!");

            String title = savedInstanceState.getString("statusTitle");
            String text = savedInstanceState.getString("statusText");
            int iconStatus = savedInstanceState.getInt("statusIconStatus");
            if (title != null && text != null && iconStatus != -1) {
                setStatus(title, text, iconStatus);
            }

            boolean buttonsDisabled = savedInstanceState.getBoolean("buttonsEnabled");
            setButtonsDisabled(buttonsDisabled);
        }

        // Return inflated view
        return view;
    }

//    @Override
//    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
//        super.onActivityCreated(savedInstanceState);
//        // get back status state when orientation changes and recreates activity
//        if (savedInstanceState != null) {
//            Log.d(Constants.TAG, "HomeFragment coming from an orientation change!");
//
//            String title = savedInstanceState.getString("statusTitle");
//            String text = savedInstanceState.getString("statusText");
//            int iconStatus = savedInstanceState.getInt("statusIconStatus");
//            if (title != null && text != null && iconStatus != -1) {
//                setStatus(title, text, iconStatus);
//            }
//
//            boolean buttonsDisabled = savedInstanceState.getBoolean("buttonsEnabled");
//            setButtonsDisabled(buttonsDisabled);
//        }
//    }

    @Override
    public void onStart() {
        super.onStart();
        // Append webserver fragment onStart as we need the layout ready to add another fragement
        FragmentManager fragmentManager = this.getFragmentManager();
        // add WebserverFragment when enabled in preferences
        if (PreferenceHelper.getWebserverEnabled(this.getContext())) {
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

            mWebserverFragment = new WebserverFragment();
            // replace container in view with fragment
            fragmentTransaction.replace(R.id.base_activity_webserver_container, mWebserverFragment);
            fragmentTransaction.commit();
        } else {
            // when disabled in preferences remove fragment if existing
            if (mWebserverFragment != null) {
                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

                fragmentTransaction.remove(mWebserverFragment);
                fragmentTransaction.commit();

                mWebserverFragment = null;
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Unregister broadcast receiver
        if (mBroadcastReceiver != null) {
            LocalBroadcastManager.getInstance(this.getContext()).unregisterReceiver(mBroadcastReceiver);
            mBroadcastReceiver = null;
        }
    }

    /**
     * Button Action to download and apply hosts files
     *
     * @param view
     */

    public void applyOnClick(View view) {
        new ApplyHelper(mActivity).apply();
    }

    /**
     * Button Action to Revert to default hosts file
     *
     * @param view
     */
    public void revertOnClick(View view) {
        new RevertHelper(mActivity).revert();
    }
}
