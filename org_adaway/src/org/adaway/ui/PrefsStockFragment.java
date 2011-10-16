/* Copyright (c) 2011 -- CommonsWare, LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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
import android.preference.PreferenceFragment;
import android.preference.Preference.OnPreferenceChangeListener;

/**
 * Fragment used in preference_headers.xml to enable the use of old preference xmls. see
 * https://github.com/commonsguy/cw-android/blob/master/Prefs/FragmentsBC
 * 
 * @author Dominik Schürmann
 * 
 */
public class PrefsStockFragment extends PreferenceFragment {
    private EditTextPreference mCustomTarget;
    private Context mContext;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int res = getActivity().getResources().getIdentifier(getArguments().getString("resource"),
                "xml", getActivity().getPackageName());

        getPreferenceManager().setSharedPreferencesName(Constants.PREFS_NAME);
        addPreferencesFromResource(res);

        mContext = getActivity();

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
