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
import org.adaway.service.UpdateCheckService;
import org.adaway.util.Constants;
import org.adaway.util.WebserverUtils;

import android.content.Context;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.Preference.OnPreferenceClickListener;

/**
 * Fragment used in preference_headers.xml to enable the use of old preference xmls. see
 * https://github.com/commonsguy/cw-android/blob/master/Prefs/FragmentsBC
 * 
 * @author Dominik Schürmann
 * 
 */
public class PrefsStockFragment extends PreferenceFragment {
    private CheckBoxPreference mUpdateCheckDaily;
    private CheckBoxPreference mWebserverEnabled;
    private Context mContext;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int res = getActivity().getResources().getIdentifier(getArguments().getString("resource"),
                "xml", getActivity().getPackageName());

        getPreferenceManager().setSharedPreferencesName(Constants.PREFS_NAME);
        addPreferencesFromResource(res);

        mContext = getActivity();

        // find checkbox for update check daily
        mUpdateCheckDaily = (CheckBoxPreference) getPreferenceScreen().findPreference(
                getString(R.string.pref_update_check_daily_key));

        // set preference click to enable update service
        mUpdateCheckDaily.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference arg0) {
                if (mUpdateCheckDaily.isChecked()) {
                    UpdateCheckService.registerAlarm(mContext);
                } else {
                    UpdateCheckService.unregisterAlarm(mContext);
                }
                return false;
            }
        });

        // find checkbox for webserver enabled
        mWebserverEnabled = (CheckBoxPreference) getPreferenceScreen().findPreference(
                getString(R.string.pref_webserver_enabled_key));

        // set preference click to install webserver and start it
        mWebserverEnabled.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference arg0) {
                if (mWebserverEnabled.isChecked()) {
                    // install webserver if not already there
                    WebserverUtils.installWebserver(mContext);
                    // start webserver
                    WebserverUtils.startWebserver(mContext);
                } else {
                    // start webserver
                    WebserverUtils.stopWebserver(mContext);
                }
                return false;
            }
        });

    }
}
