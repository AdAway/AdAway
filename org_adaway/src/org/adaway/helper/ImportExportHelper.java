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
import java.util.HashSet;
import java.util.Iterator;

import org.adaway.R;
import org.adaway.provider.ProviderHelper;
import org.adaway.util.Constants;
import org.adaway.util.HostsParser;
import org.adaway.util.Log;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.widget.Toast;

public class ImportExportHelper {

    final static int REQEST_CODE_FILE_OPEN = 42;

    /**
     * Opens file manager to open file and return it in onActivityResult in Activity
     * 
     */
    public static void openFile(final Activity activity) {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("text/plain");
        // Do this if you need to be able to open the returned URI as a stream
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            activity.startActivityForResult(
                    Intent.createChooser(intent,
                            activity.getString(R.string.import_select_filemanager)),
                    REQEST_CODE_FILE_OPEN);
        } catch (ActivityNotFoundException e) {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setPositiveButton(activity.getString(R.string.button_yes),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setData(Uri
                                    .parse("market://details?id=org.openintents.filemanager"));

                            try {
                                activity.startActivity(intent);
                            } catch (ActivityNotFoundException e) {
                                Log.e(Constants.TAG, "No Google Android Market installed!");
                                e.printStackTrace();
                            }
                        }
                    });
            builder.setNegativeButton(activity.getString(R.string.button_no),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                        }
                    });

            builder.setTitle(R.string.no_file_manager_title);
            builder.setMessage(activity.getString(org.adaway.R.string.no_file_manager));
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
        }
    }

    /**
     * Exports all lists to adaway-export file on sdcard
     * 
     * @param context
     */
    public static void exportLists(final Context context) {
        AsyncTask<Void, Void, Void> exportListsTask = new AsyncTask<Void, Void, Void>() {
            private ProgressDialog mApplyProgressDialog;

            @Override
            protected Void doInBackground(Void... unused) {
                HashSet<String> whitelist = ProviderHelper.getEnabledWhitelistArrayList(context);
                HashSet<String> blacklist = ProviderHelper.getEnabledBlacklistArrayList(context);
                HashMap<String, String> redirectionList = ProviderHelper
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

    /**
     * Handle onActivityResult to import lists
     * 
     * @param requestCode
     * @param resultCode
     * @param data
     */
    public static void onActivityResult(final Context context, int requestCode, int resultCode,
            Intent data) {

        // if request is from import
        if (requestCode == REQEST_CODE_FILE_OPEN && resultCode == Activity.RESULT_OK
                && data != null && data.getData() != null) {

            final Uri result = data.getData();
            Log.d(Constants.TAG, "File manager Uri: " + result.toString());

            AsyncTask<Void, Void, Void> importListsTask = new AsyncTask<Void, Void, Void>() {
                private ProgressDialog mApplyProgressDialog;

                @Override
                protected Void doInBackground(Void... unused) {
                    HashSet<String> blacklist = null;
                    HashSet<String> whitelist = null;
                    HashMap<String, String> redirectionList = null;
                    try {
                        InputStream is = context.getContentResolver().openInputStream(result);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(is));

                        HostsParser parser = new HostsParser(reader);
                        blacklist = parser.getBlacklist();
                        whitelist = parser.getWhitelist();
                        redirectionList = parser.getRedirectionList();

                        is.close();
                    } catch (FileNotFoundException e) {
                        Log.e(Constants.TAG, "File not found!");
                        e.printStackTrace();
                    } catch (IOException e) {
                        Log.e(Constants.TAG, "IO Exception");
                        e.printStackTrace();
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
}
