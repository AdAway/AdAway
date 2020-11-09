package org.adaway.ui.prefs;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.topjohnwu.superuser.io.SuFile;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.ui.dialog.MissingAppDialog;
import org.adaway.util.AppExecutors;
import org.adaway.util.Log;

import static android.content.Intent.ACTION_CREATE_DOCUMENT;
import static android.content.Intent.CATEGORY_OPENABLE;
import static android.content.Intent.EXTRA_TITLE;
import static android.os.Build.VERSION.SDK_INT;
import static android.provider.Settings.ACTION_SECURITY_SETTINGS;
import static org.adaway.util.Constants.ANDROID_SYSTEM_ETC_HOSTS;
import static org.adaway.util.Constants.PREFS_NAME;
import static org.adaway.util.MountType.READ_ONLY;
import static org.adaway.util.MountType.READ_WRITE;
import static org.adaway.util.ShellUtils.remountPartition;
import static org.adaway.util.WebServerUtils.TEST_URL;
import static org.adaway.util.WebServerUtils.copyCertificate;
import static org.adaway.util.WebServerUtils.getWebServerState;
import static org.adaway.util.WebServerUtils.installCertificate;
import static org.adaway.util.WebServerUtils.isWebServerRunning;
import static org.adaway.util.WebServerUtils.startWebServer;
import static org.adaway.util.WebServerUtils.stopWebServer;

/**
 * This fragment is the preferences fragment for root ad blocker.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class PrefsRootFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "PrefsRoot";
    /**
     * The request code to identify the hosts file edition without remount action.
     */
    private static final int EDIT_HOSTS_REQUEST_CODE = 20;
    /**
     * The request code to identify the hosts file edition with remount action.
     */
    private static final int EDIT_HOSTS_AND_REMOUNT_REQUEST_CODE = 21;
    /**
     * The request code to identify the export the web server certificate action.
     */
    private static final int EXPORT_WEB_SERVER_CERTIFICATE = 30;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Configure preferences
        getPreferenceManager().setSharedPreferencesName(PREFS_NAME);
        addPreferencesFromResource(R.xml.preferences_root);
        // Bind pref actions
        bindOpenHostsFile();
        bindRedirection();
        bindWebServerPrefAction();
        bindWebServerTest();
        bindWebServerCertificate();
        // Update current state
        updateWebServerState();
        // Register as listener
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        PrefsActivity.setAppBarTitle(this, R.string.pref_root_title);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Update current state
        updateWebServerState();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        switch (requestCode) {
            case EDIT_HOSTS_AND_REMOUNT_REQUEST_CODE:
                SuFile hostFile = new SuFile(ANDROID_SYSTEM_ETC_HOSTS).getCanonicalFile();
                remountPartition(hostFile, READ_ONLY);
            case EXPORT_WEB_SERVER_CERTIFICATE:
                if (data == null || data.getData() == null) {
                    Log.w(TAG, "No result data.");
                    return;
                }
                prepareWebServerCertificate(data.getData());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Unregister as listener
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Context context = requireContext();
        // Restart web server on icon change
        if (context.getString(R.string.pref_webserver_icon_key).equals(key) && isWebServerRunning()) {
            stopWebServer();
            startWebServer(context);
            updateWebServerState();
        }
    }

    private void bindOpenHostsFile() {
        Preference openHostsFilePreference = findPreference(getString(R.string.pref_open_hosts_key));
        openHostsFilePreference.setOnPreferenceClickListener(this::openHostsFile);
    }

    private boolean openHostsFile(@SuppressWarnings("unused") Preference preference) {
        SuFile hostFile = new SuFile(ANDROID_SYSTEM_ETC_HOSTS).getCanonicalFile();
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

    private void bindRedirection() {
        Context context = requireContext();
        boolean ipv6Enabled = PreferenceHelper.getEnableIpv6(context);
        Preference ipv6RedirectionPreference = findPreference(getString(R.string.pref_redirection_ipv6_key));
        ipv6RedirectionPreference.setEnabled(ipv6Enabled);
    }

    private void bindWebServerPrefAction() {
        Context context = requireContext();
        // Start web server when preference is enabled
        CheckBoxPreference webServerEnabledPref = findPreference(getString(R.string.pref_webserver_enabled_key));
        webServerEnabledPref.setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue.equals(true)) {
                // Start web server
                startWebServer(context);
                updateWebServerState();
                return isWebServerRunning();
            } else {
                // Stop web server
                stopWebServer();
                updateWebServerState();
                return !isWebServerRunning();
            }
        });
    }

    private void bindWebServerTest() {
        Preference webServerTest = findPreference(getString(R.string.pref_webserver_test_key));
        webServerTest.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(TEST_URL));
            startActivity(intent);
            return true;
        });
    }

    private void bindWebServerCertificate() {
        Preference webServerTest = findPreference(getString(R.string.pref_webserver_certificate_key));
        webServerTest.setOnPreferenceClickListener(preference -> {
            if (SDK_INT < VERSION_CODES.R) {
                installCertificate(requireContext());
            } else {
                Intent intent = new Intent(ACTION_CREATE_DOCUMENT);
                intent.addCategory(CATEGORY_OPENABLE);
                intent.setType("application/x-x509-ca-cert");
                intent.putExtra(EXTRA_TITLE, "adaway-webserver-certificate.crt");
                startActivityForResult(intent, EXPORT_WEB_SERVER_CERTIFICATE);
            }
            return true;
        });
    }

    private void prepareWebServerCertificate(Uri uri) {
        Log.d(TAG, "Certificate URI: " + uri.toString());
        copyCertificate(requireActivity(), uri);
        new MaterialAlertDialogBuilder(requireContext())
                .setCancelable(true)
                .setTitle(R.string.pref_webserver_certificate_dialog_title)
                .setMessage(R.string.pref_webserver_certificate_dialog_content)
                .setPositiveButton(
                        R.string.pref_webserver_certificate_dialog_action,
                        (dialog, which) -> {
                            dialog.dismiss();
                            Intent intent = new Intent(ACTION_SECURITY_SETTINGS);
                            startActivity(intent);
                        })
                .create()
                .show();
    }

    private void updateWebServerState() {
        Preference webServerTest = findPreference(getString(R.string.pref_webserver_test_key));
        webServerTest.setSummary(R.string.pref_webserver_state_checking);
        AppExecutors executors = AppExecutors.getInstance();
        executors.networkIO().execute(() -> {
                    // Wait for server to start
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    int summaryResId = getWebServerState();
                    executors.mainThread().execute(
                            () -> webServerTest.setSummary(summaryResId)
                    );
                }
        );
    }
}
