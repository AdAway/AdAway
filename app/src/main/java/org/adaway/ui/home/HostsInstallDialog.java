package org.adaway.ui.home;

import android.content.Context;
import android.content.Intent;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.error.HostError;
import org.adaway.ui.help.HelpActivity;
import org.adaway.util.Utils;

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
    static void showDialogBasedOnResult(Context context, HostError installError) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(R.string.button_close, (dialog, id) -> dialog.dismiss())
                .setNegativeButton(R.string.button_help, (dialog, id) -> {
                    dialog.dismiss();
                    // go to help
                    context.startActivity(new Intent(context, HelpActivity.class));
                });

        int title;
        int text;
        switch (installError) {
            case NO_CONNECTION:
                title = R.string.no_connection_title;
                text = R.string.no_connection;
                break;
            case DOWNLOAD_FAILED:
                title = R.string.download_fail_title;
                text = R.string.download_fail_dialog;
                break;
            case PRIVATE_FILE_FAILED:
                title = R.string.apply_private_file_fail_title;
                text = R.string.apply_private_file_fail;
                break;
            case NOT_ENOUGH_SPACE:
                title = R.string.apply_not_enough_space_title;
                text = R.string.apply_not_enough_space;
                break;
            case COPY_FAIL:
                title = R.string.apply_copy_fail_title;
                text = R.string.apply_copy_fail;
                break;
            case REVERT_FAIL:
                title = R.string.revert_problem_title;
                text = R.string.revert_problem;
                break;
            default:
                throw new IllegalStateException("Error code " + installError + " not supported.");
        }
        builder.setTitle(title);
        builder.setMessage(context.getString(text) + "\n\n" + context.getString(R.string.apply_help));
        builder.show();
    }
}
