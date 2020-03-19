package org.adaway.ui.prefs;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.adaway.R;
import org.adaway.ui.prefs.exclusion.PrefsVpnExcludedAppsActivity;
import org.adaway.vpn.VpnService;

import static org.adaway.util.Constants.PREFS_NAME;

/**
 * This fragment is the preferences fragment for VPN ad blocker.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class PrefsVpnFragment extends PreferenceFragmentCompat {
    private static final int RESTART_VPN_REQUEST_CODE = 2000;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Configure preferences
        getPreferenceManager().setSharedPreferencesName(PREFS_NAME);
        addPreferencesFromResource(R.xml.preferences_vpn);
        // Bind pref actions
        bindExcludedSystemApps();
        bindExcludedUserApps();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        PrefsActivity.setAppBarTitle(this, R.string.pref_vpn_title);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == RESTART_VPN_REQUEST_CODE) {
            restartVpn();
        }
    }

    private void bindExcludedSystemApps() {
        ListPreference excludeUserAppsPreferences = findPreference(getString(R.string.pref_vpn_excluded_system_apps_key));
        excludeUserAppsPreferences.setOnPreferenceChangeListener((preference, newValue) -> {
            restartVpn();
            return true;
        });
    }

    private void bindExcludedUserApps() {
        Context context = requireContext();
        Preference excludeUserAppsPreferences = findPreference(getString(R.string.pref_vpn_excluded_user_apps_key));
        excludeUserAppsPreferences.setOnPreferenceClickListener(preference -> {
            startActivityForResult(new Intent(context, PrefsVpnExcludedAppsActivity.class), RESTART_VPN_REQUEST_CODE);
            return true;
        });
    }

    private void restartVpn() {
        Context context = requireContext();
        if (VpnService.isStarted(context)) {
            VpnService.stop(context);
            VpnService.start(context);
        }
    }
}
