package org.adaway.model.backup;

import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.UiThread;

import com.annimon.stream.Stream;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.util.AppExecutors;
import org.adaway.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.concurrent.Executor;

import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;
import static org.adaway.model.backup.BackupFormat.ALLOWED_KEY;
import static org.adaway.model.backup.BackupFormat.BLOCKED_KEY;
import static org.adaway.model.backup.BackupFormat.REDIRECTED_KEY;
import static org.adaway.model.backup.BackupFormat.SOURCES_KEY;
import static org.adaway.model.backup.BackupFormat.hostToJson;
import static org.adaway.model.backup.BackupFormat.sourceToJson;
import static org.adaway.util.Constants.TAG;

/**
 * This class is a helper class to export user lists and hosts sources to a backup file.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class BackupExporter {
    private static final Executor DISK_IO_EXECUTOR = AppExecutors.getInstance().diskIO();
    private static final Executor MAIN_THREAD_EXECUTOR = AppExecutors.getInstance().mainThread();

    private static ProgressDialog dialog;

    private BackupExporter() {

    }

    /**
     * Export all user lists and hosts sources to a backup file on the external storage.
     *
     * @param context The application context.
     */
    public static void exportToBackup(Context context, Uri backupUri) {
        clearDialog();
        showExportDialog(context);
        DISK_IO_EXECUTOR.execute(() -> {
            boolean imported = true;
            try {
                exportBackup(context, backupUri);
            } catch (IOException e) {
                Log.e(TAG, "Failed to import backup", e);
                imported = false;
            }
            boolean successful = imported;
            MAIN_THREAD_EXECUTOR.execute(() -> {
                clearDialog();
                notifyExportEnd(context, successful);
            });
        });
    }

    @UiThread
    private static void showExportDialog(Context context) {
        BackupExporter.dialog = new ProgressDialog(context);
        BackupExporter.dialog.setMessage(context.getString(R.string.export_dialog));
        BackupExporter.dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        BackupExporter.dialog.setCancelable(false);
        BackupExporter.dialog.show();
    }

    @UiThread
    private static void clearDialog() {
        if (BackupExporter.dialog != null) {
            BackupExporter.dialog.cancel();
            BackupExporter.dialog = null;
        }
    }

    @UiThread
    private static void notifyExportEnd(Context context, boolean successful) {
        Toast.makeText(
                context,
                context.getString(successful ? R.string.export_success : R.string.export_failed),
                Toast.LENGTH_LONG
        ).show();
    }

    private static void exportBackup(Context context, Uri backupUri) throws IOException {
        // Open writer on the export file
        try (OutputStream outputStream = context.getContentResolver().openOutputStream(backupUri);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
            JSONObject backup = makeBackup(context);
            writer.write(backup.toString(4));
        } catch (JSONException e) {
            throw new IOException("Failed to generate backup.", e);
        } catch (IOException e) {
            throw new IOException("Could not write file.", e);
        }
    }

    private static JSONObject makeBackup(Context context) throws JSONException {
        AppDatabase database = AppDatabase.getInstance(context);
        HostsSourceDao hostsSourceDao = database.hostsSourceDao();
        HostListItemDao hostListItemDao = database.hostsListItemDao();

        List<HostListItem> userHosts = hostListItemDao.getUserList();
        List<HostListItem> blockedHosts = Stream.of(userHosts)
                .filter(value -> value.getType() == BLOCKED)
                .toList();
        List<HostListItem> allowedHosts = Stream.of(userHosts)
                .filter(value -> value.getType() == ALLOWED)
                .toList();
        List<HostListItem> redirectedHosts = Stream.of(userHosts)
                .filter(value -> value.getType() == REDIRECTED)
                .toList();

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
