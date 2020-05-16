/*
 * Copyright (C) 2011-2012 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This file is part of AdAway.
 *
 * AdAway is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AdAway is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AdAway.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.adaway.helper;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.widget.Toast;

import com.annimon.stream.Stream;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Optional;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;
import static org.adaway.util.Constants.TAG;

/**
 * This class is a helper class to import/export user lists and hosts sources to a backup file.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class ImportExportHelper {
    /**
     * The request code to identify the selection of a file in {@link androidx.fragment.app.Fragment#onActivityResult(int, int, Intent)}.
     */
    public static final int IMPORT_REQUEST_CODE = 42;
    /**
     * The request code to identify the selection of a file in {@link androidx.fragment.app.Fragment#onActivityResult(int, int, Intent)}.
     */
    public static final int EXPORT_REQUEST_CODE = 43;
    /*
     * Backup format.
     */
    private static final String SOURCES_KEY = "sources";
    private static final String BLOCKED_KEY = "blocked";
    private static final String ALLOWED_KEY = "allowed";
    private static final String REDIRECTED_KEY = "redirected";
    private static final String ENABLED_ATTRIBUTE = "enabled";
    private static final String HOST_ATTRIBUTE = "host";
    private static final String REDIRECT_ATTRIBUTE = "redirect";
    private static final String URL_ATTRIBUTE = "url";

    /**
     * Import a backup file.
     *
     * @param context   The application context.
     * @param backupUri The URI of a backup file.
     */
    public static void importFromBackup(Context context, Uri backupUri) {
        new ImportTask(context).execute(backupUri);
    }

    /**
     * Export all user lists and hosts sources to a backup file on the external storage.
     *
     * @param context The application context.
     */
    public static void exportToBackup(Context context, Uri backupUri) {
        // Export user lists
        new ExportTask(context).execute(backupUri);
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

    private static void importBackup(Context context, JSONObject backupObject) throws JSONException {
        AppDatabase database = AppDatabase.getInstance(context);
        HostsSourceDao hostsSourceDao = database.hostsSourceDao();
        HostListItemDao hostListItemDao = database.hostsListItemDao();

        importSourceBackup(hostsSourceDao, backupObject.getJSONArray(SOURCES_KEY));
        importListBackup(hostListItemDao, BLOCKED, backupObject.getJSONArray(BLOCKED_KEY));
        importListBackup(hostListItemDao, ALLOWED, backupObject.getJSONArray(ALLOWED_KEY));
        importListBackup(hostListItemDao, REDIRECTED, backupObject.getJSONArray(REDIRECTED_KEY));
    }

    private static JSONArray buildSourcesBackup(List<HostsSource> sources) throws JSONException {
        JSONArray sourceArray = new JSONArray();
        for (HostsSource source : sources) {
            sourceArray.put(sourceToJson(source));
        }
        return sourceArray;
    }

    private static void importSourceBackup(HostsSourceDao hostsSourceDao, JSONArray sources) throws JSONException {
        for (int index = 0; index < sources.length(); index++) {
            JSONObject sourceObject = sources.getJSONObject(index);
            hostsSourceDao.insert(sourceFromJson(sourceObject));
        }
    }

    private static JSONArray buildListBackup(List<HostListItem> hosts) throws JSONException {
        JSONArray listArray = new JSONArray();
        for (HostListItem host : hosts) {
            listArray.put(hostToJson(host));
        }
        return listArray;
    }

    private static void importListBackup(HostListItemDao hostListItemDao, ListType type, JSONArray hosts) throws JSONException {
        for (int index = 0; index < hosts.length(); index++) {
            JSONObject hostObject = hosts.getJSONObject(index);
            HostListItem host = hostFromJson(hostObject);
            host.setType(type);
            Optional<Integer> id = hostListItemDao.getHostId(host.getHost());
            if (id.isPresent()) {
                host.setId(id.get());
                hostListItemDao.update(host);
            } else {
                hostListItemDao.insert(host);
            }
        }
    }

    private static JSONObject sourceToJson(HostsSource source) throws JSONException {
        JSONObject sourceObject = new JSONObject();
        sourceObject.put(URL_ATTRIBUTE, source.getUrl());
        sourceObject.put(ENABLED_ATTRIBUTE, source.isEnabled());
        return sourceObject;
    }

    private static HostsSource sourceFromJson(JSONObject sourceObject) throws JSONException {
        HostsSource source = new HostsSource();
        source.setUrl(sourceObject.getString(URL_ATTRIBUTE));
        source.setEnabled(sourceObject.getBoolean(ENABLED_ATTRIBUTE));
        return source;
    }

    private static JSONObject hostToJson(HostListItem host) throws JSONException {
        JSONObject hostObject = new JSONObject();
        hostObject.put(HOST_ATTRIBUTE, host.getHost());
        String redirection = host.getRedirection();
        if (redirection != null && !redirection.isEmpty()) {
            hostObject.put(REDIRECT_ATTRIBUTE, redirection);
        }
        hostObject.put(ENABLED_ATTRIBUTE, host.isEnabled());
        return hostObject;
    }

    private static HostListItem hostFromJson(JSONObject hostObject) throws JSONException {
        HostListItem host = new HostListItem();
        host.setHost(hostObject.getString(HOST_ATTRIBUTE));
        if (hostObject.has(REDIRECT_ATTRIBUTE)) {
            host.setRedirection(hostObject.getString(REDIRECT_ATTRIBUTE));
        }
        host.setEnabled(hostObject.getBoolean(ENABLED_ATTRIBUTE));
        host.setSourceId(USER_SOURCE_ID);
        return host;
    }

    /**
     * This class is an {@link AsyncTask} to import from a backup file.
     *
     * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
     */
    private static class ImportTask extends AsyncTask<Uri, Void, Boolean> {
        /**
         * A weak reference to application context.
         */
        private final WeakReference<Context> mWeakContext;
        /**
         * The progress dialog.
         */
        private ProgressDialog mProgressDialog;

        /**
         * Constructor.
         *
         * @param context The application context.
         */
        private ImportTask(Context context) {
            // Store context into weak reference to prevent memory leak
            this.mWeakContext = new WeakReference<>(context);
        }

        @Override
        protected Boolean doInBackground(Uri... params) {
            // Check parameters
            if (params.length < 1) {
                return false;
            }
            // Get URI to export lists
            Uri result = params[0];
            // Get context from weak reference
            Context context = this.mWeakContext.get();
            if (context == null) {
                return false;
            }
            // Get input stream from user selected URI
            try (InputStream inputStream = context.getContentResolver().openInputStream(result);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                StringBuilder contentBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    contentBuilder.append(line);
                }
                JSONObject backupObject = new JSONObject(contentBuilder.toString());
                importBackup(context, backupObject);
            } catch (JSONException exception) {
                Log.e(TAG, "Failed to parse backup file.", exception);
                return false;
            } catch (FileNotFoundException exception) {
                Log.e(TAG, "Failed to find backup file.", exception);
                return false;
            } catch (IOException exception) {
                Log.e(TAG, "Failed to read backup file.", exception);
                return false;
            }
            return true;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // Get context from weak reference
            Context context = this.mWeakContext.get();
            if (context == null) {
                return;
            }
            // Check and show progress dialog
            this.mProgressDialog = new ProgressDialog(context);
            this.mProgressDialog.setMessage(context.getString(R.string.import_dialog));
            this.mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            this.mProgressDialog.setCancelable(false);
            this.mProgressDialog.show();
        }

        @Override
        protected void onPostExecute(Boolean imported) {
            super.onPostExecute(imported);
            // Check progress dialog
            if (this.mProgressDialog != null) {
                this.mProgressDialog.dismiss();
            }
            // Get context from weak reference
            Context context = this.mWeakContext.get();
            if (context == null) {
                return;
            }
            // Display user toast notification
            Toast toast = Toast.makeText(
                    context,
                    context.getString(imported ? R.string.import_success : R.string.import_failed),
                    Toast.LENGTH_LONG
            );
            toast.show();
        }
    }

    /**
     * This class is an {@link AsyncTask} to export to a backup file.
     *
     * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
     */
    private static class ExportTask extends AsyncTask<Uri, Void, Boolean> {
        /**
         * A weak reference to application context.
         */
        private final WeakReference<Context> mWeakContext;
        /**
         * The progress dialog.
         */
        private ProgressDialog mProgressDialog;

        /**
         * Constructor.
         *
         * @param context The application context.
         */
        private ExportTask(Context context) {
            // Store context into weak reference to prevent memory leak
            this.mWeakContext = new WeakReference<>(context);
        }

        @Override
        protected Boolean doInBackground(Uri... params) {
            // Check parameters
            if (params.length < 1) {
                return false;
            }
            // Get URI to export lists
            Uri backupUri = params[0];
            // Get context from weak reference
            Context context = this.mWeakContext.get();
            if (context == null) {
                // Fail to export
                return false;
            }
            // Open writer on the export file
            try (OutputStream outputStream = context.getContentResolver().openOutputStream(backupUri);
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
                JSONObject backup = makeBackup(context);
                writer.write(backup.toString(4));
            } catch (JSONException e) {
                Log.e(TAG, "Failed to generate backup.", e);
                return false;
            } catch (IOException e) {
                Log.e(TAG, "Could not write file.", e);
                return false;
            }
            // Return successfully exported
            return true;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // Check context weak reference
            Context context = this.mWeakContext.get();
            if (context == null) {
                return;
            }
            // Create and show progress dialog
            this.mProgressDialog = new ProgressDialog(context);
            this.mProgressDialog.setMessage(context.getString(R.string.export_dialog));
            this.mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            this.mProgressDialog.setCancelable(false);
            this.mProgressDialog.show();
        }

        @Override
        protected void onPostExecute(Boolean exported) {
            super.onPostExecute(exported);
            // Check progress dialog
            if (this.mProgressDialog != null) {
                this.mProgressDialog.dismiss();
            }
            // Get context from weak reference
            Context context = this.mWeakContext.get();
            if (context == null) {
                return;
            }
            // Display user toast notification
            Toast.makeText(
                    context,
                    context.getString(exported ? R.string.export_success : R.string.export_failed),
                    Toast.LENGTH_LONG
            ).show();
        }
    }
}
