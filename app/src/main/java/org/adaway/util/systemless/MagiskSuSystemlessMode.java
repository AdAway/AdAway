package org.adaway.util.systemless;

import android.content.Context;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.adaway.R;
import org.sufficientlysecure.rootcommands.Shell;
import org.sufficientlysecure.rootcommands.command.SimpleCommand;

/**
 * This class provides methods to systemless mode Magisk module.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class MagiskSuSystemlessMode extends AbstractSystemlessMode {
    /**
     * The Magisk systemless hosts module location
     */
    private static final String HOSTS_MODULE_PATH = "/data/adb/modules/hosts";

    @Override
    boolean isEnabled(Context context, Shell shell) throws Exception {
        // Look for mount point of system hosts file
        SimpleCommand command = new SimpleCommand(
                "su -c test -d " + HOSTS_MODULE_PATH,
                "mount | grep /system/etc/hosts"
        );
        shell.add(command).waitForFinish();
        return command.getExitCode() == 0;
    }

    @Override
    public boolean enable(Context context) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.enable_systemless_magisk_title)
                .setMessage(R.string.enable_systemless_magisk)
                .setNeutralButton(R.string.button_close, (d, which) -> d.dismiss())
                .create()
                .show();
        return false;
    }

    @Override
    public boolean disable(Context context) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.disable_systemless_magisk_title)
                .setMessage(R.string.disable_systemless_magisk)
                .setNeutralButton(R.string.button_close, (d, which) -> d.dismiss())
                .create()
                .show();
        return false;
    }

    @Override
    public boolean isRebootNeededAfterActivation() {
        return false;
    }

    @Override
    public boolean isRebootNeededAfterDeactivation() {
        return false;
    }
}
