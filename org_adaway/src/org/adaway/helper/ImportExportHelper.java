package org.adaway.helper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;

import org.adaway.R;
import org.adaway.provider.ProviderHelper;
import org.adaway.util.Constants;
import org.adaway.util.HostsParser;
import org.adaway.util.Log;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;

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
        // (for example here to read the image data).
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            activity.startActivityForResult(Intent.createChooser(intent, "Select file"),
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
     * handle onActivityResult for blacklist
     * 
     * @param requestCode
     * @param resultCode
     * @param data
     */
    public static void onActivityResult(Context context, final int requestCode,
            final int resultCode, final Intent data) {

        if (requestCode == REQEST_CODE_FILE_OPEN && resultCode == Activity.RESULT_OK
                && data != null && data.getData() != null) {

            Uri result = data.getData();
            Log.d(Constants.TAG, "uri: " + result.toString());

            HashSet<String> hostnames = null;
            try {
                InputStream is = context.getContentResolver().openInputStream(result);
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));

                HostsParser parser = new HostsParser(reader);
                hostnames = parser.getHostnames();

                is.close();
            } catch (FileNotFoundException e) {
                Log.e(Constants.TAG, "File not found!");
                e.printStackTrace();
            } catch (IOException e) {
                Log.e(Constants.TAG, "IO Exception");
                e.printStackTrace();
            }

            Log.d(Constants.TAG, "hostnames: " + hostnames.toString());

            ProviderHelper.importLists(context, hostnames);
        }
    }
}
