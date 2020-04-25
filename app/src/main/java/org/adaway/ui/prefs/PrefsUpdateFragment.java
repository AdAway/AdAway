package org.adaway.ui.prefs;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.source.SourceUpdateService;
import org.adaway.model.update.ApkUpdateService;

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
        // Bind pref actions
        bindAppUpdatePrefAction();
        bindHostsUpdatePrefAction();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        PrefsActivity.setAppBarTitle(this, R.string.pref_update_title);
    }

    private void bindAppUpdatePrefAction() {
        Context context = requireContext();
        CheckBoxPreference checkAppDailyPref = findPreference(getString(R.string.pref_update_check_app_daily_key));
        checkAppDailyPref.setOnPreferenceChangeListener((preference, newValue) -> {
            if (Boolean.TRUE.equals(newValue)) {
                ApkUpdateService.enable(context);
            } else {
                ApkUpdateService.disable(context);
            }
            return true;
        });
    }

    private void bindHostsUpdatePrefAction() {
        Context context = requireContext();
        Preference checkHostsDailyPref = findPreference(getString(R.string.pref_update_check_hosts_daily_key));
        checkHostsDailyPref.setOnPreferenceChangeListener((preference, newValue) -> {
            if (Boolean.TRUE.equals(newValue)) {
                boolean unmeteredNetworkOnly = PreferenceHelper.getUpdateOnlyOnWifi(context);
                SourceUpdateService.enable(context, unmeteredNetworkOnly);
            } else {
                SourceUpdateService.disable(context);
            }
            return true;
        });
        Preference updateOnlyOnWifiPref = findPreference(this.getString(R.string.pref_update_only_on_wifi_key));
        updateOnlyOnWifiPref.setOnPreferenceChangeListener((preference, newValue) -> {
            SourceUpdateService.enable(context, Boolean.TRUE.equals(newValue));
            return true;
        });
    }
}
