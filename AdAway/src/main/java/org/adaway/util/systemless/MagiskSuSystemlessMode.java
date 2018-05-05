package org.adaway.util.systemless;

import android.content.Context;

import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.sufficientlysecure.rootcommands.Shell;
import org.sufficientlysecure.rootcommands.command.SimpleCommand;
import org.sufficientlysecure.rootcommands.util.Utils;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * This class provides methods to launch Magisk settings in order to enable systemless mode.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class MagiskSuSystemlessMode extends AbstractSystemlessMode {
    @Override
    boolean isEnabled(Context context, Shell shell) throws Exception {
        // Look for mount point of system hosts file
        SimpleCommand command = new SimpleCommand("mount | grep " + Constants.ANDROID_SYSTEM_ETC_HOSTS);
        shell.add(command).waitForFinish();
        return command.getExitCode() == 0;
    }

    /*
     * Source: https://github.com/topjohnwu/MagiskManager/blob/b06f69573d9d035381393e04d5f393e07188110f/src/main/java/com/topjohnwu/magisk/SettingsActivity.java#L267
     */
    @Override
    public boolean enable(Context context) {
        // Declare root shell
        Shell shell = null;
        try {
            // Start root shell with "mount master" feature (to apply 'publicly' the mount)
            shell = Shell.startCustomShell(Utils.getSuPath() + " -mm");
            // Check if systemless mode is already enabled
            if (this.isEnabled(context, shell)) {
                return true;
            }
            // Copy system hosts file to Magisk path
            SimpleCommand copyCommand = new SimpleCommand("cp -af " + Constants.ANDROID_SYSTEM_ETC_HOSTS + " " + this.getMagiskHostsFile(shell));
            shell.add(copyCommand).waitForFinish();
            if (copyCommand.getExitCode() != 0) {
                Log.w(Constants.TAG, "Could not copy hosts file to Magisk path.");
                return false;
            }
            // Mount Magisk hosts file over system hosts file
            SimpleCommand mountCommand = new SimpleCommand("mount -o bind " + this.getMagiskHostsFile(shell) + " " + Constants.ANDROID_SYSTEM_ETC_HOSTS);
            shell.add(mountCommand).waitForFinish();
            if (mountCommand.getExitCode() != 0) {
                Log.w(Constants.TAG, "Could not mount Magisk hosts file to system hosts file.");
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

    /*
     * Source: https://github.com/topjohnwu/MagiskManager/blob/b06f69573d9d035381393e04d5f393e07188110f/src/main/java/com/topjohnwu/magisk/SettingsActivity.java#L271
     */
    @Override
    public boolean disable(Context context) {
        // Declare root shell
        Shell shell = null;
        try {
            // Start root shell with "mount master" feature (to apply 'publicly' the mount)
            shell = Shell.startCustomShell(Utils.getSuPath() + " -mm");
            // Check if systemless mode is enabled
            if (!this.isEnabled(context, shell)) {
                return true;
            }
            // Umount the system hosts file
            SimpleCommand umountCommand = new SimpleCommand("umount -l " + Constants.ANDROID_SYSTEM_ETC_HOSTS);
            shell.add(umountCommand).waitForFinish();
            // Remove Magisk hosts file mounted hosts file
            SimpleCommand removeCommand = new SimpleCommand(Constants.COMMAND_RM + " " + this.getMagiskHostsFile(shell));
            shell.add(removeCommand).waitForFinish();
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

    @Override
    public boolean isRebootNeededAfterDeactivation() {
        return false;
    }

    /**
     * Get Magisk path.
     * Source: https://github.com/topjohnwu/MagiskManager/blob/b06f69573d9d035381393e04d5f393e07188110f/src/main/java/com/topjohnwu/magisk/utils/Const.java#L60
     *
     * @param rootShell The root shell to get Magisk path.
     * @return The Magisk path.
     */
    private String getMagiskHostsFile(Shell rootShell) {
        return this.getMagiskPath(rootShell) + "/.core/hosts";
    }

    /**
     * Get Magisk path.
     * Source: https://github.com/topjohnwu/MagiskManager/blob/b06f69573d9d035381393e04d5f393e07188110f/src/main/java/com/topjohnwu/magisk/utils/Const.java#L45
     *
     * @param rootShell The root shell to get Magisk path.
     * @return The Magisk path.
     */
    private String getMagiskPath(Shell rootShell) {
        try {
            // Check if /sbin path exists
            String magiskSbinPath = "/sbin/.core/img";
            SimpleCommand simpleCommand = new SimpleCommand("ls " + magiskSbinPath);
            rootShell.add(simpleCommand).waitForFinish();
            if (simpleCommand.getExitCode() == 0) {
                return magiskSbinPath;
            }
            // Check if /dev path exists
            String magiskDevPath = "/dev/magisk/img";
            simpleCommand = new SimpleCommand("ls " + magiskDevPath);
            rootShell.add(simpleCommand).waitForFinish();
            if (simpleCommand.getExitCode() == 0) {
                return magiskDevPath;
            }
        } catch (TimeoutException | IOException exception) {
            Log.w(Constants.TAG, "Error while getting Magisk path.", exception);
        }
        // Return default Magisk path
        return "/magisk";
    }
}
