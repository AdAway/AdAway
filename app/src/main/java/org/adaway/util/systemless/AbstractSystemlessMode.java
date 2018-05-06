package org.adaway.util.systemless;

import android.content.Context;

import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.sufficientlysecure.rootcommands.Shell;

import java.io.IOException;

/**
 * This class provides methods to check if enabled, enable and disable systemless mode.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public abstract class AbstractSystemlessMode {
    /**
     * Check if the systemless mode is supported.
     *
     * @return <code>true</code> if systemless mode is supported, <code>false</code> otherwise.
     */
    public boolean isSupported() {
        return true;
    }

    /**
     * Check if systemless mode is enabled.
     *
     * @param context The application context (current activity).
     * @return The systemless mode installation status (<code>true</code> if enabled,
     * <code>false</code> otherwise).
     */
    public boolean isEnabled(Context context) {
        // Declare shell
        Shell shell = null;
        try {
            // Start shell
            shell = Shell.startShell();
            // Check if enabled
            return this.isEnabled(context, shell);
        } catch (Exception exception) {
            Log.e(Constants.TAG, "Error while checking if systemless mode is installed.", exception);
            // Consider systemless mode is not installed if script could not be checked
            return false;
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
    }

    /**
     * Check if systemless mode is enabled.
     *
     * @param context The application context (current activity).
     * @param shell   The shell to check if systemless mode is enabled.
     * @return The systemless mode installation status (<code>true</code> if enabled,
     * <code>false</code> otherwise).
     * @throws Exception if the check could not be done.
     */
    abstract boolean isEnabled(Context context, Shell shell) throws Exception;

    /**
     * Enable systemless mode.
     *
     * @param context The application context (current activity).
     * @return The activation status (<code>true</code> if enabled, <code>false</code> otherwise).
     */
    public abstract boolean enable(Context context);

    /**
     * Disable systemless mode.
     *
     * @param context The application context (current activity).
     * @return The deactivation (<code>true</code> if disabled, <code>false</code> otherwise).
     */
    public abstract boolean disable(Context context);

    /**
     * Check if reboot is needed after systemless mode activation.
     *
     * @return <code>true</code> if reboot is needed after systemless activation.
     */
    public boolean isRebootNeededAfterActivation() {
        return true;
    }

    /**
     * Check if reboot is needed after systemless mode deactivation.
     *
     * @return <code>true</code> if reboot is needed after systemless deactivation.
     */
    public boolean isRebootNeededAfterDeactivation() {
        return true;
    }
}
