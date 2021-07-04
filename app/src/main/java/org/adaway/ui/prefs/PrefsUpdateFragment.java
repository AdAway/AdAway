package org.adaway.ui.prefs;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.source.SourceUpdateService;
import org.adaway.model.update.ApkUpdateService;
import org.adaway.model.update.UpdateStore;

import static org.adaway.model.update.UpdateStore.ADAWAY;
import static org.adaway.ui.prefs.PrefsActivity.PREFERENCE_NOT_FOUND;
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
        bindAppChannelPrefAction();
        bindHostsUpdatePrefAction();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        PrefsActivity.setAppBarTitle(this, R.string.pref_update_title);
    }

    private void bindAppUpdatePrefAction() {
        Context context = requireContext();
        SwitchPreferenceCompat checkAppDailyPref = findPreference(getString(R.string.pref_update_check_app_daily_key));
        assert checkAppDailyPref != null : PREFERENCE_NOT_FOUND;
        checkAppDailyPref.setOnPreferenceChangeListener((preference, newValue) -> {
            if (Boolean.TRUE.equals(newValue)) {
                ApkUpdateService.enable(context);
            } else {
                ApkUpdateService.disable(context);
            }
            return true;
        });
    }

    private void bindAppChannelPrefAction() {
        Context context = requireContext();
        AdAwayApplication application = (AdAwayApplication) context.getApplicationContext();
        UpdateStore store = application.getUpdateModel().getStore();
        Preference includeBetaReleasesPref = findPreference(getString(R.string.pref_update_include_beta_releases_key));
        assert includeBetaReleasesPref != null : PREFERENCE_NOT_FOUND;
        includeBetaReleasesPref.setEnabled(store == ADAWAY);
    }

    private void bindHostsUpdatePrefAction() {
        Context context = requireContext();
        Preference checkHostsDailyPref = findPreference(getString(R.string.pref_update_check_hosts_daily_key));
        assert checkHostsDailyPref != null : PREFERENCE_NOT_FOUND;
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
        assert updateOnlyOnWifiPref != null : PREFERENCE_NOT_FOUND;
        updateOnlyOnWifiPref.setOnPreferenceChangeListener((preference, newValue) -> {
            SourceUpdateService.enable(context, Boolean.TRUE.equals(newValue));
            return true;
        });
    }
}
