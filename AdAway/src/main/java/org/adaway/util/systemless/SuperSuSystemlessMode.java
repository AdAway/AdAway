package org.adaway.util.systemless;

import android.content.Context;

import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.sufficientlysecure.rootcommands.Shell;
import org.sufficientlysecure.rootcommands.Toolbox;
import org.sufficientlysecure.rootcommands.command.SimpleCommand;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * This class provides methods to install and remove systemless mode for ChainFire's SuperSU root.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class SuperSuSystemlessMode extends AbstractSystemlessMode {
    @Override
    boolean isEnabled(Context context, Shell shell) throws Exception {
        // Look for mount point of system hosts file
        SimpleCommand command = new SimpleCommand("mount | grep " + Constants.ANDROID_SYSTEM_ETC_HOSTS);
        shell.add(command).waitForFinish();
        return command.getExitCode() == 0;
    }

    /**
     * Install systemless script.<br>
     * Create and execute<code>/su/su.d/0000adaway.script</code> file to mount hosts file to
     * <code>/su/etc/hosts</code> location and ensure mounted hosts file is present.<br>
     * Require SuperSU >= 2.56.
     *
     * @param context The application context (current activity).
     * @return The script installation status (<code>true</code> if the systemless script is installed,
     * <code>false</code> otherwise).
     */
    @Override
    public boolean enable(Context context) {
        // Declare root shell
        Shell shell = null;
        try {
            // Start root shell
            shell = Shell.startRootShell();
            Toolbox toolbox = new Toolbox(shell);
            // Ensure mounted hosts file exists
            if (!toolbox.fileExists(Constants.ANDROID_SU_ETC_HOSTS)) {
                // Copy current hosts file to mounted host file
                if (!toolbox.copyFile(Constants.ANDROID_SYSTEM_ETC_HOSTS, Constants.ANDROID_SU_ETC_HOSTS, false, true)) {
                    Log.w(Constants.TAG, "Could not copy hosts file to " + Constants.ANDROID_SU_ETC_HOSTS + ".");
                    return false;
                }
            }
            // Check if systemless mode is already enabled
            if (this.isEnabled(context, shell)) {
                return true;
            }
            /*
             * Install systemless script by writing content to a temporary file, copying to su hook location then running it.
             */
            // Create temp file
            File cacheDir = context.getCacheDir();
            File tempFile = File.createTempFile(Constants.TAG, ".script", cacheDir);
            // Write script content to temp file
            BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));
            writer.write("mount -o bind " + Constants.ANDROID_SU_ETC_HOSTS + " " + Constants.ANDROID_SYSTEM_ETC_HOSTS + ";");
            writer.newLine();
            writer.close();
            // Copy temp file to /su partition
            if (!toolbox.copyFile(tempFile.getAbsolutePath(), Constants.ANDROID_SYSTEMLESS_SCRIPT, false, false)) {
                Log.w(Constants.TAG, "Could not copy the systemless script to " + Constants.ANDROID_SYSTEMLESS_SCRIPT + ".");
                return false;
            }
            // Apply script permissions
            if (!toolbox.setFilePermissions(Constants.ANDROID_SYSTEMLESS_SCRIPT, "755")) {
                Log.w(Constants.TAG, "Could not set systemless script rights.");
                return false;
            }
            // Remove temp file
            if (!tempFile.delete()) {
                Log.i(Constants.TAG, "Could not delete the temporary script file.");
            }
            // Execute script
            SimpleCommand command = new SimpleCommand(Constants.ANDROID_SYSTEMLESS_SCRIPT);
            shell.add(command).waitForFinish();
            if (command.getExitCode() != 0) {
                Log.w(Constants.TAG, "Could not execute the systemless script.");
                return false;
            }
            // Check if installation is successful
            if (!this.isEnabled(context, shell)) {
                Log.w(Constants.TAG, "Systemless mode installation was successful but systemless is not working.");
                return false;
            }
            // Systemless mode is installed and working
            return true;
        } catch (Exception exception) {
            Log.e(Constants.TAG, "Error while enabling systemless mode.", exception);
            return false;
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

    /**
     * Remove systemless script.<br>
     * Remove <code>/su/su.d/0000adaway.script</code> and mounted hosts files.
     *
     * @param context The application context (current activity).
     * @return The script removal status (<code>true</code> if the systemless script is removed,
     * <code>false</code> otherwise).
     */
    @Override
    public boolean disable(Context context) {
        // Declare root shell
        Shell shell = null;
        try {
            // Start root shell
            shell = Shell.startRootShell();
            // Check if systemless mode is enabled
            if (!this.isEnabled(context, shell)) {
                return true;
            }
            // Remove systemless script
            SimpleCommand removeScriptCommand =
                    new SimpleCommand(Constants.COMMAND_RM + " " + Constants.ANDROID_SYSTEMLESS_SCRIPT);
            shell.add(removeScriptCommand).waitForFinish();
            if (removeScriptCommand.getExitCode() != 0) {
                Log.w(Constants.TAG, "Couldn't remove systemless script.");
                return false;
            }
            // Try to umount hosts file (resource must be used: it requires reboot)
            SimpleCommand umountCommand =
                    new SimpleCommand("umount " + Constants.ANDROID_SYSTEM_ETC_HOSTS);
            shell.add(umountCommand).waitForFinish();
            // Remove mounted hosts file
            SimpleCommand removeMountedHostsCommand =
                    new SimpleCommand(Constants.COMMAND_RM + " " + Constants.ANDROID_SU_ETC_HOSTS);
            shell.add(removeMountedHostsCommand).waitForFinish();
            // Return successfully removed
            return true;
        } catch (Exception exception) {
            Log.e(Constants.TAG, "Error while disabling systemless mode.", exception);
            return false;
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

    @Override
    public boolean isRebootNeededAfterActivation() {
        return false;
    }
}
