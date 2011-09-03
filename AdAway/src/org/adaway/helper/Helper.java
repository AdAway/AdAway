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

import java.text.DateFormat;
import java.util.Date;

import org.adaway.R;
import org.adaway.util.Constants;

import com.stericson.RootTools.RootTools;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

public class Helper {
    /**
     * Builds date string out of long value containing unix date
     * 
     * @param input
     * @return formatted date string
     */
    public static String longToDateString(long input) {
        Date date = new Date(input);
        DateFormat dataformat = DateFormat.getDateInstance(DateFormat.LONG);

        return dataformat.format(date);
    }

    /**
     * Returns current unix date as long value
     * 
     * @return current date as long
     */
    public static long getCurrentLongDate() {
        Date date = new Date();

        return date.getTime();
    }

    /**
     * Check if Android is rooted, check for su binary and busybox and display possible solutions if
     * they are not available
     * 
     * @param activity
     * @return true if phone is rooted
     */
    public static boolean isAndroidRooted(final Activity activity) {
        boolean rootAvailable = false;

        // root check can be disabled for debugging in emulator
        if (Constants.disableRootCheck) {
            rootAvailable = true;
        } else {
            // check for root on device and call su binary
            if (!RootTools.isAccessGiven()) {
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setCancelable(false);
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                builder.setTitle(activity.getString(R.string.no_root_title));

                // build view from layout
                LayoutInflater factory = LayoutInflater.from(activity);
                final View dialogView = factory.inflate(R.layout.no_root_dialog, null);
                builder.setView(dialogView);

                builder.setNeutralButton(activity.getResources().getString(R.string.button_exit),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                activity.finish(); // finish current activity, means exiting app
                            }
                        });

                AlertDialog alert = builder.create();
                alert.show();
            } else {
                // checking for busybox and offer if not available
                if (!RootTools.isBusyboxAvailable()) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                    builder.setTitle(R.string.no_busybox_title);
                    builder.setMessage(activity.getString(R.string.no_busybox));
                    builder.setIcon(android.R.drawable.ic_dialog_alert);
                    builder.setPositiveButton(activity.getString(R.string.button_busybox),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    activity.finish();

                                    // offer busybox from Android Market
                                    try {
                                        RootTools.offerBusyBox(activity);
                                    } catch (Exception e) {
                                        Log.e(Constants.TAG,
                                                "Problem when offering busybox through Android Market. Excpetion: "
                                                        + e);
                                        e.printStackTrace();
                                    }
                                }
                            });
                    builder.setNegativeButton(activity.getString(R.string.button_exit),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    activity.finish();
                                }
                            });
                    AlertDialog busyboxDialog = builder.create();
                    busyboxDialog.show();
                } else {
                    rootAvailable = true;
                }
            }
        }

        return rootAvailable;
    }
}
