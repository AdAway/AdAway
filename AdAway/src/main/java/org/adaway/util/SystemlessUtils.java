package org.adaway.util;

import org.adaway.util.systemless.AbstractSystemlessMode;
import org.adaway.util.systemless.MagiskSuSystemlessMode;
import org.adaway.util.systemless.NotSupportedSystemlessMode;
import org.adaway.util.systemless.SuperSuSystemlessMode;
import org.adaway.util.systemless.SuperUserSystemlessMode;
import org.sufficientlysecure.rootcommands.Shell;
import org.sufficientlysecure.rootcommands.Toolbox;
import org.sufficientlysecure.rootcommands.command.SimpleCommand;

import java.io.IOException;

/**
 * This class provides methods to check, install and remove systemless script.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class SystemlessUtils {
    /**
     * The cached systemless mode.
     */
    private static AbstractSystemlessMode systemlessMode;

    /**
     * Get the systemless mode.
     *
     * @return The systemless mode.
     */
    public static AbstractSystemlessMode getSystemlessMode() {
        // Check cached systemless mode
        if (SystemlessUtils.systemlessMode != null) {
            return SystemlessUtils.systemlessMode;
        }
        // Declare shell
        Shell shell = null;
        try {
            // Start shell
            shell = Shell.startShell();
            // Check each supported su implementations
            if (SystemlessUtils.checkChainFireSuperSuBindSbin(shell)) {
                SystemlessUtils.systemlessMode = new SuperSuSystemlessMode(SuperSuSystemlessMode.Mode.BIND_SBIN);
            } else if (SystemlessUtils.checkChainFireSuperSuSuPartition(shell)) {
                SystemlessUtils.systemlessMode = new SuperSuSystemlessMode(SuperSuSystemlessMode.Mode.SU_PARTITION);
            } else if (SystemlessUtils.checkPhhSuperUserSuBind(shell)) {
                SystemlessUtils.systemlessMode = new SuperUserSystemlessMode();
            } else if (SystemlessUtils.checkMagiskSu(shell)) {
                SystemlessUtils.systemlessMode = new MagiskSuSystemlessMode();
            } else {
                // Otherwise not supported systemless mode
                SystemlessUtils.systemlessMode = new NotSupportedSystemlessMode();
            }
        } catch (Exception exception) {
            Log.e(Constants.TAG, "Error while getting systemless mode.", exception);
            SystemlessUtils.systemlessMode = new NotSupportedSystemlessMode();
        } finally {
            // Close shell
            if (shell != null) {
                try {
                    shell.close();
                } catch (IOException exception) {
                    Log.d(Constants.TAG, "Error while closing shell.", exception);
                }
            }
        }
        // Return found systemless mode
        return SystemlessUtils.systemlessMode;
    }

    /**
     * Check installation of ChainFire's SuperSU "/su partition" systemless root mode.
     *
     * @param shell The current shell.
     * @return <code>true</code> if the ChainFire's SuperSU "/su partition" systemless root mode is installed, <code>false</code> otherwise.
     * @throws Exception if the installation could not be checked.
     */
    private static boolean checkChainFireSuperSuSuPartition(Shell shell) throws Exception {
        // Check if a su binary is present in su partition
        Toolbox toolbox = new Toolbox(shell);
        return toolbox.fileExists("/su/bin/su");
    }

    /**
     * Check installation of ChainFire's SuperSU "bind sbin" systemless root mode.
     *
     * @param shell The current shell.
     * @return <code>true</code> if the ChainFire's SuperSU "bind sbin" systemless root mode is installed, <code>false</code> otherwise.
     * @throws Exception if the installation could not be checked.
     */
    private static boolean checkChainFireSuperSuBindSbin(Shell shell) throws Exception {
        // Check if a su binary is present in su partition
        Toolbox toolbox = new Toolbox(shell);
        return toolbox.fileExists("/sbin/supersu/supersu_is_here");
    }

    /**
     * Check installation of SuperUser su bind.
     *
     * @param shell The current shell.
     * @return <code>true</code> if the SuperUser su bind is install, <code>false</code> otherwise.
     * @throws Exception if the installation could not be checked.
     */
    private static boolean checkPhhSuperUserSuBind(Shell shell) throws Exception {
        // Check if phh's SuperUser su bind is installed
        SimpleCommand command = new SimpleCommand("su -v | grep subind");
        shell.add(command).waitForFinish();
        return command.getExitCode() == 0;
    }

    /**
     * Check installation of MagiskSU.
     *
     * @param shell The current shell.
     * @return <code>true</code> if the MagiskSU is installed, <code>false</code> otherwise.
     * @throws Exception if the installation could not be checked.
     */
    private static boolean checkMagiskSu(Shell shell) throws Exception {
        // Check if MagiskSU is installed
        SimpleCommand command = new SimpleCommand("su -v | grep MAGISKSU");
        shell.add(command).waitForFinish();
        return command.getExitCode() == 0;
    }
}
