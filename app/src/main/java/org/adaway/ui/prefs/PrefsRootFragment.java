package org.adaway.ui.prefs;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
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

import static android.content.Intent.CATEGORY_OPENABLE;
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
    private ActivityResultLauncher<Intent> openHostsFileLauncher;
    private ActivityResultLauncher<String> prepareCertificateLauncher;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Configure preferences
        getPreferenceManager().setSharedPreferencesName(PREFS_NAME);
        addPreferencesFromResource(R.xml.preferences_root);
        // Register for activities
        registerForOpenHostActivity();
        registerForPrepareCertificateActivity();
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

    private void registerForOpenHostActivity() {
        this.openHostsFileLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            SuFile hostFile = new SuFile(ANDROID_SYSTEM_ETC_HOSTS).getCanonicalFile();
            remountPartition(hostFile, READ_ONLY);
        });
    }

    private void registerForPrepareCertificateActivity() {
        this.prepareCertificateLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument() {
                    @NonNull
                    @Override
                    public Intent createIntent(@NonNull Context context, @NonNull String input) {
                        return super.createIntent(context, input)
                                .addCategory(CATEGORY_OPENABLE)
                                .setType("application/x-x509-ca-cert");
                    }
                }, this::prepareWebServerCertificate
        );
    }

    private void bindOpenHostsFile() {
        Preference openHostsFilePreference = findPreference(getString(R.string.pref_open_hosts_key));
        openHostsFilePreference.setOnPreferenceClickListener(this::openHostsFile);
    }

    private boolean openHostsFile(Preference preference) {
        SuFile hostFile = new SuFile(ANDROID_SYSTEM_ETC_HOSTS).getCanonicalFile();
        boolean remount = !hostFile.canWrite() && remountPartition(hostFile, READ_WRITE);
        try {
            Intent intent = new Intent()
                    .setAction(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.parse("file://" + hostFile.getAbsolutePath()), "text/plain");
            if (remount) {
                this.openHostsFileLauncher.launch(intent);
            } else {
                startActivity(intent);
            }
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
                this.prepareCertificateLauncher.launch("adaway-webserver-certificate.crt");
            }
            return true;
        });
    }

    private void prepareWebServerCertificate(Uri uri) {
        // Check user selected document
        if (uri == null) {
            return;
        }
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
