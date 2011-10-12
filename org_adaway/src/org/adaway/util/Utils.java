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

package org.adaway.util;

import org.adaway.R;
import org.adaway.helper.PreferencesHelper;
import org.adaway.util.Log;

import com.stericson.RootTools.RootTools;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

public class Utils {
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
        if (Constants.DEBUG_DISABLE_ROOT_CHECK) {
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

    /**
     * Show reboot question
     * 
     * @param titleR
     *            resource id of title string
     * @param messageR
     *            resource id of message string
     */
    public static void rebootQuestion(final Activity activity, int titleR, int messageR) {
        // only show if reboot dialog is not disabled in preferences
        if (!PreferencesHelper.getNeverReboot(activity)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(titleR);
            // builder.setMessage(activity.getString(messageR));
            builder.setIcon(android.R.drawable.ic_dialog_info);

            // build view from layout
            LayoutInflater factory = LayoutInflater.from(activity);
            final View dialogView = factory.inflate(R.layout.reboot_dialog, null);

            // set text in view based on given resource id
            TextView text = (TextView) dialogView.findViewById(R.id.reboot_dialog_text);
            text.setText(activity.getString(messageR));

            builder.setView(dialogView);

            builder.setPositiveButton(activity.getString(R.string.button_yes),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {

                            // set preference to never show reboot dialog again if checkbox is
                            // checked
                            CheckBox checkBox = (CheckBox) dialogView
                                    .findViewById(R.id.reboot_dialog_checkbox);
                            if (checkBox.isChecked()) {
                                PreferencesHelper.setNeverReboot(activity, true);
                            }

                            try {
                                ApplyUtils.quickReboot();
                            } catch (CommandException e) {
                                Log.e(Constants.TAG, "Exception: " + e);
                                e.printStackTrace();
                            }
                        }
                    });
            builder.setNegativeButton(activity.getString(R.string.button_no),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {

                            // set preference to never show reboot dialog again if checkbox is
                            // checked
                            CheckBox checkBox = (CheckBox) dialogView
                                    .findViewById(R.id.reboot_dialog_checkbox);
                            if (checkBox.isChecked()) {
                                PreferencesHelper.setNeverReboot(activity, true);
                            }

                            dialog.dismiss();
                        }
                    });
            AlertDialog question = builder.create();

            question.show();
        }
    }

    /**
     * Checks if Android is online
     * 
     * @param context
     * @return returns true if online
     */
    public static boolean isAndroidOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnectedOrConnecting()) {
            return true;
        }
        return false;
    }
}
