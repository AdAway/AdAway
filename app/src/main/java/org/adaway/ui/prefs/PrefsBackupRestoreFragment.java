package org.adaway.ui.prefs;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument;
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.adaway.R;
import org.adaway.model.backup.BackupExporter;
import org.adaway.model.backup.BackupImporter;

import static android.content.Intent.CATEGORY_OPENABLE;
import static org.adaway.util.Constants.PREFS_NAME;

/**
 * This fragment is the preferences fragment for backup and restore block rules.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class PrefsBackupRestoreFragment extends PreferenceFragmentCompat {
    /**
     * The backup mime type.
     */
    private static final String JSON_MIME_TYPE = "application/json";
    /**
     * The default backup file name.
     */
    private static final String BACKUP_FILE_NAME = "adaway-backup.json";
    /**
     * The launcher to start import backup activity.
     */
    private ActivityResultLauncher<String[]> importActivityLauncher;
    /**
     * The launcher to start export backup activity.
     */
    private ActivityResultLauncher<String> exportActivityLauncher;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Configure preferences
        getPreferenceManager().setSharedPreferencesName(PREFS_NAME);
        addPreferencesFromResource(R.xml.preferences_backup_restore);
        // Register for activities
        registerForImportActivity();
        registerForExportActivity();
        // Bind pref actions
        bindBackupPref();
        bindRestorePref();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        PrefsActivity.setAppBarTitle(this, R.string.pref_backup_restore_title);
    }

    private void registerForImportActivity() {
        this.importActivityLauncher = registerForActivityResult(new OpenDocument() {
            @NonNull
            @Override
            public Intent createIntent(@NonNull Context context, @NonNull String[] input) {
                return super.createIntent(context, input)
                        .addCategory(CATEGORY_OPENABLE);
            }
        }, backupUri -> {
            if (backupUri != null) {
                BackupImporter.importFromBackup(requireContext(), backupUri);
            }
        });
    }

    private void registerForExportActivity() {
        this.exportActivityLauncher = registerForActivityResult(new CreateDocument() {
            @NonNull
            @Override
            public Intent createIntent(@NonNull Context context, @NonNull String input) {
                return super.createIntent(context, input)
                        .addCategory(CATEGORY_OPENABLE)
                        .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{JSON_MIME_TYPE});
            }
        }, backupUri -> {
            if (backupUri != null) {
                BackupExporter.exportToBackup(requireContext(), backupUri);
            }
        });
    }

    private void bindBackupPref() {
        Preference backupPreference = findPreference(getString(R.string.pref_backup_key));
        assert backupPreference != null : "preference not found";
        backupPreference.setOnPreferenceClickListener(preference -> {
            this.exportActivityLauncher.launch(BACKUP_FILE_NAME);
            return true;
        });
    }

    private void bindRestorePref() {
        Preference backupPreference = findPreference(getString(R.string.pref_restore_key));
        assert backupPreference != null : "preference not found";
        backupPreference.setOnPreferenceClickListener(preference -> {
            String[] mimeTypes;
            if (Build.VERSION.SDK_INT < 28) {
                mimeTypes = new String[]{"*/*"};
            } else if (Build.VERSION.SDK_INT < 29) {
                mimeTypes = new String[]{JSON_MIME_TYPE, "application/octet-stream"};
            } else {
                mimeTypes = new String[] {JSON_MIME_TYPE};
            }
            this.importActivityLauncher.launch(mimeTypes);
            return true;
        });
    }
}
