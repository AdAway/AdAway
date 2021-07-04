package org.adaway.ui.prefs;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.NonNull;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.adaway.R;
import org.adaway.ui.prefs.exclusion.PrefsVpnExcludedAppsActivity;
import org.adaway.vpn.VpnService;

import static org.adaway.ui.prefs.PrefsActivity.PREFERENCE_NOT_FOUND;
import static org.adaway.util.Constants.PREFS_NAME;

/**
 * This fragment is the preferences fragment for VPN ad blocker.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class PrefsVpnFragment extends PreferenceFragmentCompat {
    private ActivityResultLauncher<Intent> startActivityLauncher;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Configure preferences
        getPreferenceManager().setSharedPreferencesName(PREFS_NAME);
        addPreferencesFromResource(R.xml.preferences_vpn);
        // Register for activity
        registerForStartActivity();
        // Bind pref actions
        bindExcludedSystemApps();
        bindExcludedUserApps();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        PrefsActivity.setAppBarTitle(this, R.string.pref_vpn_title);
    }

    private void registerForStartActivity() {
        this.startActivityLauncher = registerForActivityResult(
                new StartActivityForResult(),
                result -> restartVpn()
        );
    }

    private void bindExcludedSystemApps() {
        ListPreference excludeUserAppsPreferences = findPreference(getString(R.string.pref_vpn_excluded_system_apps_key));
        assert excludeUserAppsPreferences != null : PREFERENCE_NOT_FOUND;
        excludeUserAppsPreferences.setOnPreferenceChangeListener((preference, newValue) -> {
            restartVpn();
            return true;
        });
    }

    private void bindExcludedUserApps() {
        Context context = requireContext();
        Preference excludeUserAppsPreferences = findPreference(getString(R.string.pref_vpn_excluded_user_apps_key));
        assert excludeUserAppsPreferences != null : PREFERENCE_NOT_FOUND;
        excludeUserAppsPreferences.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(context, PrefsVpnExcludedAppsActivity.class);
            this.startActivityLauncher.launch(intent);
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
