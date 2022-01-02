package org.adaway.model.backup;

import static org.adaway.util.Constants.PREFS_NAME;

import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupDataInputStream;
import android.app.backup.BackupDataOutput;
import android.app.backup.FileBackupHelper;
import android.app.backup.SharedPreferencesBackupHelper;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.IOException;

import timber.log.Timber;

/**
 * This class is a {@link android.app.backup.BackupAgent} to backup and restore application state
 * using Android Backup Service. It is based on key-value pairs backup to prevent killing the
 * application during the backup (leaving the VPN foreground service always running). It backs up
 * and restores the application preferences and the user rules.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class AppBackupAgent extends BackupAgentHelper {
    private static final String PREFS_BACKUP_KEY = "prefs";
    private static final String RULES_BACKUP_KEY = "rules";

    @Override
    public void onCreate() {
        super.onCreate();
        addHelper(PREFS_BACKUP_KEY, new SharedPreferencesBackupHelper(this, PREFS_NAME));
        addHelper(RULES_BACKUP_KEY, new SourceBackupHelper(this));
    }

    /**
     * This class is a {@link android.app.backup.BackupHelper} to backup and restore user rules.
     *
     * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
     */
    private static class SourceBackupHelper extends FileBackupHelper {
        private static final String RULES_FILE_NAME = "rules-backup.json";
        private final Context context;

        /**
         * Constructor.
         *
         * @param context The application context.
         */
        public SourceBackupHelper(Context context) {
            super(context, RULES_FILE_NAME);
            this.context = context;
        }

        @Override
        public void performBackup(ParcelFileDescriptor oldState, BackupDataOutput data, ParcelFileDescriptor newState) {
            try {
                BackupExporter.exportBackup(this.context, getRulesFileUri());
                super.performBackup(oldState, data, newState);
            } catch (IOException e) {
                Timber.w(e, "Failed to export rules to backup.");
            }
        }

        @Override
        public void restoreEntity(BackupDataInputStream data) {
            super.restoreEntity(data);
            try {
                BackupImporter.importBackup(this.context, getRulesFileUri());
            } catch (IOException e) {
                Timber.w(e, "Failed to import rules from backup.");
            }
        }

        private Uri getRulesFileUri() {
            File ruleFile = new File(this.context.getFilesDir(), RULES_FILE_NAME);
            return Uri.fromFile(ruleFile);
        }
    }
}
