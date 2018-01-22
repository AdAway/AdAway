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

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v4.app.FragmentActivity;
import android.widget.Toast;

import org.adaway.R;
import org.adaway.provider.ProviderHelper;
import org.adaway.ui.dialog.ActivityNotFoundDialogFragment;
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
import java.util.HashMap;

import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.THashSet;

public class ImportExportHelper {
    /**
     * The request code to identify the selection of a file in onActivityResult() in activity.
     */
    private final static int REQUEST_CODE_IMPORT = 42;

    /**
     * Opens file manager to open file and return it in onActivityResult in Activity
     *
     * @param activity The application activity.
     */
    public static void openFileStream(final FragmentActivity activity) {
        // Create intent to pick a text file
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("text/plain");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // Start file picker activity
        try {
            activity.startActivityForResult(intent, REQUEST_CODE_IMPORT);
        } catch (ActivityNotFoundException exception) {
            // Show dialog to install file picker
            ActivityNotFoundDialogFragment.newInstance(
                    R.string.no_file_manager_title,
                    R.string.no_file_manager,
                    "market://details?id=org.openintents.filemanager", "OI File Manager"
            ).show(activity.getSupportFragmentManager(), "notFoundDialog");
        }
    }

    /**
     * After user selected file in file manager with openFile() the path of the selected file is
     * returned by onActivityResult in the corresponding activity.
     *
     * @param requestCode The integer request code originally supplied to
     *                    startActivityForResult(), allowing you to identify who this
     *                    result came from.
     * @param resultCode  The integer result code returned by the child activity
     *                    through its setResult().
     * @param data        An Intent, which can return result data to the caller
     *                    (various data can be attached to Intent "extras").
     */
    public static void onActivityResultHandleImport(final Context context, int requestCode,
                                                    int resultCode, Intent data) {
        // if request is from import
        if (requestCode == REQUEST_CODE_IMPORT && resultCode == Activity.RESULT_OK && data != null
                && data.getData() != null) {
            // Get selected file URI
            Uri result = data.getData();
            Log.d(Constants.TAG, "File manager URI: " + result.toString());
            // Import user lists
            new ImportListsTask(context).execute(result);
        }
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
            // Get input stream from user selected URI
            try (InputStream inputStream = context.getContentResolver().openInputStream(result)) {
                if (inputStream != null) {
                    // Create reader from input stream
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                        // Parse user lists
                        HostsParser parser = new HostsParser(reader, true, true);
                        // Import parsed user lists
                        ProviderHelper.importBlacklist(context, parser.getBlacklist());
                        ProviderHelper.importWhitelist(context, parser.getWhitelist());
                        ProviderHelper.importRedirectionList(context, parser.getRedirectionList());
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
    }

    /**
     * This class is an {@link AsyncTask} to export user lists.
     *
     * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
     */
    private static class ExportListsTask extends AsyncTask<Void, Void, Void> {
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
        protected Void doInBackground(Void... unused) {
            // Get context from weak reference
            Context context = this.mWeakContext.get();
            if (context == null) {
                return null;
            }
            // Get list values
            THashSet<String> whitelist = ProviderHelper.getEnabledWhitelistHashSet(context);
            THashSet<String> blacklist = ProviderHelper.getEnabledBlacklistHashSet(context);
            THashMap<String, String> redirectionList = ProviderHelper
                    .getEnabledRedirectionListHashMap(context);
            // Check if sdcard can be written
            File sdcard = Environment.getExternalStorageDirectory();
            if (!sdcard.canWrite()) {
                Log.e(Constants.TAG, "External storage can not be written.");
                return null;
            }
            // Create export file
            File exportFile = new File(sdcard, "adaway-export");
            // Open writer on the export file
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(exportFile))) {
                writer.write(Constants.HEADER_EXPORT + Constants.LINE_SEPERATOR);
                // Write blacklist items
                for (String aBlacklist : blacklist) {
                    writer.write(Constants.LOCALHOST_IPv4 + " " + aBlacklist
                            + Constants.LINE_SEPERATOR);
                }
                // Write whitelist items
                for (String aWhitelist : whitelist) {
                    writer.write(Constants.WHITELIST_ENTRY + " " + aWhitelist
                            + Constants.LINE_SEPERATOR);
                }
                // Write redirection list items
                for (HashMap.Entry<String, String> item : redirectionList.entrySet()) {
                    writer.write(item.getValue() + " " + item.getKey()
                            + Constants.LINE_SEPERATOR);
                }
            } catch (IOException exception) {
                Log.e(Constants.TAG, "Could not write file.", exception);
            }
            // Return nothing
            return null;
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
        protected void onPostExecute(Void unused) {
            super.onPostExecute(unused);
            // Check progress dialog
            if (this.mProgressDialog != null) {
                this.mProgressDialog.dismiss();
            }
            // Get context from weak reference
            Context context = this.mWeakContext.get();
            if (context != null) {
                // Display user success toast notification
                Toast toast = Toast.makeText(
                        context,
                        context.getString(R.string.export_success),
                        Toast.LENGTH_LONG
                );
                toast.show();
            }
        }
    }
}
