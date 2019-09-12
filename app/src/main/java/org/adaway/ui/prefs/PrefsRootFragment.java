package org.adaway.ui.prefs;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.topjohnwu.superuser.io.SuFile;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.ui.dialog.MissingAppDialog;
import org.adaway.util.Constants;
import org.adaway.util.WebServerUtils;

import static org.adaway.model.hostsinstall.HostsInstallLocation.CUSTOM_TARGET;
import static org.adaway.util.Constants.PREFS_NAME;
import static org.adaway.util.MountType.READ_ONLY;
import static org.adaway.util.MountType.READ_WRITE;
import static org.adaway.util.ShellUtils.remountPartition;

/**
 * This fragment is the preferences fragment for root ad blocker.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class PrefsRootFragment extends PreferenceFragmentCompat {
    /**
     * The request code to identify the hosts file edition without remount action.
     */
    private static final int EDIT_HOSTS_REQUEST_CODE = 20;
    /**
     * The request code to identify the hosts file edition with remount action.
     */
    private static final int EDIT_HOSTS_AND_REMOUNT_REQUEST_CODE = 21;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Configure preferences
        getPreferenceManager().setSharedPreferencesName(PREFS_NAME);
        addPreferencesFromResource(R.xml.preferences_root);
        // Bind pref actions
        bindOpenHostsFile();
        bindCustomTargetPrefAction();
        bindRedirection();
        bindWebServerPrefAction();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        PrefsActivity.setAppBarTitle(this, R.string.pref_root_title);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == EDIT_HOSTS_AND_REMOUNT_REQUEST_CODE) {
            SuFile hostFile = new SuFile(Constants.ANDROID_SYSTEM_ETC_HOSTS).getCanonicalFile();
            remountPartition(hostFile, READ_ONLY);
        }
    }

    private void bindOpenHostsFile() {
        Preference openHostsFilePreference = findPreference(getString(R.string.pref_open_hosts_key));
        openHostsFilePreference.setOnPreferenceClickListener(this::openHostsFile);
    }

    private boolean openHostsFile(@SuppressWarnings("unused") Preference preference) {
        SuFile hostFile = new SuFile(Constants.ANDROID_SYSTEM_ETC_HOSTS).getCanonicalFile();
        boolean remount = !hostFile.canWrite() && remountPartition(hostFile, READ_WRITE);
        try {
            Intent intent = new Intent()
                    .setAction(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.parse("file://" + hostFile.getAbsolutePath()), "text/plain");
            startActivityForResult(intent, remount ? EDIT_HOSTS_AND_REMOUNT_REQUEST_CODE : EDIT_HOSTS_REQUEST_CODE);
            return true;
        } catch (ActivityNotFoundException exception) {
            MissingAppDialog.showTextEditorMissingDialog(getContext());
            return false;
        }
    }

    private void bindCustomTargetPrefAction() {
        Context context = getContext();
        // find custom target edit
        EditTextPreference customTargetPreference = findPreference(getString(R.string.pref_custom_target_key));
        // enable custom target pref on create if enabled in apply method
        if (PreferenceHelper.getInstallLocation(context).equals(CUSTOM_TARGET)) {
            customTargetPreference.setEnabled(true);
        } else {
            customTargetPreference.setEnabled(false);
        }
        // Enable custom target pref if enabled in apply method
        Preference customTargetPref = findPreference(getString(R.string.pref_apply_method_key));
        customTargetPref.setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue.equals("customTarget")) {
                customTargetPreference.setEnabled(true);
            } else {
                customTargetPreference.setEnabled(false);
            }
            return true;
        });
    }

    private void bindRedirection() {
        Context context = getContext();
        boolean ipv6Enabled = PreferenceHelper.getEnableIpv6(context);
        Preference ipv6RedirectionPreference = findPreference(getString(R.string.pref_redirection_ipv6_key));
        ipv6RedirectionPreference.setEnabled(ipv6Enabled);
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
}
