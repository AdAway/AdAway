package org.adaway.util;


import android.content.Context;

import org.sufficientlysecure.rootcommands.Shell;
import org.sufficientlysecure.rootcommands.Toolbox;
import org.sufficientlysecure.rootcommands.command.SimpleCommand;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * This class provides methods to check, install and remove systemless script.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class SystemlessUtils {

    /**
     * Check if systemless mode is enabled.
     *
     * @param shell She current root shell to check
     * @return The systemless mode installation status (<code>true</code> if enabled,
     * <code>false</code> otherwise).
     */
    public static boolean isSystemlessModeEnabled(Shell shell) {
        try {
            Toolbox toolbox = new Toolbox(shell);
            // Check if systemless script is present
            return toolbox.fileExists(Constants.ANDROID_SYSTEMLESS_SCRIPT);
        } catch (Exception exception) {
            Log.e(Constants.TAG, "Error while checking if systemless mode is installed.", exception);
            // Consider systemless mode is not installed if script could not be checked
            return false;
        }
    }

    /**
     * Install systemless script.<br>
     * Create <code>/su/su.d/0000adaway.script</code> file to mount hosts file to
     * <code>/su/etc/hosts</code> location and copy current hosts file to mounted hosts file.<br>
     * Require SuperSU >= 2.76.
     *
     * @param context The application context (current activity).
     * @param shell   The current root shell to install script.
     * @return The script installation status (<code>true</code> if the systemless script is installed,
     * <code>false</code> otherwise).
     */
    public static boolean enableSystemlessMode(Context context, Shell shell) {
        try {
            Toolbox toolbox = new Toolbox(shell);
            // Check if systemless mode is already enabled
            if (!SystemlessUtils.isSystemlessModeEnabled(shell)) {
                // Create temp file
                File cacheDir = context.getCacheDir();
                File tempFile = File.createTempFile(Constants.TAG, ".script", cacheDir);
                // Write script content to temp file
                BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));
                writer.write("mount -o bind " + Constants.ANDROID_SU_ETC_HOSTS + " " + Constants.ANDROID_SYSTEM_ETC_HOSTS + ";");
                writer.newLine();
                writer.close();
                // Copy temp file to /su partition
                toolbox.copyFile(tempFile.getAbsolutePath(), Constants.ANDROID_SYSTEMLESS_SCRIPT, false, false);
                // Apply script permissions
                toolbox.setFilePermissions(Constants.ANDROID_SYSTEMLESS_SCRIPT, "755");
                // Remove temp file
                tempFile.delete();
            }
            // Check if mounted hosts file exists
            if (!toolbox.fileExists(Constants.ANDROID_SU_ETC_HOSTS)) {
                // Copy current hosts file to mounted host file
                toolbox.copyFile(Constants.ANDROID_SYSTEM_ETC_HOSTS, Constants.ANDROID_SU_ETC_HOSTS, false, true);
            }
            return true;
        } catch (Exception exception) {
            Log.e(Constants.TAG, "Error while enabling systemless mode.", exception);
            return false;
        }
    }

    /**
     * Remove systemless script.<br>
     * Remove <code>/su/su.d/0000adaway.script</code> file.
     *
     * @param shell The current root shell to install script.
     * @return The script removal status (<code>true</code> if the systemless script is removed,
     * <code>false</code> otherwise).
     */
    public static boolean disableSystemlessMode(Shell shell) {
        try {
            // Check if systemless mode is enabled
            if (SystemlessUtils.isSystemlessModeEnabled(shell)) {
                // Remove systemless script
                SimpleCommand command = new SimpleCommand(Constants.COMMAND_RM + " " + Constants.ANDROID_SYSTEMLESS_SCRIPT);
                shell.add(command).waitForFinish();
            }
            return true;
        } catch (Exception exception) {
            Log.e(Constants.TAG, "Error while disabling systemless mode.", exception);
            return false;
        }
    }
}
