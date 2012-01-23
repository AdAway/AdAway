/*
 * Copyright (C) 2011 Dominik Schürmann <dominik@dominikschuermann.de>
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

import java.io.File;
import java.io.IOException;

import org.adaway.R;
import org.adaway.util.Constants;
import org.adaway.util.Log;

import com.stericson.RootTools.RootTools;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;

public class OpenHelper {

    /**
     * Open hosts file with default text app
     * 
     * @param activity
     */
    public static void openHostsFile(final Activity activity) {
        /* remount for write access */
        if (!RootTools.remount(Constants.ANDROID_SYSTEM_ETC_HOSTS, "RW")) {
            Log.e(Constants.TAG, "System partition could not be remounted as rw!");
        } else {
            openFile(activity, Constants.ANDROID_SYSTEM_ETC_HOSTS);
        }
    }

    public static void openTcpdumpLog(final Activity activity) {
        try {
            String cachePath = activity.getCacheDir().getCanonicalPath();
            String filePath = cachePath + Constants.FILE_SEPERATOR + Constants.TCPDUMP_LOG;

            File file = new File(filePath);
            if (file.exists()) {
                openFile(activity, filePath);
            } else {
                Log.e(Constants.TAG, "Tcpdump log is not existing!");
            }
        } catch (IOException e) {
            Log.e(Constants.TAG, "Can not get cache dir: " + e);
            e.printStackTrace();
        }
    }

    /**
     * Helper to open files
     * 
     * @param activity
     * @param file
     */
    private static void openFile(final Activity activity, String file) {
        /* start default app for opening plain text files */
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri uri = Uri.parse("file://" + file);
        intent.setDataAndType(uri, "text/plain");

        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setPositiveButton(activity.getString(R.string.button_yes),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setData(Uri.parse("market://details?id=jp.sblo.pandora.jota"));

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

            builder.setTitle(R.string.no_text_editor_title);
            builder.setMessage(activity.getString(org.adaway.R.string.no_text_editor));
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
        }
    }
}
