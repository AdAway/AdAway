package org.adaway.ui.prefs;

import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.service.hosts.UpdateService;
import org.adaway.util.Constants;
import org.adaway.util.SentryLog;
import org.adaway.util.Utils;
import org.adaway.util.WebServerUtils;

/**
 * This fragment is the preferences fragment.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class PrefsFragment extends PreferenceFragmentCompat {
    /**
     * The custom target preferences (<code>null</code> if fragment is not created).
     */
    private EditTextPreference mCustomTarget;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Configure preferences
        this.getPreferenceManager().setSharedPreferencesName(Constants.PREFS_NAME);
        this.addPreferencesFromResource(R.xml.preferences);
        // Get current context
        Context context = this.getActivity();

        /*
         * Display notification on theme change to tell user to restart application.
         */
        Preference darkThemePref = this.findPreference(getString(R.string.pref_dark_theme_key));
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

        /*
         * Listen on click of update daily pref, register UpdateService if enabled,
         * setOnPreferenceChangeListener is not used because it is executed before setting the
         * preference value, this would lead to a false check in UpdateListener
         */
        Preference updateDailyPref = this.findPreference(getString(R.string.pref_update_check_daily_key));
        Preference updateOnlyOnWifiPref = this.findPreference(this.getString(R.string.pref_update_only_on_wifi_key));
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

        // Start web server when preference is enabled
        Preference WebServerEnabledPref = this.findPreference(getString(R.string.pref_webserver_enabled_key));
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

        // find custom target edit
        mCustomTarget = (EditTextPreference) getPreferenceScreen().findPreference(
                getString(R.string.pref_custom_target_key));

        // enable custom target pref on create if enabled in apply method
        if (PreferenceHelper.getApplyMethod(context).equals("customTarget")) {
            mCustomTarget.setEnabled(true);
        } else {
            mCustomTarget.setEnabled(false);
        }

        /* enable custom target pref if enabled in apply method */
        Preference customTargetPref = findPreference(getString(R.string.pref_apply_method_key));
        customTargetPref.setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue.equals("customTarget")) {
                mCustomTarget.setEnabled(true);
            } else {
                mCustomTarget.setEnabled(false);
            }
            return true;
        });

        Preference enableTelemetryPref = findPreference(getString(R.string.pref_enable_telemetry_key));
        enableTelemetryPref.setEnabled(!SentryLog.isStub());
        enableTelemetryPref.setOnPreferenceChangeListener((preference, newValue) -> {
            SentryLog.setEnabled(this.getContext(), (boolean) newValue);
            return true;
        });

        /*
         * Disable update check daily and web server on boot when installed on sd card. See
         * http://developer.android.com/guide/appendix/install-location.html why
         */
        CheckBoxPreference updateCheckDaily = (CheckBoxPreference) getPreferenceScreen().findPreference(
                getString(R.string.pref_update_check_daily_key));
        CheckBoxPreference webServerOnBoot = (CheckBoxPreference) getPreferenceScreen().findPreference(
                getString(R.string.pref_webserver_on_boot_key));

        if (Utils.isInstalledOnSdCard(context)) {
            updateCheckDaily.setEnabled(false);
            webServerOnBoot.setEnabled(false);
            updateCheckDaily.setSummary(R.string.pref_sdcard_problem);
            webServerOnBoot.setSummary(R.string.pref_sdcard_problem);
        } else {
            updateCheckDaily.setEnabled(true);
            webServerOnBoot.setEnabled(true);
            updateCheckDaily.setSummary(R.string.pref_update_check_daily_summary);
            webServerOnBoot.setSummary(R.string.pref_webserver_on_boot_summary);
        }
    }
}
