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
import org.adaway.db.entity.ListType;
import org.adaway.db.entity.RuleKind;
import org.adaway.util.AppExecutors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;

import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;
import static org.adaway.model.backup.BackupFormat.ALLOWED_KEY;
import static org.adaway.model.backup.BackupFormat.BLOCKED_KEY;
import static org.adaway.model.backup.BackupFormat.REDIRECTED_KEY;
import static org.adaway.model.backup.BackupFormat.SOURCES_KEY;
import static org.adaway.model.backup.BackupFormat.hostFromJson;
import static org.adaway.model.backup.BackupFormat.sourceFromJson;

import timber.log.Timber;

/**
 * This class is a helper class to import user lists and hosts sources to a backup file.<br>
 * Importing a file source will no restore read access from Storage Access Framework.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public final class BackupImporter {
    /** Maximum number of sources that may be imported from a single backup file (ATK-05). */
    private static final int MAX_IMPORT_SOURCES = 200;
    /** Maximum number of host list entries (blocked/allowed/redirected) per import (ATK-24). */
    private static final int MAX_IMPORT_LIST_ITEMS = 50_000;
    static final int MAX_IMPORT_CHARACTERS = 16 * 1024 * 1024;

    private BackupImporter() {

    }

    /**
     * Import a backup file.
     *
     * @param context   The application context.
     * @param backupUri The URI of a backup file.
     */
    @UiThread
    public static void importFromBackup(Context context, Uri backupUri) {
        Context appContext = context.getApplicationContext();
        AppExecutors.getInstance().diskIO().execute(() -> {
            boolean imported = true;
            try {
                importBackup(appContext, backupUri);
            } catch (IOException | RuntimeException e) {
                Timber.e(e, "Failed to import backup.");
                imported = false;
            }
            boolean successful = imported;
            AppExecutors.getInstance().mainThread().execute(() -> notifyImportEnd(appContext, successful));
        });
    }

    @UiThread
    private static void notifyImportEnd(Context context, boolean successful) {
        Toast.makeText(
                context,
                context.getString(successful ? R.string.import_success : R.string.import_failed),
                Toast.LENGTH_LONG
        ).show();
    }

    static void importBackup(Context context, Uri backupUri) throws IOException {
        try (InputStream inputStream = context.getContentResolver().openInputStream(backupUri)) {
            if (inputStream == null) {
                throw new IOException("Backup provider returned no input stream.");
            }
            JSONObject backupObject = new JSONObject(readBackup(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8)));
            importBackup(context, backupObject);
        } catch (JSONException exception) {
            throw new IOException("Failed to parse backup file.", exception);
        } catch (SecurityException exception) {
            throw new IOException("Backup read permission denied.", exception);
        } catch (IOException exception) {
            throw new IOException("Failed to read backup file.", exception);
        }
    }

    static String readBackup(Reader reader) throws IOException {
        StringBuilder content = new StringBuilder();
        char[] buffer = new char[8192];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            if (count > MAX_IMPORT_CHARACTERS - content.length()) {
                throw new IOException("Backup exceeds maximum size.");
            }
            content.append(buffer, 0, count);
        }
        return content.toString();
    }

    private static void importBackup(Context context, JSONObject backupObject) throws JSONException {
        importBackup(AppDatabase.getInstance(context), backupObject);
    }

    static void importBackup(AppDatabase database, JSONObject backupObject) throws JSONException {
        JSONArray sources = backupObject.getJSONArray(SOURCES_KEY);
        JSONArray blocked = backupObject.getJSONArray(BLOCKED_KEY);
        JSONArray allowed = backupObject.getJSONArray(ALLOWED_KEY);
        JSONArray redirected = backupObject.getJSONArray(REDIRECTED_KEY);
        if (sources.length() > MAX_IMPORT_SOURCES
                || (long) blocked.length() + allowed.length() + redirected.length()
                > MAX_IMPORT_LIST_ITEMS) {
            throw new JSONException("Backup exceeds import limits.");
        }

        // Fully validate before the transaction; malformed later rows must not leave earlier writes.
        List<HostsSource> importedSources = new ArrayList<>();
        for (int index = 0; index < sources.length(); index++) {
            HostsSource source = sourceFromJson(sources.getJSONObject(index));
            source.setRedirectEnabled(false);
            importedSources.add(source);
        }
        List<HostListItem> importedHosts = new ArrayList<>();
        parseList(importedHosts, BLOCKED, blocked);
        parseList(importedHosts, ALLOWED, allowed);
        parseList(importedHosts, REDIRECTED, redirected);

        HostsSourceDao hostsSourceDao = database.hostsSourceDao();
        HostListItemDao hostListItemDao = database.hostsListItemDao();
        database.runInTransaction(() -> {
            hostsSourceDao.insertAll(importedSources);
            for (HostListItem host : importedHosts) {
                List<HostListItem> existing = host.getKind() == RuleKind.EXACT
                        ? hostListItemDao.getExactEntriesForHost(host.getHost())
                        : hostListItemDao.getSuffixEntriesForHost(host.getHost());
                for (HostListItem item : existing) {
                    if (item.getSourceId() == USER_SOURCE_ID) {
                        hostListItemDao.delete(item);
                    }
                }
                hostListItemDao.insert(host);
            }
            hostsSourceDao.updateActiveRuleStats(USER_SOURCE_ID);
        });
    }

    private static void parseList(List<HostListItem> target, ListType type, JSONArray hosts)
            throws JSONException {
        for (int index = 0; index < hosts.length(); index++) {
            target.add(hostFromJson(hosts.getJSONObject(index), type));
        }
    }
}
