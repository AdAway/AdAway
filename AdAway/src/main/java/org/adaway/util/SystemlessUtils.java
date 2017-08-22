package org.adaway.util;

import org.adaway.util.systemless.AbstractSystemlessMode;
import org.adaway.util.systemless.NotSupportedSystemlessMode;
import org.adaway.util.systemless.SuperSuSystemlessMode;
import org.adaway.util.systemless.SuperUserSystemlessMode;
import org.sufficientlysecure.rootcommands.Shell;
import org.sufficientlysecure.rootcommands.Toolbox;
import org.sufficientlysecure.rootcommands.command.SimpleCommand;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

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
            // Check if ChainFire's SuperSU systemless root is installed
            SuperSuSystemlessMode.Mode mode = SystemlessUtils.checkSuperSuSystemlessMode(shell);
            if (mode != null) {
                SystemlessUtils.systemlessMode = new SuperSuSystemlessMode(mode);
            } else {
                // Check if phh's SuperUser su bind is installed
                SimpleCommand command = new SimpleCommand("su -v | grep subind");
                shell.add(command).waitForFinish();
                if (command.getExitCode() == 0) {
                    SystemlessUtils.systemlessMode = new SuperUserSystemlessMode();
                } else {
                    // Otherwise not supported systemless mode
                    SystemlessUtils.systemlessMode = new NotSupportedSystemlessMode();
                }
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
     * Check if ChainFire's SuperSU systemless root is installed.<br>
     * Look for <code>/su/bin/su</code> binary in case of "/su partition" systemless mode and
     * for <code>/sbin/su</code> mount point in case of "bind sbin" systemless mode.
     *
     * @param shell The root shell.
     * @return The SuperSU systemless mode found, <code>null</code> if no SuperSU systemless root was found.
     * @throws IOException      If the check could be done.
     * @throws TimeoutException If the commands take too long.
     */
    private static SuperSuSystemlessMode.Mode checkSuperSuSystemlessMode(Shell shell) throws IOException, TimeoutException {
        // Check toolbox
        Toolbox toolbox = new Toolbox(shell);
        // Check if "/su partition" systemless mode is installed
        if (toolbox.fileExists("/su/bin/su")) {
            return SuperSuSystemlessMode.Mode.SU_PARTITION;
        }
        // Check if "bind sbin" systemless mode is installed
        SimpleCommand command = new SimpleCommand("mount | grep /sbin/su");
        shell.add(command).waitForFinish();
        if (command.getExitCode() == 0) {
            return SuperSuSystemlessMode.Mode.BIND_SBIN;
        }
        // No ChainFire's SuperSU systemless root was found
        return null;
    }
}
