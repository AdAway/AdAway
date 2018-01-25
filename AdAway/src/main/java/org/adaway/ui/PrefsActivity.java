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

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;
import com.commonsware.cwac.wakeful.WakefulIntentService;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.service.DailyListener;
import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.adaway.util.systemless.AbstractSystemlessMode;
import org.adaway.util.systemless.NotSupportedSystemlessMode;
import org.adaway.util.SystemlessUtils;
import org.adaway.util.Utils;
import org.adaway.util.WebserverUtils;
import org.sufficientlysecure.rootcommands.Shell;

public class PrefsActivity extends SherlockPreferenceActivity {
    private Context mActivity;
    private ActionBar mActionBar;

    private EditTextPreference mCustomTarget;
    private CheckBoxPreference mSystemless;
    private CheckBoxPreference mUpdateCheckDaily;
    private CheckBoxPreference mWebserverOnBoot;

    /**
     * Menu Items
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
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActivity = this;
        mActionBar = getSupportActionBar();

        mActionBar.setDisplayShowTitleEnabled(true);
        mActionBar.setDisplayHomeAsUpEnabled(true);

        getPreferenceManager().setSharedPreferencesName(Constants.PREFS_NAME);
        addPreferencesFromResource(R.xml.preferences);

        /*
         * Enable systemless mode systemless mode if supported.
         */
        Preference SystemlessPref = findPreference(getString(R.string.pref_enable_systemless_key));
        SystemlessPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                // Get device systemless mode
                AbstractSystemlessMode systemlessMode = SystemlessUtils.getSystemlessMode();
                // Check if systemless is supported
                if (!systemlessMode.isSupported()) {
                    return false;
                }
                // Declare successful action status
                boolean successful;
                // Check action to apply
                if (newValue.equals(true)) {
                    // Enable systemless mode
                    successful = systemlessMode.enable(PrefsActivity.this);
                    // Check if reboot is needed
                    if (successful && systemlessMode.isRebootNeededAfterActivation()) {
                        Utils.rebootQuestion(PrefsActivity.this, R.string.enable_systemless_successful_title,
                                R.string.enable_systemless_successful);
                    }
                } else {
                    // Disable systemless mode
                    successful = systemlessMode.disable(PrefsActivity.this);
                    // Check if reboot is needed
                    if (successful && systemlessMode.isRebootNeededAfterDeactivation()) {
                        Utils.rebootQuestion(PrefsActivity.this, R.string.disable_systemless_successful_title,
                                R.string.disable_systemless_successful);
                    }
                }
                // Return successful action status
                return successful;
            }
        });

        /*
         * Listen on click of update daily pref, register UpdateService if enabled,
         * setOnPreferenceChangeListener is not used because it is executed before setting the
         * preference value, this would lead to a false check in UpdateListener
         */
        Preference UpdateDailyPref = findPreference(getString(R.string.pref_update_check_daily_key));
        UpdateDailyPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (PreferenceHelper.getUpdateCheckDaily(mActivity)) {
                    WakefulIntentService.scheduleAlarms(new DailyListener(), mActivity, false);
                } else {
                    WakefulIntentService.cancelAlarms(mActivity);
                }
                return false;
            }

        });

        /* Start webserver if pref is enabled */
        Preference WebserverEnabledPref = findPreference(getString(R.string.pref_webserver_enabled_key));
        WebserverEnabledPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Shell rootShell;
                try {
                    rootShell = Shell.startRootShell();

                    if (newValue.equals(true)) {
                        // start webserver
                        WebserverUtils.startWebserver(mActivity, rootShell);
                    } else {
                        // stop webserver
                        WebserverUtils.stopWebserver(mActivity, rootShell);
                    }

                    rootShell.close();
                } catch (Exception e) {
                    Log.e(Constants.TAG, "Problem while starting/stopping webserver!", e);
                }

                return true;
            }
        });

        // Find systemless mode preferences
        mSystemless = (CheckBoxPreference) getPreferenceScreen().findPreference(getString(R.string.pref_enable_systemless_key));

        // find custom target edit
        mCustomTarget = (EditTextPreference) getPreferenceScreen().findPreference(
                getString(R.string.pref_custom_target_key));

        // enable custom target pref on create if enabled in apply method
        if (PreferenceHelper.getApplyMethod(mActivity).equals("customTarget")) {
            mCustomTarget.setEnabled(true);
        } else {
            mCustomTarget.setEnabled(false);
        }

        /* enable custom target pref if enabled in apply method */
        Preference customTargetPref = findPreference(getString(R.string.pref_apply_method_key));
        customTargetPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue.equals("customTarget")) {
                    mCustomTarget.setEnabled(true);
                } else {
                    mCustomTarget.setEnabled(false);
                }
                return true;
            }
        });

        /*
         * Disable update check daily and webserver on boot when installed on sd card. See
         * http://developer.android.com/guide/appendix/install-location.html why
         */
        mUpdateCheckDaily = (CheckBoxPreference) getPreferenceScreen().findPreference(
                getString(R.string.pref_update_check_daily_key));
        mWebserverOnBoot = (CheckBoxPreference) getPreferenceScreen().findPreference(
                getString(R.string.pref_webserver_on_boot_key));

        if (Utils.isInstalledOnSdCard(this)) {
            mUpdateCheckDaily.setEnabled(false);
            mWebserverOnBoot.setEnabled(false);
            mUpdateCheckDaily.setSummary(R.string.pref_sdcard_problem);
            mWebserverOnBoot.setSummary(R.string.pref_sdcard_problem);
        } else {
            mUpdateCheckDaily.setEnabled(true);
            mWebserverOnBoot.setEnabled(true);
            mUpdateCheckDaily.setSummary(R.string.pref_update_check_daily_summary);
            mWebserverOnBoot.setSummary(R.string.pref_webserver_on_boot_summary);
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check preference if systemless mode is enabled
        new SystemlessCheckTask().execute();
    }

    /**
     * This class is an async task to check systemless mode status.
     *
     * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
     */
    private class SystemlessCheckTask extends AsyncTask<Void, Void, AbstractSystemlessMode> {
        @Override
        protected AbstractSystemlessMode doInBackground(Void... params) {
            // Retrieve the systemless mode
            return SystemlessUtils.getSystemlessMode();
        }

        @Override
        protected void onPostExecute(AbstractSystemlessMode systemlessMode) {
            // Ensure reference exists
            if (mSystemless == null) {
                return;
            }
            // Enable setting and set initial value
            mSystemless.setEnabled(systemlessMode.isSupported());
            mSystemless.setChecked(systemlessMode.isEnabled(PrefsActivity.this));
        }
    }
}