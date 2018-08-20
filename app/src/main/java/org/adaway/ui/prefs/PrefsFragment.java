package org.adaway.ui.prefs;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.widget.Toast;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.service.hosts.UpdateService;
import org.adaway.util.Constants;
import org.adaway.util.SystemlessUtils;
import org.adaway.util.Utils;
import org.adaway.util.WebServerUtils;
import org.adaway.util.systemless.AbstractSystemlessMode;

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
    /**
     * The systemless preferences (<code>null</code> if fragment is not created).
     */
    private CheckBoxPreference mSystemless;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        // Configure preferences
//        this.getPreferenceManager().setSharedPreferencesName(Constants.PREFS_NAME);
//        this.addPreferencesFromResource(R.xml.preferences);
//        // Get current context
//        final Context context = this.getActivity();

//
//        /*
//         * Listen on click of update daily pref, register UpdateService if enabled,
//         * setOnPreferenceChangeListener is not used because it is executed before setting the
//         * preference value, this would lead to a false check in UpdateListener
//         */
//        Preference UpdateDailyPref = findPreference(getString(R.string.pref_update_check_daily_key));
//        UpdateDailyPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
//
//            @Override
//            public boolean onPreferenceClick(Preference preference) {
//                if (PreferenceHelper.getUpdateCheckDaily(context)) {
//                    UpdateService.enable();
//                } else {
//                    UpdateService.disable();
//                }
//                return false;
//            }
//
//        });
//
//        /* Start webserver if pref is enabled */
//        Preference WebserverEnabledPref = findPreference(getString(R.string.pref_webserver_enabled_key));
//        WebserverEnabledPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
//            @Override
//            public boolean onPreferenceChange(Preference preference, Object newValue) {
//                Shell rootShell;
//                try {
//                    rootShell = Shell.startRootShell();
//
//                    if (newValue.equals(true)) {
//                        // start webserver
//                        WebServerUtils.startWebServer(context, rootShell);
//                    } else {
//                        // stop webserver
//                        WebServerUtils.stopWebServer(context, rootShell);
//                    }
//
//                    rootShell.close();
//                } catch (Exception e) {
//                    Log.e(Constants.TAG, "Problem while starting/stopping webserver!", e);
//                }
//
//                return true;
//            }
//        });
//
//        // Find systemless mode preferences
//        mSystemless = (CheckBoxPreference) getPreferenceScreen().findPreference(getString(R.string.pref_enable_systemless_key));
//
//        // find custom target edit
//        mCustomTarget = (EditTextPreference) getPreferenceScreen().findPreference(
//                getString(R.string.pref_custom_target_key));
//
//        // enable custom target pref on create if enabled in apply method
//        if (PreferenceHelper.getApplyMethod(context).equals("customTarget")) {
//            mCustomTarget.setEnabled(true);
//        } else {
//            mCustomTarget.setEnabled(false);
//        }
//
//        /* enable custom target pref if enabled in apply method */
//        Preference customTargetPref = findPreference(getString(R.string.pref_apply_method_key));
//        customTargetPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
//            @Override
//            public boolean onPreferenceChange(Preference preference, Object newValue) {
//                if (newValue.equals("customTarget")) {
//                    mCustomTarget.setEnabled(true);
//                } else {
//                    mCustomTarget.setEnabled(false);
//                }
//                return true;
//            }
//        });
//
//        /*
//         * Disable update check daily and webserver on boot when installed on sd card. See
//         * http://developer.android.com/guide/appendix/install-location.html why
//         */
//        CheckBoxPreference updateCheckDaily = (CheckBoxPreference) getPreferenceScreen().findPreference(
//                getString(R.string.pref_update_check_daily_key));
//        CheckBoxPreference webserverOnBoot = (CheckBoxPreference) getPreferenceScreen().findPreference(
//                getString(R.string.pref_webserver_on_boot_key));
//
//        if (Utils.isInstalledOnSdCard(context)) {
//            updateCheckDaily.setEnabled(false);
//            webserverOnBoot.setEnabled(false);
//            updateCheckDaily.setSummary(R.string.pref_sdcard_problem);
//            webserverOnBoot.setSummary(R.string.pref_sdcard_problem);
//        } else {
//            updateCheckDaily.setEnabled(true);
//            webserverOnBoot.setEnabled(true);
//            updateCheckDaily.setSummary(R.string.pref_update_check_daily_summary);
//            webserverOnBoot.setSummary(R.string.pref_webserver_on_boot_summary);
//        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Configure preferences
        this.getPreferenceManager().setSharedPreferencesName(Constants.PREFS_NAME);
        this.addPreferencesFromResource(R.xml.preferences);
        // Get current context
        final Context context = this.getActivity();

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
         * Enable systemless mode systemless mode if supported.
         */
        Preference SystemlessPref = this.findPreference(getString(R.string.pref_enable_systemless_key));
        SystemlessPref.setOnPreferenceChangeListener((preference, newValue) -> {
            // Get device systemless mode
            AbstractSystemlessMode systemlessMode = SystemlessUtils.getSystemlessMode();
            // Check if systemless is supported
            if (!systemlessMode.isSupported()) {
                return false;
            }
            // Declare successful action status
            boolean successful;
            // Check action to apply
            if (newValue.equals(true)) {
                // Enable systemless mode
                successful = systemlessMode.enable(context);
                // Check if reboot is needed
                if (successful && systemlessMode.isRebootNeededAfterActivation()) {
                    Utils.rebootQuestion(context, R.string.enable_systemless_successful_title,
                            R.string.enable_systemless_successful);
                }
            } else {
                // Disable systemless mode
                successful = systemlessMode.disable(context);
                // Check if reboot is needed
                if (successful && systemlessMode.isRebootNeededAfterDeactivation()) {
                    Utils.rebootQuestion(context, R.string.disable_systemless_successful_title,
                            R.string.disable_systemless_successful);
                }
            }
            // Return successful action status
            return successful;
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

        // Find systemless mode preferences
        mSystemless = (CheckBoxPreference) getPreferenceScreen().findPreference(getString(R.string.pref_enable_systemless_key));

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

    @Override
    public void onResume() {
        super.onResume();
        // Check preference if systemless mode is enabled
        new SystemlessCheckTask().execute();
    }

    /**
     * This class is an async task to check systemless mode status.
     *
     * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
     */
    private class SystemlessCheckTask extends AsyncTask<Void, Void, AbstractSystemlessMode> {
        @Override
        protected AbstractSystemlessMode doInBackground(Void... params) {
            // Retrieve the systemless mode
            return SystemlessUtils.getSystemlessMode();
        }

        @Override
        protected void onPostExecute(AbstractSystemlessMode systemlessMode) {
            // Ensure reference exists
            if (mSystemless == null) {
                return;
            }
            // Enable setting and set initial value
            mSystemless.setEnabled(systemlessMode.isSupported());
            mSystemless.setChecked(systemlessMode.isEnabled(PrefsFragment.this.getActivity()));
        }
    }
}
