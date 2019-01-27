package org.adaway.util.systemless;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.sufficientlysecure.rootcommands.Shell;
import org.sufficientlysecure.rootcommands.Toolbox;
import org.sufficientlysecure.rootcommands.command.SimpleCommand;

import java.io.IOException;

/**
 * This class provides methods to install and remove systemless mode for phh's SuperUser root.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class SuperUserSystemlessMode extends AbstractSystemlessMode {
    /**
     * Get the hosts file path.<br/>
     * Due to phh's SuperUser security, the file should be accessible by the user which declare bind mount.<br/>
     * So the file cannot be in /data or /data/data.
     *
     * @param context The application context.
     * @return The hosts file absolute path.
     */
    @NonNull
    private static String getHostsPath(Context context) {
        return context.getFileStreamPath("systemless-" + Constants.HOSTS_FILENAME).getAbsolutePath();
    }

    /**
     * Check if preferences are applied for systemless mode.
     *
     * @param context The application context.
     * @return <code>true</code> if preferences are set, <code>false</code> otherwise.
     */
    private static boolean isPreferencesApplied(Context context) {
        return PreferenceHelper.getApplyMethod(context).equals("customTarget") &&
                PreferenceHelper.getCustomTarget(context).equals(SuperUserSystemlessMode.getHostsPath(context));
    }

    /**
     * Reset preferences for systemless mode.
     *
     * @param context The application context.
     * @return <code>true</code> if the preferences are successfully reset, <code>false</code> otherwise.
     */
    private static boolean resetPreferences(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor preferencesEditor = prefs.edit();
        preferencesEditor.putString(context.getString(R.string.pref_apply_method_key), context.getString(R.string.pref_apply_method_def));
        preferencesEditor.putString(context.getString(R.string.pref_custom_target_key), context.getString(R.string.pref_custom_target_def));
        return preferencesEditor.commit();
    }

    /**
     * Apply preferences for systemless mode.
     *
     * @param context The application context.
     * @return <code>true</code> if the preferences are successfully applied, <code>false</code> otherwise.
     */
    private static boolean applyPreferences(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor preferencesEditor = prefs.edit();
        preferencesEditor.putString(context.getString(R.string.pref_apply_method_key), "customTarget");
        preferencesEditor.putString(context.getString(R.string.pref_custom_target_key), SuperUserSystemlessMode.getHostsPath(context));
        return preferencesEditor.commit();
    }

    /**
     * Check if the system hosts file is bind mounted.
     *
     * @param shell The shell to check if systemless mode is enabled.
     * @return <code>true</code> if the system hosts file is bind mounted, <code>false</code> otherwise.
     * @throws Exception if the check could not be done.
     */
    private static boolean isHostBindMounted(Shell shell) throws Exception {
        // WARNING: This command outputs on error stream.
        SimpleCommand suBindListCommand = new SimpleCommand("su --bind --list 2>&1 | grep " + Constants.ANDROID_SYSTEM_ETC_HOSTS);
        shell.add(suBindListCommand).waitForFinish();
        return suBindListCommand.getExitCode() == 0;
    }

    @Override
    boolean isEnabled(Context context, Shell shell) throws Exception {
        return SuperUserSystemlessMode.isHostBindMounted(shell) && SuperUserSystemlessMode.isPreferencesApplied(context);
    }

    @Override
    public boolean enable(Context context) {
        // Declare shell
        Shell shell = null;
        try {
            // Start shell
            shell = Shell.startShell();
            Toolbox toolbox = new Toolbox(shell);
            // Get hosts path
            String hostsPath = SuperUserSystemlessMode.getHostsPath(context);
            // Ensure mounted hosts file exists
            if (!toolbox.fileExists(hostsPath)) {
                // Copy current hosts file to mounted host file
                if (!toolbox.copyFile(Constants.ANDROID_SYSTEM_ETC_HOSTS, hostsPath, false, true)) {
                    Log.w(Constants.TAG, "Could not copy hosts file to " + hostsPath + ".");
                    return false;
                }
            }
            // Check if systemless mode is already enabled
            if (this.isEnabled(context, shell)) {
                return true;
            }
            // Check if the system hosts file is already bind mounted
            if (!SuperUserSystemlessMode.isHostBindMounted(shell)) {
                // Execute su bind command
                SimpleCommand suBindCommand = new SimpleCommand("su --bind " + hostsPath + ":" + Constants.ANDROID_SYSTEM_ETC_HOSTS);
                shell.add(suBindCommand).waitForFinish();
                if (suBindCommand.getExitCode() != 0) {
                    Log.w(Constants.TAG, "Could not execute the su bind command.");
                    return false;
                }
            }
            // Check if the preferences are already applied
            if (!SuperUserSystemlessMode.isPreferencesApplied(context)) {
                // Apply preferences
                if (!SuperUserSystemlessMode.applyPreferences(context)) {
                    Log.w(Constants.TAG, "Could not apply preferences.");
                    return false;
                }
            }
            // Systemless mode is activated
            return true;
        } catch (Exception exception) {
            Log.e(Constants.TAG, "Error while enabling systemless mode.", exception);
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

    @Override
    public boolean disable(Context context) {
        // Declare shell
        Shell shell = null;
        try {
            // Start shell
            shell = Shell.startShell();
            // Check if systemless mode is enabled
            if (!this.isEnabled(context, shell)) {
                return true;
            }
            // Get hosts path
            String hostsPath = SuperUserSystemlessMode.getHostsPath(context);
            // Execute su bind command
            SimpleCommand suBindCommand = new SimpleCommand("su --bind !" + hostsPath + ":" + Constants.ANDROID_SYSTEM_ETC_HOSTS);
            shell.add(suBindCommand).waitForFinish();
            if (suBindCommand.getExitCode() != 0) {
                Log.w(Constants.TAG, "Could not execute the su bind suBindCommand.");
                return false;
            }
            // Reset preferences
            if (!SuperUserSystemlessMode.resetPreferences(context)) {
                Log.w(Constants.TAG, "Could not reset preferences.");
            }
            // Remove hosts file
            SimpleCommand removeHostsCommand = new SimpleCommand(Constants.COMMAND_RM + " " + hostsPath);
            shell.add(removeHostsCommand).waitForFinish();
            if (removeHostsCommand.getExitCode() != 0) {
                Log.w(Constants.TAG, "Could not remote the bound hosts file.");
            }
            // Return successfully disabled
            return true;
        } catch (Exception exception) {
            Log.e(Constants.TAG, "Error while disabling systemless mode.", exception);
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
}
