package org.adaway.ui.prefs;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.adaway.R;
import org.adaway.helper.ImportExportHelper;
import org.adaway.ui.dialog.ActivityNotFoundDialogFragment;
import org.adaway.util.Constants;
import org.adaway.util.Log;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.app.Activity.RESULT_OK;
import static android.content.Intent.ACTION_GET_CONTENT;
import static android.content.Intent.CATEGORY_OPENABLE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static org.adaway.helper.ImportExportHelper.IMPORT_REQUEST_CODE;
import static org.adaway.helper.ImportExportHelper.WRITE_STORAGE_PERMISSION_REQUEST_CODE;
import static org.adaway.util.Constants.PREFS_NAME;

/**
 * This fragment is the preferences fragment for backup and restore block rules.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class PrefsBackupRestoreFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Configure preferences
        getPreferenceManager().setSharedPreferencesName(PREFS_NAME);
        addPreferencesFromResource(R.xml.preferences_backup_restore);
        // Bind pref actions
        bindBackupPref();
        bindRestorePref();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        PrefsActivity.setAppBarTitle(this, R.string.pref_backup_restore_title);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Check request code
        if (requestCode != IMPORT_REQUEST_CODE) {
            return;
        }
        // Check result
        if (resultCode != RESULT_OK) {
            return;
        }
        // Check data
        if (data != null && data.getData() != null) {
            // Get selected file URI
            Uri backupUri = data.getData();
            Log.d(Constants.TAG, "Backup URI: " + backupUri.toString());
            // Import from backup
            ImportExportHelper.importFromBackup(getContext(), backupUri);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Check permission request code
        if (requestCode != WRITE_STORAGE_PERMISSION_REQUEST_CODE) {
            return;
        }
        // Check results
        if (grantResults.length == 0 || grantResults[0] != PERMISSION_GRANTED) {
            return;
        }
        // Restart action according granted permission
        switch (permissions[0]) {
            case READ_EXTERNAL_STORAGE:
                importFromBackup();
                break;
            case WRITE_EXTERNAL_STORAGE:
                exportToBackup();
                break;
        }
    }

    private void bindBackupPref() {
        Preference backupPreference = findPreference(getString(R.string.pref_backup_key));
        backupPreference.setOnPreferenceClickListener(preference -> {
            // Check write storage permission
            if (checkPermission(WRITE_EXTERNAL_STORAGE)) {
                exportToBackup();
            }
            return true;
        });
    }

    private void bindRestorePref() {
        Preference backupPreference = findPreference(getString(R.string.pref_restore_key));
        backupPreference.setOnPreferenceClickListener(preference -> {
            // Check read storage permission
            if (checkPermission(READ_EXTERNAL_STORAGE)) {
                importFromBackup();
            }
            return true;
        });
    }

    /**
     * Ensure a permission is granted.<br>
     * If the permission is not granted, a request is shown to user.
     *
     * @param permission The permission to check
     * @return <code>true</code> if the permission is granted, <code>false</code> otherwise.
     */
    private boolean checkPermission(String permission) {
        // Get application context
        Context context = this.getContext();
        if (context == null) {
            // Return permission failed as no context to check
            return false;
        }
        int permissionCheck = ContextCompat.checkSelfPermission(context, permission);
        if (permissionCheck != PERMISSION_GRANTED) {
            // Request write external storage permission
            this.requestPermissions(
                    new String[]{permission},
                    WRITE_STORAGE_PERMISSION_REQUEST_CODE
            );
            // Return permission not granted yes
            return false;
        }
        // Return permission granted
        return true;
    }

    /**
     * Import from a user backup.
     */
    private void importFromBackup() {
        Intent intent = new Intent(ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(CATEGORY_OPENABLE);
        // Start file picker activity
        try {
            startActivityForResult(intent, IMPORT_REQUEST_CODE);
        } catch (ActivityNotFoundException exception) {
            // Show dialog to install file picker
            FragmentManager fragmentManager = getFragmentManager();
            if (fragmentManager != null) {
                ActivityNotFoundDialogFragment.newInstance(
                        R.string.no_file_manager_title,
                        R.string.no_file_manager,
                        "market://details?id=org.openintents.filemanager",
                        "OI File Manager"
                ).show(fragmentManager, "notFoundDialog");
            }
        }
    }

    /**
     * Exports to a user backup.
     */
    private void exportToBackup() {
        Context context = getContext();
        ImportExportHelper.exportToBackup(context);
    }
}
