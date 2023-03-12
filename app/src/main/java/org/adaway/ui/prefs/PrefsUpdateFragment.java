package org.adaway.ui.prefs;

import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.TIRAMISU;
import static android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS;
import static android.provider.Settings.EXTRA_APP_PACKAGE;

import android.content.Context;
import android.content.Intent;
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
        bindNotificationPreferencesAction();
        bindAppUpdatePrefAction();
        bindAppChannelPrefAction();
        bindHostsUpdatePrefAction();
        // Update current state
        updateNotificationPreferencesState();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        PrefsActivity.setAppBarTitle(this, R.string.pref_update_title);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateNotificationPreferencesState();
    }

    private void bindNotificationPreferencesAction() {
        Context context = requireContext();
        Preference openNotificationPref = findPreference(getString(R.string.pref_update_open_notification_preferences_key));
        assert openNotificationPref != null : PREFERENCE_NOT_FOUND;
        openNotificationPref.setOnPreferenceClickListener(preference -> {
            Intent settingsIntent = new Intent(ACTION_APP_NOTIFICATION_SETTINGS)
                    .addFlags(FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_APP_PACKAGE, context.getPackageName());
            context.startActivity(settingsIntent);
            return true;
        });
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

    private void updateNotificationPreferencesState() {
        Context context = requireContext();
        Preference openNotificationPref = findPreference(getString(R.string.pref_update_open_notification_preferences_key));
        assert openNotificationPref != null : PREFERENCE_NOT_FOUND;
        boolean notificationsDisabled = SDK_INT >= TIRAMISU && context.checkSelfPermission(POST_NOTIFICATIONS) != PERMISSION_GRANTED;
        openNotificationPref.setVisible(notificationsDisabled);
    }
}
