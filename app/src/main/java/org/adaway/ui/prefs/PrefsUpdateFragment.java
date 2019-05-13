package org.adaway.ui.prefs;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.service.hosts.UpdateService;

import static org.adaway.util.Constants.PREFS_NAME;

/**
 * This fragment is the preferences fragment for update settings.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class PrefsUpdateFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Configure preferences
        getPreferenceManager().setSharedPreferencesName(PREFS_NAME);
        addPreferencesFromResource(R.xml.preferences_update);
        // Bind pref action
        bindUpdatePrefAction();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        PrefsActivity.setAppBarTitle(this, R.string.pref_update_title);
    }

    private void bindUpdatePrefAction() {
        Context context = getContext();
        /*
         * Listen on click of update daily pref, register UpdateService if enabled,
         * setOnPreferenceChangeListener is not used because it is executed before setting the
         * preference value, this would lead to a false check in UpdateListener
         */
        Preference updateDailyPref = findPreference(getString(R.string.pref_update_check_daily_key));
        Preference updateOnlyOnWifiPref = findPreference(this.getString(R.string.pref_update_only_on_wifi_key));
        Preference.OnPreferenceClickListener onPreferenceClickListener = preference -> {
            if (PreferenceHelper.getUpdateCheckDaily(context)) {
                boolean unmeteredNetworkOnly = PreferenceHelper.getUpdateOnlyOnWifi(context);
                UpdateService.enable(unmeteredNetworkOnly);
            } else {
                UpdateService.disable();
            }
            return false;
        };
        updateDailyPref.setOnPreferenceClickListener(onPreferenceClickListener);
        updateOnlyOnWifiPref.setOnPreferenceClickListener(onPreferenceClickListener);
    }
}
