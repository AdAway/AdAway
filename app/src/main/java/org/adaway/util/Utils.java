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

package org.adaway.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.sufficientlysecure.rootcommands.RootCommands;
import org.sufficientlysecure.rootcommands.Shell;
import org.sufficientlysecure.rootcommands.command.SimpleCommand;

import java.util.List;

public class Utils {
    /**
     * Private constructor.
     */
    private Utils() {

    }

    /**
     * Check if Android is rooted, check for su binary and busybox and display possible solutions if
     * they are not available
     *
     * @return true if phone is rooted
     */
    public static boolean isAndroidRooted() {
        // root check can be disabled for debugging in emulator
        if (Constants.DEBUG_DISABLE_ROOT_CHECK) {
            return true;
        }
        return RootCommands.rootAccessGiven();
    }

    /**
     * Display a dialog to inform user that root was not found then finish the activity.
     *
     * @param activity The current activity.
     */
    public static void displayNoRootDialog(Activity activity) {
        // build view from layout
        LayoutInflater factory = LayoutInflater.from(activity);
        View dialogView = factory.inflate(R.layout.no_root_dialog, null);

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.no_root_title)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setCancelable(false)
                .setView(dialogView)
                .setNeutralButton(R.string.button_exit, (dialog, which) -> activity.finish())
                .show();
    }

    /**
     * Show reboot question
     *
     * @param titleR   resource id of title string
     * @param messageR resource id of message string
     */
    public static void rebootQuestion(final Context context, int titleR, int messageR) {
        // build view from layout
        LayoutInflater factory = LayoutInflater.from(context);
        final View dialogView = factory.inflate(R.layout.reboot_dialog, null);
        // set text in view based on given resource id
        TextView text = dialogView.findViewById(R.id.reboot_dialog_text);
        text.setText(context.getString(messageR));

        Runnable updatePreferences = () -> {
            // set preference to never show reboot dialog again if checkbox is checked
            CheckBox checkBox = dialogView.findViewById(R.id.reboot_dialog_checkbox);
            if (checkBox.isChecked()) {
                PreferenceHelper.setNeverReboot(context, true);
            }
        };

        new MaterialAlertDialogBuilder(context)
                .setTitle(titleR)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setView(dialogView)
                .setPositiveButton(context.getString(R.string.button_yes),
                        (dialog, id) -> {
                            updatePreferences.run();
                            reboot();
                        }
                )
                .setNegativeButton(context.getString(R.string.button_no),
                        (dialog, id) -> {
                            updatePreferences.run();
                            dialog.dismiss();
                        }
                )
                .show();
    }

    private static void reboot() {
        try (Shell rootShell = Shell.startRootShell()) {
            rootShell.add(new SimpleCommand("svc power reboot"));
        } catch (Exception e) {
            Log.e(Constants.TAG, "Problem with rebooting", e);
        }
    }

    /**
     * Checks if Android is online
     *
     * @param context
     * @return returns true if online
     */
    public static boolean isAndroidOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    /**
     * Checks if the application is installed on the SD card. See
     * http://stackoverflow.com/questions/
     * 5814474/how-can-i-find-out-if-my-app-is-installed-on-sd-card
     *
     * @return <code>true</code> if the application is installed on the sd card
     */
    @SuppressLint("SdCardPath")
    public static boolean isInstalledOnSdCard(Context context) {
        // check for API level 8 and higher
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
            ApplicationInfo ai = pi.applicationInfo;
            return (ai.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) == ApplicationInfo.FLAG_EXTERNAL_STORAGE;
        } catch (NameNotFoundException e) {
            // ignore
        }

        // check for API level 7 (rooted devices) - check files dir
        try {
            String filesDir = context.getFilesDir().getAbsolutePath();
            if (filesDir.startsWith("/data/")) {
                return false;
            } else if (filesDir.contains("/mnt/") || filesDir.contains("/sdcard/")) {
                return true;
            }
        } catch (Throwable e) {
            // ignore
        }

        return false;
    }

    /**
     * Checks if AdAway is in foreground, see
     * http://stackoverflow.com/questions/8489993/check-android-application-is-in-foreground-or-not
     *
     * @param context
     * @return
     */
    public static boolean isInForeground(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return false;
        }
        List<RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
        if (appProcesses == null) {
            return false;
        }
        String packageName = context.getPackageName();
        for (RunningAppProcessInfo appProcess : appProcesses) {
            if (appProcess.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                    && appProcess.processName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }
}
