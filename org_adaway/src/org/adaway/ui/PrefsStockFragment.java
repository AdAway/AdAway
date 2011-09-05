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

import android.os.Bundle;
import android.preference.PreferenceFragment;

/**
 * Fragment used in preference_headers.xml to enable the use of old preference xmls.
 * see https://github.com/commonsguy/cw-android/blob/master/Prefs/FragmentsBC
 * 
 * @author Dominik Schürmann
 * 
 */
public class PrefsStockFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int res = getActivity().getResources().getIdentifier(getArguments().getString("resource"),
                "xml", getActivity().getPackageName());

        addPreferencesFromResource(res);
    }
}
