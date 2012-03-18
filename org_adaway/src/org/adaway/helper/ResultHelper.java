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

import org.adaway.R;
import org.adaway.ui.BaseActivity;
import org.adaway.ui.HelpActivity;
import org.adaway.util.ApplyUtils;
import org.adaway.util.CommandException;
import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.adaway.util.RemountException;
import org.adaway.util.StatusCodes;
import org.adaway.util.Utils;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

public class ResultHelper {

    private static final int RESULT_NOTIFICATION_ID = 30;

    /**
     * Show notification based on result after ApplyService or RevertService
     * 
     * @param context
     * @param result
     * @param failingUrl
     */
    public static void showNotificationBasedOnResult(Context context, int result, String failingUrl) {
        if (result == StatusCodes.SUCCESS) {
            String title = context.getString(R.string.apply_success_title);
            String text = context.getString(R.string.apply_success);

            BaseActivity.setStatusBroadcast(context, title, text, StatusCodes.ENABLED);

            // only show if reboot dialog is not disabled in preferences
            if (!PreferencesHelper.getNeverReboot(context)) {
                processResult(context, title, text, text, result, StatusCodes.ENABLED, null, true);
            }
        } else if (result == StatusCodes.REVERT_SUCCESS) {
            String title = context.getString(R.string.revert_successful_title);
            String text = context.getString(R.string.revert_successful);

            BaseActivity.setStatusBroadcast(context, title, text, StatusCodes.DISABLED);

            // only show if reboot dialog is not disabled in preferences
            if (!PreferencesHelper.getNeverReboot(context)) {
                processResult(context, title, text, text, result, StatusCodes.DISABLED, null, true);
            }
        } else if (result == StatusCodes.REVERT_FAIL) {
            String title = context.getString(R.string.revert_problem_title);
            String text = context.getString(R.string.revert_problem);

            // back to old status
            int oldStatus;
            if (ApplyUtils.isHostsFileCorrect(context, Constants.ANDROID_SYSTEM_ETC_HOSTS)) {
                oldStatus = StatusCodes.ENABLED;
            } else {
                oldStatus = StatusCodes.DISABLED;
            }

            processResult(context, title, text, text, result, oldStatus, null, false);
        } else if (result == StatusCodes.UPDATE_AVAILABLE) { // used from UpdateService
            String title = context.getString(R.string.status_update_available);
            String text = context.getString(R.string.status_update_available_subtitle);

            processResult(context, title, text, text, result, StatusCodes.UPDATE_AVAILABLE, null,
                    false);
        } else if (result == StatusCodes.APN_PROXY) { // used from ApplyService
            String title = context.getString(R.string.apply_apn_proxy_title);
            String text = context.getString(R.string.apply_apn_proxy);

            processResult(context, title, text, text, result, StatusCodes.ENABLED, null, true);
        } else if (result == StatusCodes.DOWNLOAD_FAIL) { // used from UpdateService and
                                                          // ApplyService
            String title = context.getString(R.string.download_fail_title);
            String text = context.getString(R.string.download_fail);
            String statusText = context.getString(R.string.status_download_fail_subtitle);

            processResult(context, title, text, statusText, result, StatusCodes.DOWNLOAD_FAIL,
                    failingUrl, true);
        } else if (result == StatusCodes.NO_CONNECTION) { // used from UpdateService and
                                                          // ApplyService
            String title = context.getString(R.string.no_connection_title);
            String text = context.getString(R.string.no_connection);
            String statusText = context.getString(R.string.status_no_connection_subtitle);

            processResult(context, title, text, statusText, result, StatusCodes.DOWNLOAD_FAIL,
                    null, false);
        } else if (result == StatusCodes.ENABLED) { // used from UpdateService
            BaseActivity.updateStatusEnabled(context);
        } else if (result == StatusCodes.DISABLED) { // used from UpdateService
            BaseActivity.updateStatusDisabled(context);
        } else {
            String title = "";
            String text = "";
            switch (result) {
            case StatusCodes.SYMLINK_MISSING:
                title = context.getString(R.string.apply_symlink_missing_title);
                text = context.getString(R.string.apply_symlink_missing);
                break;
            case StatusCodes.EMPTY_HOSTS_SOURCES:
                title = context.getString(R.string.no_sources_title);
                text = context.getString(R.string.no_sources);
                break;
            case StatusCodes.APPLY_FAIL:
                title = context.getString(R.string.apply_fail_title);
                text = context.getString(R.string.apply_fail);
                break;
            case StatusCodes.PRIVATE_FILE_FAIL:
                title = context.getString(R.string.apply_private_file_fail_title);
                text = context.getString(R.string.apply_private_file_fail);
                break;
            case StatusCodes.NOT_ENOUGH_SPACE:
                title = context.getString(R.string.apply_not_enough_space_title);
                text = context.getString(R.string.apply_not_enough_space);
                break;
            case StatusCodes.REMOUNT_FAIL:
                title = context.getString(R.string.apply_remount_fail_title);
                text = context.getString(R.string.apply_remount_fail);
                break;
            case StatusCodes.COPY_FAIL:
                title = context.getString(R.string.apply_copy_fail_title);
                text = context.getString(R.string.apply_copy_fail);
                break;
            }

            processResult(context, title, text, text, result, StatusCodes.DISABLED, null, true);
        }
    }

    /**
     * Shows dialog and further information how to proceed after the applying process has ended and
     * the user clicked on the notification. This is based on the result from the apply process.
     * 
     * @param result
     */
    public static void showDialogBasedOnResult(final Context context, int result, String failingUrl) {
        if (result == StatusCodes.SUCCESS) {
            BaseActivity.updateStatusEnabled(context);

            Utils.rebootQuestion(context, R.string.apply_success_title, R.string.apply_success);
        } else if (result == StatusCodes.REVERT_SUCCESS) {
            BaseActivity.updateStatusDisabled(context);

            Utils.rebootQuestion(context, R.string.revert_successful_title,
                    R.string.revert_successful);
        } else if (result == StatusCodes.ENABLED) {
            BaseActivity.updateStatusEnabled(context);
        } else if (result == StatusCodes.DISABLED) {
            BaseActivity.updateStatusDisabled(context);
        } else if (result == StatusCodes.UPDATE_AVAILABLE) {
            String title = context.getString(R.string.status_update_available);
            String text = context.getString(R.string.status_update_available_subtitle);

            BaseActivity.setStatusBroadcast(context, title, text, StatusCodes.UPDATE_AVAILABLE);
        } else if (result == StatusCodes.SYMLINK_MISSING) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(R.string.apply_symlink_missing_title);
            builder.setMessage(context.getString(R.string.apply_symlink_missing));
            builder.setIcon(android.R.drawable.ic_dialog_info);
            builder.setPositiveButton(context.getString(R.string.button_yes),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            tryToCreateSymlink(context);
                        }
                    });
            builder.setNegativeButton(context.getString(R.string.button_no),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();

                            BaseActivity.updateStatusDisabled(context);
                        }
                    });
            AlertDialog question = builder.create();
            question.show();
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setPositiveButton(context.getString(R.string.button_close),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                        }
                    });
            builder.setNegativeButton(context.getString(R.string.button_help),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();

                            // go to help
                            context.startActivity(new Intent(context, HelpActivity.class));
                        }
                    });

            String title = "";
            String text = "";
            String statusText = "";
            switch (result) {
            case StatusCodes.NO_CONNECTION:
                title = context.getString(R.string.no_connection_title);
                text = context.getString(R.string.no_connection);
                statusText = context.getString(R.string.status_no_connection_subtitle);

                BaseActivity.setStatusBroadcast(context, title, statusText,
                        StatusCodes.DOWNLOAD_FAIL);
                break;
            case StatusCodes.DOWNLOAD_FAIL:
                title = context.getString(R.string.download_fail_title);
                if (failingUrl != null) {
                    text = context.getString(R.string.download_fail) + "\n" + failingUrl;
                } else {
                    text = context.getString(R.string.download_fail);
                }
                statusText = context.getString(R.string.status_download_fail_subtitle) + " "
                        + failingUrl;

                BaseActivity.setStatusBroadcast(context, title, statusText,
                        StatusCodes.DOWNLOAD_FAIL);
                break;
            case StatusCodes.APN_PROXY:
                title = context.getString(R.string.apply_apn_proxy_title);
                text = context.getString(R.string.apply_apn_proxy);

                BaseActivity.updateStatusEnabled(context);
                break;
            case StatusCodes.EMPTY_HOSTS_SOURCES:
                title = context.getString(R.string.no_sources_title);
                text = context.getString(R.string.no_sources);

                BaseActivity.updateStatusDisabled(context);
                break;
            case StatusCodes.APPLY_FAIL:
                title = context.getString(R.string.apply_fail_title);
                text = context.getString(R.string.apply_fail);

                BaseActivity.updateStatusDisabled(context);
                break;
            case StatusCodes.PRIVATE_FILE_FAIL:
                title = context.getString(R.string.apply_private_file_fail_title);
                text = context.getString(R.string.apply_private_file_fail);

                BaseActivity.updateStatusDisabled(context);
                break;
            case StatusCodes.NOT_ENOUGH_SPACE:
                title = context.getString(R.string.apply_not_enough_space_title);
                text = context.getString(R.string.apply_not_enough_space);

                BaseActivity.updateStatusDisabled(context);
                break;
            case StatusCodes.REMOUNT_FAIL:
                title = context.getString(R.string.apply_remount_fail_title);
                text = context.getString(R.string.apply_remount_fail);

                BaseActivity.updateStatusDisabled(context);
                break;
            case StatusCodes.COPY_FAIL:
                title = context.getString(R.string.apply_copy_fail_title);
                text = context.getString(R.string.apply_copy_fail);

                BaseActivity.updateStatusDisabled(context);
                break;
            case StatusCodes.REVERT_FAIL:
                title = context.getString(R.string.revert_problem_title);
                text = context.getString(R.string.revert_problem);

                // back to old status
                if (ApplyUtils.isHostsFileCorrect(context, Constants.ANDROID_SYSTEM_ETC_HOSTS)) {
                    BaseActivity.updateStatusEnabled(context);
                } else {
                    BaseActivity.updateStatusDisabled(context);
                }
                break;
            }
            text += "\n\n" + context.getString(R.string.apply_help);
            builder.setTitle(title);
            builder.setMessage(text);

            AlertDialog alertDialog = builder.create();
            alertDialog.show();
        }
    }

    /**
     * Private helper used in showNotificationBasedOnResult
     * 
     * @param context
     * @param title
     * @param text
     * @param statusText
     * @param result
     * @param iconStatus
     * @param failingUrl
     * @param showDialog
     */
    private static void processResult(Context context, String title, String text,
            String statusText, int result, int iconStatus, String failingUrl, boolean showDialog) {
        if (Utils.isInForeground(context)) {
            if (showDialog) {
                // start BaseActivity with result
                Intent resultIntent = new Intent(context, BaseActivity.class);
                resultIntent.putExtra(BaseActivity.EXTRA_APPLYING_RESULT, result);
                if (failingUrl != null) {
                    resultIntent.putExtra(BaseActivity.EXTRA_FAILING_URL, failingUrl);
                }
                resultIntent.addFlags(Intent.FLAG_FROM_BACKGROUND);
                resultIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(resultIntent);
            }
        } else {
            // show notification
            showResultNotification(context, title, text, result, failingUrl);
        }

        if (failingUrl != null) {
            BaseActivity.setStatusBroadcast(context, title, statusText + " " + failingUrl,
                    iconStatus);
        } else {
            BaseActivity.setStatusBroadcast(context, title, statusText, iconStatus);
        }
    }

    /**
     * Show notification with result defined in params
     * 
     * @param contentTitle
     * @param contentText
     */
    private static void showResultNotification(Context context, String contentTitle,
            String contentText, int applyingResult, String failingUrl) {
        NotificationManager notificationManager = (NotificationManager) context
                .getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

        int icon = R.drawable.status_bar_icon;
        long when = System.currentTimeMillis();

        // add app name to title
        contentTitle = context.getString(R.string.app_name) + ": " + contentTitle;

        Notification notification = new Notification(icon, contentTitle, when);
        notification.flags = Notification.FLAG_AUTO_CANCEL;

        Intent notificationIntent = new Intent(context, BaseActivity.class);

        // give postApplyingStatus with intent
        notificationIntent.putExtra(BaseActivity.EXTRA_APPLYING_RESULT, applyingResult);
        notificationIntent.putExtra(BaseActivity.EXTRA_FAILING_URL, failingUrl);

        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);

        notificationManager.notify(RESULT_NOTIFICATION_ID, notification);
    }

    /**
     * Trying to create symlink and displays dialogs on fail
     */
    private static void tryToCreateSymlink(final Context context) {
        boolean success = true;

        try {
            // symlink to /system/etc/hosts, based on target
            if (PreferencesHelper.getApplyMethod(context).equals("writeToDataData")) {
                ApplyUtils.createSymlink(Constants.ANDROID_DATA_DATA_HOSTS);
            } else if (PreferencesHelper.getApplyMethod(context).equals("customTarget")) {
                ApplyUtils.createSymlink(PreferencesHelper.getCustomTarget(context));
            }
        } catch (CommandException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();

            success = false;
        } catch (RemountException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();

            success = false;
        }

        if (success) {
            if (ApplyUtils.isHostsFileCorrect(context, Constants.ANDROID_SYSTEM_ETC_HOSTS)) {
                success = true;
            } else {
                success = false;
            }
        }

        if (success) {
            BaseActivity.updateStatusEnabled(context);

            Utils.rebootQuestion(context, R.string.apply_symlink_successful_title,
                    R.string.apply_symlink_successful);
        } else {
            BaseActivity.updateStatusDisabled(context);

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(R.string.apply_symlink_fail_title);
            builder.setMessage(context.getString(R.string.apply_symlink_fail) + "\n\n"
                    + context.getString(R.string.apply_help));
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setPositiveButton(context.getString(R.string.button_close),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                        }
                    });
            builder.setNegativeButton(context.getString(R.string.button_help),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();

                            // go to help
                            context.startActivity(new Intent(context, HelpActivity.class));
                        }
                    });
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
        }
    }

}
