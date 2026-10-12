package org.adaway.model.backup;

import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.UiThread;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.util.AppExecutors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;
import static org.adaway.model.backup.BackupFormat.ALLOWED_KEY;
import static org.adaway.model.backup.BackupFormat.BLOCKED_KEY;
import static org.adaway.model.backup.BackupFormat.REDIRECTED_KEY;
import static org.adaway.model.backup.BackupFormat.SOURCES_KEY;
import static org.adaway.model.backup.BackupFormat.hostToJson;
import static org.adaway.model.backup.BackupFormat.sourceToJson;

import timber.log.Timber;

/**
 * This class is a helper class to export user lists and hosts sources to a backup file.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class BackupExporter {
    private BackupExporter() {

    }

    /**
     * Export all user lists and hosts sources to a backup file on the external storage.
     *
     * @param context The application context.
     */
    public static void exportToBackup(Context context, Uri backupUri) {
        Context appContext = context.getApplicationContext();
        AppExecutors.getInstance().diskIO().execute(() -> {
            boolean imported = true;
            try {
                exportBackup(appContext, backupUri);
            } catch (IOException | RuntimeException e) {
                Timber.e(e, "Failed to export backup.");
                imported = false;
            }
            boolean successful = imported;
            String fileName = getFileNameFromUri(backupUri);
            AppExecutors.getInstance().mainThread().execute(() -> notifyExportEnd(appContext, successful, fileName));
        });
    }

    private static String getFileNameFromUri(Uri backupUri) {
        String path = backupUri.getPath();
        return path == null ? "" : new File(path).getName();
    }

    @UiThread
    private static void notifyExportEnd(Context context, boolean successful, String backupUri) {
        Toast.makeText(
                context,
                context.getString(successful ? R.string.export_success : R.string.export_failed, backupUri),
                Toast.LENGTH_LONG
        ).show();
    }

    static void exportBackup(Context context, Uri backupUri) throws IOException {
        // Open writer on the export file
        try {
            // Build first so a database/serialization failure cannot truncate an existing backup.
            String backup = makeBackup(context).toString(4);
            try (OutputStream outputStream = context.getContentResolver().openOutputStream(backupUri, "wt")) {
                if (outputStream == null) {
                    throw new IOException("Backup provider returned no output stream.");
                }
                try (BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
                    writer.write(backup);
                }
            }
        } catch (JSONException e) {
            throw new IOException("Failed to generate backup.", e);
        } catch (SecurityException e) {
            throw new IOException("Backup write permission denied.", e);
        } catch (IOException e) {
            throw new IOException("Could not write file.", e);
        }
    }

    private static JSONObject makeBackup(Context context) throws JSONException {
        AppDatabase database = AppDatabase.getInstance(context);
        HostsSourceDao hostsSourceDao = database.hostsSourceDao();
        HostListItemDao hostListItemDao = database.hostsListItemDao();

        List<HostListItem> userHosts = hostListItemDao.getUserList();
        List<HostListItem> blockedHosts = userHosts.stream()
                .filter(value -> value.getType() == BLOCKED)
                .collect(toList());
        List<HostListItem> allowedHosts = userHosts.stream()
                .filter(value -> value.getType() == ALLOWED)
                .collect(toList());
        List<HostListItem> redirectedHosts = userHosts.stream()
                .filter(value -> value.getType() == REDIRECTED)
                .collect(toList());

        JSONObject backupObject = new JSONObject();
        backupObject.put(SOURCES_KEY, buildSourcesBackup(hostsSourceDao.getAll()));
        backupObject.put(BLOCKED_KEY, buildListBackup(blockedHosts));
        backupObject.put(ALLOWED_KEY, buildListBackup(allowedHosts));
        backupObject.put(REDIRECTED_KEY, buildListBackup(redirectedHosts));

        return backupObject;
    }

    private static JSONArray buildSourcesBackup(List<HostsSource> sources) throws JSONException {
        JSONArray sourceArray = new JSONArray();
        for (HostsSource source : sources) {
            sourceArray.put(sourceToJson(source));
        }
        return sourceArray;
    }

    private static JSONArray buildListBackup(List<HostListItem> hosts) throws JSONException {
        JSONArray listArray = new JSONArray();
        for (HostListItem host : hosts) {
            listArray.put(hostToJson(host));
        }
        return listArray;
    }
}
