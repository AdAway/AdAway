package org.adaway.ui.home;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.ui.help.HelpActivity;
import org.adaway.model.hostsinstall.HostsInstallError;
import org.adaway.util.Utils;
import org.adaway.model.hostsinstall.HostsInstallException;
import org.adaway.model.hostsinstall.HostsInstallModel;
import org.adaway.model.hostsinstall.HostsInstallStatus;

/**
 * This class is an helper class to show hosts install error related dialogs.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
final class HostsInstallDialog {
    /**
     * Private constructor.
     */
    private HostsInstallDialog() {

    }

    /**
     * Show a reboot dialog after installing host file.
     *
     * @param context The application context.
     * @param status  The current hosts status.
     */
    static void showRebootDialog(Context context, HostsInstallStatus status) {
        // Check reboot dialog preference
        if (PreferenceHelper.getNeverReboot(context)) {
            return;
        }
        switch (status) {
            case INSTALLED:
                Utils.rebootQuestion(context, R.string.apply_success_title, R.string.apply_success_dialog);
                break;
            case ORIGINAL:
                Utils.rebootQuestion(context, R.string.revert_successful_title, R.string.revert_successful);
                break;
            default:
                // Nothing to do
                break;
        }
    }

    /**
     * Shows dialog and further information how to proceed after the applying process has ended and
     * the user clicked on the notification. This is based on the result from the apply process.
     *
     * @param installError The install error to show dialog.
     */
    static void showDialogBasedOnResult(Context context, HostsInstallError installError) {
        if (installError == HostsInstallError.SYMLINK_MISSING) {
            showSymlinkDialog(context);
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setPositiveButton(
                context.getString(R.string.button_close),
                (dialog, id) -> dialog.dismiss()
        );
        builder.setNegativeButton(
                context.getString(R.string.button_help),
                (dialog, id) -> {
                    dialog.dismiss();
                    // go to help
                    context.startActivity(new Intent(context, HelpActivity.class));
                }
        );

        int title;
        int text;
        switch (installError) {
            case NO_CONNECTION:
                title = R.string.no_connection_title;
                text = R.string.no_connection;
                break;
            case DOWNLOAD_FAIL:
                title = R.string.download_fail_title;
                text = R.string.download_fail_dialog;
                break;
            case APN_PROXY:
                title = R.string.apply_apn_proxy_title;
                text = R.string.apply_apn_proxy;
                break;
            case APPLY_FAIL:
                title = R.string.apply_fail_title;
                text = R.string.apply_fail;
                break;
            case PRIVATE_FILE_FAIL:
                title = R.string.apply_private_file_fail_title;
                text = R.string.apply_private_file_fail;
                break;
            case NOT_ENOUGH_SPACE:
                title = R.string.apply_not_enough_space_title;
                text = R.string.apply_not_enough_space;
                break;
            case REMOUNT_FAIL:
                title = R.string.apply_remount_fail_title;
                text = R.string.apply_remount_fail;
                break;
            case COPY_FAIL:
                title = R.string.apply_copy_fail_title;
                text = R.string.apply_copy_fail;
                break;
            case REVERT_FAIL:
                title = R.string.revert_problem_title;
                text = R.string.revert_problem;
                break;
            case SYMLINK_FAILED:
                title = R.string.apply_symlink_fail_title;
                text = R.string.apply_symlink_fail;
                break;

            default:
                throw new IllegalStateException("Error code " + installError + " not supported.");
        }
        builder.setTitle(title);
        builder.setMessage(context.getString(text) + "\n\n" + context.getString(R.string.apply_help));

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    /**
     * Show dialog to notify missing symlink dialog.
     *
     * @param context The application context.
     */
    private static void showSymlinkDialog(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.apply_symlink_missing_title);
        builder.setMessage(R.string.apply_symlink_missing);
        builder.setIcon(android.R.drawable.ic_dialog_info);
        builder.setPositiveButton(
                context.getString(R.string.button_yes),
                (dialog, id) -> {
                    dialog.dismiss();
                    HostsInstallDialog.tryToCreateSymlink(context);
                }
        );
        builder.setNegativeButton(
                context.getString(R.string.button_no),
                (dialog, id) -> dialog.dismiss()
        );
        AlertDialog question = builder.create();
        question.show();
    }

    /**
     * Trying to create symlink and displays dialogs on fail.
     *
     * @param context The application context.
     */
    private static void tryToCreateSymlink(Context context) {
        try {
            HostsInstallModel model = new HostsInstallModel(context);
            model.createSymlink();
            if (!PreferenceHelper.getNeverReboot(context)) {
                Utils.rebootQuestion(context, R.string.apply_symlink_successful_title, R.string.apply_symlink_successful);
            }
        } catch (HostsInstallException exception) {
            HostsInstallDialog.showDialogBasedOnResult(context, HostsInstallError.SYMLINK_FAILED);
        }
    }
}
