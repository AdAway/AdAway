package org.adaway.ui.prefs;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.util.WebServerUtils;

import static org.adaway.model.hostsinstall.HostsInstallLocation.CUSTOM_TARGET;
import static org.adaway.util.Constants.PREFS_NAME;

/**
 * This fragment is the preferences fragment for root ad blocker.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class PrefsRootFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Configure preferences
        getPreferenceManager().setSharedPreferencesName(PREFS_NAME);
        addPreferencesFromResource(R.xml.preferences_root);
        // Bind pref actions
        bindWebServerPrefAction();
        bindCustomTargetPrefAction();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        PrefsActivity.setAppBarTitle(this, R.string.pref_root_title);
    }

    private void bindWebServerPrefAction() {
        Context context = getContext();
        // Start web server when preference is enabled
        Preference WebServerEnabledPref = findPreference(getString(R.string.pref_webserver_enabled_key));
        WebServerEnabledPref.setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue.equals(true)) {
                // Start web server
                WebServerUtils.startWebServer(context);
            } else {
                // Stop web server
                WebServerUtils.stopWebServer();
            }
            return true;
        });
    }

    private void bindCustomTargetPrefAction() {
        Context context = getContext();
        // find custom target edit
        EditTextPreference mCustomTarget = findPreference(getString(R.string.pref_custom_target_key));
        // enable custom target pref on create if enabled in apply method
        if (PreferenceHelper.getInstallLocation(context).equals(CUSTOM_TARGET)) {
            mCustomTarget.setEnabled(true);
        } else {
            mCustomTarget.setEnabled(false);
        }
        // Enable custom target pref if enabled in apply method
        Preference customTargetPref = findPreference(getString(R.string.pref_apply_method_key));
        customTargetPref.setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue.equals("customTarget")) {
                mCustomTarget.setEnabled(true);
            } else {
                mCustomTarget.setEnabled(false);
            }
            return true;
        });
    }
}
