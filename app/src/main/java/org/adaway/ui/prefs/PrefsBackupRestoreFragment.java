package org.adaway.ui.prefs;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.adaway.R;
import org.adaway.helper.ImportExportHelper;
import org.adaway.util.Log;

import static android.app.Activity.RESULT_OK;
import static android.content.Intent.ACTION_CREATE_DOCUMENT;
import static android.content.Intent.ACTION_OPEN_DOCUMENT;
import static android.content.Intent.CATEGORY_OPENABLE;
import static android.content.Intent.EXTRA_TITLE;
import static org.adaway.helper.ImportExportHelper.EXPORT_REQUEST_CODE;
import static org.adaway.helper.ImportExportHelper.IMPORT_REQUEST_CODE;
import static org.adaway.util.Constants.PREFS_NAME;

/**
 * This fragment is the preferences fragment for backup and restore block rules.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class PrefsBackupRestoreFragment extends PreferenceFragmentCompat {
    private static final String TAG = "BackupRestorePref";
    /**
     * The backup mime type.
     */
    private static final String JSON_MIME_TYPE = "application/json";
    /**
     * The default backup file name.
     */
    private static final String BACKUP_FILE_NAME = "adaway-backup.json";

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
        // Check result
        if (resultCode != RESULT_OK) {
            return;
        }
        // Check data
        if (data == null || data.getData() == null) {
            Log.w(TAG, "No result data.");
            return;
        }
        // Get selected file URI
        Uri backupUri = data.getData();
        Log.d(TAG, "Backup URI: " + backupUri.toString());
        // Check request code
        switch (requestCode) {
            case IMPORT_REQUEST_CODE:
                ImportExportHelper.importFromBackup(getContext(), backupUri);
                break;
            case EXPORT_REQUEST_CODE:
                ImportExportHelper.exportToBackup(getContext(), backupUri);
                break;
            default:
                Log.w(TAG, "Unsupported request code: " + requestCode + ".");
        }
    }

    private void bindBackupPref() {
        Preference backupPreference = findPreference(getString(R.string.pref_backup_key));
        backupPreference.setOnPreferenceClickListener(preference -> {
            exportToBackup();
            return true;
        });
    }

    private void bindRestorePref() {
        Preference backupPreference = findPreference(getString(R.string.pref_restore_key));
        backupPreference.setOnPreferenceClickListener(preference -> {
            importFromBackup();
            return true;
        });
    }

    /**
     * Import from a user backup.
     */
    private void importFromBackup() {
        Intent intent = new Intent(ACTION_OPEN_DOCUMENT);
        intent.addCategory(CATEGORY_OPENABLE);
        intent.setType(JSON_MIME_TYPE);
        startActivityForResult(intent, IMPORT_REQUEST_CODE);
    }

    /**
     * Export to a user backup.
     */
    private void exportToBackup() {
        Intent intent = new Intent(ACTION_CREATE_DOCUMENT);
        intent.addCategory(CATEGORY_OPENABLE);
        intent.setType(JSON_MIME_TYPE);
        intent.putExtra(EXTRA_TITLE, BACKUP_FILE_NAME);
        startActivityForResult(intent, EXPORT_REQUEST_CODE);
    }
}
