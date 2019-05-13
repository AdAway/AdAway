package org.adaway.ui.prefs;

import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.adaway.R;
import org.adaway.util.SentryLog;

import static org.adaway.util.Constants.PREFS_NAME;

/**
 * This fragment is the preferences main fragment.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class PrefsMainFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Configure preferences
        getPreferenceManager().setSharedPreferencesName(PREFS_NAME);
        addPreferencesFromResource(R.xml.preferences_main);
        // Bind pref actions
        bindThemePrefAction();
        bindTelemetryPrefAction();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        PrefsActivity.setAppBarTitle(this, R.string.pref_main_title);
    }

    private void bindThemePrefAction() {
        Context context = getContext();
        Preference darkThemePref = findPreference(getString(R.string.pref_dark_theme_key));
        darkThemePref.setOnPreferenceChangeListener((preference, newValue) -> {
            // Display user toast notification
            Toast.makeText(
                    context,
                    R.string.pref_dark_theme_notification,
                    Toast.LENGTH_LONG
            ).show();
            // Allow preference change
            return true;
        });
    }

    private void bindTelemetryPrefAction() {
        Preference enableTelemetryPref = findPreference(getString(R.string.pref_enable_telemetry_key));
        enableTelemetryPref.setOnPreferenceChangeListener((preference, newValue) -> {
            SentryLog.setEnabled(getContext(), (boolean) newValue);
            return true;
        });
        if (SentryLog.isStub()) {
            enableTelemetryPref.setEnabled(false);
            enableTelemetryPref.setSummary(R.string.pref_enable_telemetry_disabled_summary);
        }
    }
}
