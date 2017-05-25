package org.adaway.util.systemless;

import android.content.Context;

import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.sufficientlysecure.rootcommands.Shell;
import org.sufficientlysecure.rootcommands.command.SimpleCommand;

import java.io.IOException;

/**
 * This class provides methods to launch Magisk settings in order to enable systemless mode.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class MagiskSuSystemlessMode extends AbstractSystemlessMode {
    /**
     * The Magisk package name.
     */
    private static final String MAGISK_PACKAGE_NAME = "com.topjohnwu.magisk";
    /**
     * The Magisk settings class name.
     */
    private static final String MAGISK_SETTINGS_CLASS_NAME = "SettingsActivity";

    @Override
    boolean isEnabled(Context context, Shell shell) throws Exception {
        // Look for mount point of system hosts file
        SimpleCommand command = new SimpleCommand("mount | grep " + Constants.ANDROID_SYSTEM_ETC_HOSTS);
        shell.add(command).waitForFinish();
        return command.getExitCode() == 0;
    }

    @Override
    public boolean enable(Context context) {
        // Start Magisk settings
        this.startMagiskSettings();
        // Check if enabled
        return this.isEnabled(context);

    }

    @Override
    public boolean disable(Context context) {
        // Start Magisk settings
        this.startMagiskSettings();
        // Check if disable
        return !this.isEnabled(context);
    }

    /**
     * Start Magisk settings.
     */
    private void startMagiskSettings() {
        /*
         * The following method is not allowed as the activity is not exported.
         *
        // Start activity to display Magisk settings
        Intent intent = new Intent();
        intent.setClassName(MagiskSuSystemlessMode.MAGISK_PACKAGE_NAME, MagiskSuSystemlessMode.MAGISK_SETTINGS_CLASS_NAME);
        context.startActivity(intent);
        *
        * So we use am start as root to start activity.
        */
        // Declare root shell
        Shell shell = null;
        try {
            // Start root shell
            shell = Shell.startRootShell();
            SimpleCommand command = new SimpleCommand("am start -n " + MagiskSuSystemlessMode.MAGISK_PACKAGE_NAME + "/." + MagiskSuSystemlessMode.MAGISK_SETTINGS_CLASS_NAME);
            shell.add(command);
        } catch (Exception exception) {
            Log.e(Constants.TAG, "Error while opening Magisk settings activity.", exception);
        } finally {
            // Close root shell
            if (shell != null) {
                try {
                    shell.close();
                } catch (IOException exception) {
                    Log.d(Constants.TAG, "Error while closing root shell.", exception);
                }
            }
        }
    }
}
