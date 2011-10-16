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
import org.adaway.helper.PreferencesHelper;
import org.adaway.service.UpdateCheckService;
import org.adaway.util.Constants;
import org.adaway.util.WebserverUtils;

import android.content.Context;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;

/**
 * Preference Activity used on Android 2.x devices
 * 
 * @author Dominik Schürmann
 * 
 */
public class PrefsActivity extends PreferenceActivity {
    private EditTextPreference mCustomTarget;
    private Context mContext;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = this;

        getPreferenceManager().setSharedPreferencesName(Constants.PREFS_NAME);
        addPreferencesFromResource(R.xml.preferences);

        /* Listen on change of update daily pref, register UpdateCheckService if enabled */
        Preference UpdateDailyPref = findPreference(getString(R.string.pref_update_check_daily_key));
        UpdateDailyPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue.equals(true)) {
                    UpdateCheckService.registerAlarm(mContext);
                } else {
                    UpdateCheckService.unregisterAlarm(mContext);
                }
                return true;
            }
        });

        /* Start webserver if pref is enabled */
        Preference WebserverEnabledPref = findPreference(getString(R.string.pref_webserver_enabled_key));
        WebserverEnabledPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue.equals(true)) {
                    // install webserver if not already there
                    WebserverUtils.installWebserver(mContext);
                    // start webserver
                    WebserverUtils.startWebserver(mContext);
                } else {
                    // stop webserver
                    WebserverUtils.stopWebserver(mContext);
                }
                return true;
            }
        });

        // find custom target edit
        mCustomTarget = (EditTextPreference) getPreferenceScreen().findPreference(
                getString(R.string.pref_custom_target_key));

        // enable custom target pref on create if enabled in apply method
        if (PreferencesHelper.getApplyMethod(mContext).equals("customTarget")) {
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

    }
}