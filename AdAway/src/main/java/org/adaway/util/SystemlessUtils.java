package org.adaway.util;

import org.adaway.util.systemless.AbstractSystemlessMode;
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
            Toolbox toolbox = new Toolbox(shell);
            // Check if ChainFire's SuperSU systemless root is installed
            if (toolbox.fileExists("/su/bin/su")) {
                SystemlessUtils.systemlessMode = new SuperSuSystemlessMode();
            } else {
                // Check if phh's SuperUser su bind is installed
                SimpleCommand command = new SimpleCommand("su -v | grep subind");
                shell.add(command).waitForFinish();
                if (command.getExitCode() == 0) {
                    SystemlessUtils.systemlessMode = new SuperUserSystemlessMode();
                } else {
                    // Check if Magisk root is installed
                    SimpleCommand command = new SimpleCommand("su -v | grep MAGISKSU");
                    shell.add(command).waitForFinish();
                    if (command.getExitCode() == 0) {
                        SystemlessUtils.systemlessMode = new SuperUserSystemlessMode();
                    } else {
                        // Otherwise not supported systemless mode
                        SystemlessUtils.systemlessMode = new NotSupportedSystemlessMode();
                    }
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
}
