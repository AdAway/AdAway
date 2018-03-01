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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;

import org.adaway.R;
import org.adaway.provider.ProviderHelper;
import org.adaway.ui.dialog.ActivityNotFoundDialogFragment;
import org.adaway.util.Constants;
import org.adaway.util.HostsParser;
import org.adaway.util.Log;

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

import gnu.trove.set.hash.THashSet;
import gnu.trove.map.hash.THashMap;

public class ImportExportHelper {

    // request code to identify the selection of a file in onActivityResult() in activity
    final static int REQUEST_CODE_IMPORT = 42;

    /**
     * Opens file manager to open file and return it in onActivityResult in Activity
     *
     * @param activity
     */
    public static void openFileStream(final FragmentActivity activity) {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            activity.startActivityForResult(intent, REQUEST_CODE_IMPORT);
        } catch (ActivityNotFoundException e) {
            ActivityNotFoundDialogFragment notFoundDialog = ActivityNotFoundDialogFragment
                    .newInstance(R.string.no_file_manager_title, R.string.no_file_manager,
                            "market://details?id=org.openintents.filemanager", "OI File Manager");

            notFoundDialog.show(activity.getSupportFragmentManager(), "notFoundDialog");
        }
    }

    /**
     * After user selected file in file manager with openFile() the path of the selected file is
     * returned by onActivityResult in the corresponding activity.
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    public static void onActivityResultHandleImport(final Context context, int requestCode,
                                                    int resultCode, Intent data) {

        // if request is from import
        if (requestCode == REQUEST_CODE_IMPORT && resultCode == Activity.RESULT_OK && data != null
                && data.getData() != null) {

            final Uri result = data.getData();
            Log.d(Constants.TAG, "File manager Uri: " + result.toString());

            // do it in AsyncTask without blocking the user interface thread
            AsyncTask<Void, Void, Void> importListsTask = new AsyncTask<Void, Void, Void>() {
                private ProgressDialog mApplyProgressDialog;

                @Override
                protected Void doInBackground(Void... unused) {
                    THashSet<String> blacklist = null;
                    THashSet<String> whitelist = null;
                    THashMap<String, String> redirectionList = null;
                    try {
                        InputStream is = context.getContentResolver().openInputStream(result);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(is));

                        HostsParser parser = new HostsParser(reader, true, true);
                        blacklist = parser.getBlacklist();
                        whitelist = parser.getWhitelist();
                        redirectionList = parser.getRedirectionList();

                        is.close();
                    } catch (FileNotFoundException e) {
                        Log.e(Constants.TAG, "File not found!", e);
                    } catch (IOException e) {
                        Log.e(Constants.TAG, "IO Exception", e);
                    }

                    ProviderHelper.importBlacklist(context, blacklist);
                    ProviderHelper.importWhitelist(context, whitelist);
                    ProviderHelper.importRedirectionList(context, redirectionList);

                    // return nothing as type is Void
                    return null;
                }

                @Override
                protected void onPreExecute() {
                    super.onPreExecute();
                    mApplyProgressDialog = new ProgressDialog(context);
                    mApplyProgressDialog.setMessage(context.getString(R.string.import_dialog));
                    mApplyProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                    mApplyProgressDialog.setCancelable(false);
                    mApplyProgressDialog.show();
                }

                @Override
                protected void onPostExecute(Void unused) {
                    super.onPostExecute(unused);
                    mApplyProgressDialog.dismiss();
                }
            };

            importListsTask.execute();
        }
    }

    /**
     * Exports all lists to adaway-export file on sdcard
     *
     * @param context
     */
    public static void exportLists(final Context context) {
        // do it in AsyncTask without blocking the user interface thread
        AsyncTask<Void, Void, Void> exportListsTask = new AsyncTask<Void, Void, Void>() {
            private ProgressDialog mApplyProgressDialog;

            @Override
            protected Void doInBackground(Void... unused) {
                THashSet<String> whitelist = ProviderHelper.getEnabledWhitelistHashSet(context);
                THashSet<String> blacklist = ProviderHelper.getEnabledBlacklistHashSet(context);
                THashMap<String, String> redirectionList = ProviderHelper
                        .getEnabledRedirectionListHashMap(context);

                try {
                    File sdcard = Environment.getExternalStorageDirectory();
                    if (sdcard.canWrite()) {
                        File exportFile = new File(sdcard, "adaway-export");
                        FileWriter writer = new FileWriter(exportFile);
                        BufferedWriter out = new BufferedWriter(writer);

                        out.write(Constants.HEADER_EXPORT + Constants.LINE_SEPERATOR);

                        // write blacklist
                        Iterator<String> itrBlacklist = blacklist.iterator();
                        while (itrBlacklist.hasNext()) {
                            out.write(Constants.LOCALHOST_IPv4 + " " + itrBlacklist.next()
                                    + Constants.LINE_SEPERATOR);
                        }

                        // write whitelist
                        Iterator<String> itrWhitelist = whitelist.iterator();
                        while (itrWhitelist.hasNext()) {
                            out.write(Constants.WHITELIST_ENTRY + " " + itrWhitelist.next()
                                    + Constants.LINE_SEPERATOR);
                        }

                        // write redirection list
                        for (HashMap.Entry<String, String> item : redirectionList.entrySet()) {
                            out.write(item.getValue() + " " + item.getKey()
                                    + Constants.LINE_SEPERATOR);
                        }

                        out.close();
                    }
                } catch (IOException e) {
                    Log.e(Constants.TAG, "Could not write file " + e.getMessage());
                }

                // return nothing as type is Void
                return null;
            }

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                mApplyProgressDialog = new ProgressDialog(context);
                mApplyProgressDialog.setMessage(context.getString(R.string.export_dialog));
                mApplyProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                mApplyProgressDialog.setCancelable(false);
                mApplyProgressDialog.show();
            }

            @Override
            protected void onPostExecute(Void unused) {
                super.onPostExecute(unused);

                mApplyProgressDialog.dismiss();
                Toast toast = Toast.makeText(context, context.getString(R.string.export_success),
                        Toast.LENGTH_LONG);
                toast.show();
            }
        };

        exportListsTask.execute();
    }
}
