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
        import android.os.Environment;
        import android.util.ArraySet;
        import android.widget.Toast;

        import com.annimon.stream.Collectors;
        import com.annimon.stream.Stream;

        import org.adaway.R;
        import org.adaway.db.AppDatabase;
        import org.adaway.db.dao.HostListItemDao;
        import org.adaway.db.dao.HostsSourceDao;
        import org.adaway.db.entity.HostListItem;
        import org.adaway.db.entity.HostsSource;
        import org.adaway.db.entity.ListType;
        import org.adaway.util.AppExecutors;
        import org.adaway.util.Constants;
        import org.adaway.util.HostsParser;
        import org.adaway.util.Log;

        import java.io.BufferedReader;
        import java.io.BufferedWriter;
        import java.io.File;
        import java.io.FileNotFoundException;
        import java.io.FileWriter;
        import java.io.IOException;
        import java.io.InputStream;
        import java.io.InputStreamReader;
        import java.lang.ref.WeakReference;
        import java.util.ArrayList;
        import java.util.HashMap;
        import java.util.HashSet;
        import java.util.List;
        import java.util.Map;
        import java.util.Set;

/**
 * This class is a helper class to import/export user lists to a backup file on sdcard.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class ImportExportHostsHelper {
    /**
     * The request code to identify the selection of a file in {@link androidx.fragment.app.Fragment#onActivityResult(int, int, Intent)}.
     */
    public final static int REQUEST_CODE_IMPORT = 45;

    /**
     * Import the given user lists backup file.
     *
     * @param context      The application context.
     * @param userListsUri The URI of the user lists backup file.
     */
    public static void importLists(Context context, Uri userListsUri) {
        // Import user lists
        new ImportListsTask(context).execute(userListsUri);
    }

    /**
     * Exports all lists to adaway-export file on sdcard.
     *
     * @param context The application context.
     */
    public static void exportLists(final Context context) {
        // Export user lists
        new ExportListsTask(context).execute();
    }

    /**
     * This class is an {@link AsyncTask} to import user lists.
     *
     * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
     */
    private static class ImportListsTask extends AsyncTask<Uri, Void, Void> {
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
        private ImportListsTask(Context context) {
            // Store context into weak reference to prevent memory leak
            this.mWeakContext = new WeakReference<>(context);
        }

        @Override
        protected Void doInBackground(Uri... results) {
            // Check parameters
            if (results.length < 1) {
                return null;
            }
            // Get URI to export lists
            Uri result = results[0];
            // Get context from weak reference
            Context context = this.mWeakContext.get();
            if (context == null) {
                return null;
            }
            // Get database
            AppDatabase database = AppDatabase.getInstance(context);
            // Get input stream from user selected URI
            try (InputStream inputStream = context.getContentResolver().openInputStream(result)) {
                if (inputStream != null) {
                    // Create reader from input stream
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                        Set<String> newHosts = new HashSet<>();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (HostsSource.isValidUrl(line)) {
                                newHosts.add(line);
                            }
                        }
                        this.importHosts(database, newHosts);
                    }
                }
            } catch (FileNotFoundException exception) {
                Log.e(Constants.TAG, "File not found!", exception);
            } catch (IOException exception) {
                Log.e(Constants.TAG, "IO Exception", exception);
            }
            // Return nothing
            return null;
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
        protected void onPostExecute(Void unused) {
            super.onPostExecute(unused);
            // Check progress dialog
            if (this.mProgressDialog != null) {
                this.mProgressDialog.dismiss();
            }
        }

        private void importHosts(AppDatabase database, Set<String> hosts) {
            for (String url: hosts) {
                HostsSource source = new HostsSource();
                source.setUrl(url);
                source.setEnabled(true);
                AppExecutors.getInstance().diskIO().execute(() -> database.hostsSourceDao().insert(source));
            }
        }
    }

    /**
     * This class is an {@link AsyncTask} to export user lists.
     *
     * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
     */
    private static class ExportListsTask extends AsyncTask<Void, Void, Boolean> {
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
        private ExportListsTask(Context context) {
            // Store context into weak reference to prevent memory leak
            this.mWeakContext = new WeakReference<>(context);
        }

        @Override
        protected Boolean doInBackground(Void... unused) {
            // Get context from weak reference
            Context context = this.mWeakContext.get();
            if (context == null) {
                // Fail to export
                return false;
            }
            // Get database
            AppDatabase database = AppDatabase.getInstance(context);
            HostsSourceDao hostListItemDao = database.hostsSourceDao();
            // Get list values
            List<HostsSource> hosts = hostListItemDao.getEnabled();
            // Check if sdcard can be written
            File sdcard = Environment.getExternalStorageDirectory();
            if (!sdcard.canWrite()) {
                Log.e(Constants.TAG, "External storage can not be written.");
                // Fail to export
                return false;
            }
            // Create export file
            File exportFile = new File(sdcard, "adaway-hosts-export");
            // Open writer on the export file
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(exportFile))) {
                writer.write(Constants.HEADER_EXPORT);
                writer.newLine();
                // Write blacklist items
                for (HostsSource host : hosts) {
                    writer.write(host.getUrl());
                    writer.newLine();
                }
            } catch (IOException exception) {
                Log.e(Constants.TAG, "Could not write file.", exception);
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
            Toast toast = Toast.makeText(
                    context,
                    context.getString(exported ? R.string.export_success : R.string.export_failed),
                    Toast.LENGTH_LONG
            );
            toast.show();
        }
    }
}
