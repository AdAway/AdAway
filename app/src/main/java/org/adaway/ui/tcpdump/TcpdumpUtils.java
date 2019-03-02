/*
 * Copyright (C) 2011-2012 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This file is part of AdAway.
 *
 * AdAway is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AdAway is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AdAway.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.adaway.ui.tcpdump;

import android.content.Context;

import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.adaway.util.RegexUtils;
import org.sufficientlysecure.rootcommands.Shell;
import org.sufficientlysecure.rootcommands.Toolbox;
import org.sufficientlysecure.rootcommands.command.SimpleCommand;
import org.sufficientlysecure.rootcommands.command.SimpleExecutableCommand;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.sentry.Sentry;

class TcpdumpUtils {
    /**
     * Private constructor.
     */
    private TcpdumpUtils() {

    }

    /**
     * Checks if tcpdump is running
     *
     * @return true if tcpdump is running
     */
    static boolean isTcpdumpRunning(Shell shell) {
        try {
            Toolbox tb = new Toolbox(shell);
            return tb.isBinaryRunning(Constants.TCPDUMP_EXECUTABLE);
        } catch (Exception e) {
            Log.e(Constants.TAG, "Exception while checking tcpdump", e);
            return false;
        }
    }

    /**
     * Start tcpdump tool.
     *
     * @param context The application context.
     * @return returns true if starting worked
     */
    static boolean startTcpdump(Context context, Shell shell) {
        Log.d(Constants.TAG, "Starting tcpdump...");
        checkSystemTcpdump(shell);

        File file = getLogFile(context);
        try {
            // Create log file before using it with tcpdump if not exists
            if (!file.exists() && !file.createNewFile()) {
                return false;
            }
        } catch (IOException e) {
            Log.e(Constants.TAG, "Problem while getting cache directory!", e);
            return false;
        }

        // "-i any": listen on any network interface
        // "-p": disable promiscuous mode (doesn't work anyway)
        // "-l": Make stdout line buffered. Useful if you want to see the data while
        // capturing it.
        // "-v": verbose
        // "-t": don't print a timestamp
        // "-s 0": capture first 512 bit of packet to get DNS content
        String parameters = "-i any -p -l -v -t -s 512 'udp dst port 53' >> " + file.toString() + " 2>&1 &";

        SimpleExecutableCommand tcpdumpCommand = new SimpleExecutableCommand(context,
                Constants.TCPDUMP_EXECUTABLE, parameters);

        try {
            shell.add(tcpdumpCommand).waitForFinish();
        } catch (Exception exception) {
            Log.e(Constants.TAG, "Exception while starting tcpdump", exception);
            return false;
        }
        return true;
    }

    /**
     * Stop tcpdump.
     */
    static void stopTcpdump(Shell shell) {
        try {
            Toolbox tb = new Toolbox(shell);
            tb.killAllExecutable(Constants.TCPDUMP_EXECUTABLE);
        } catch (Exception e) {
            Log.e(Constants.TAG, "Exception while killing tcpdump", e);
        }
    }

    /**
     * Check if tcpdump binary in bundled in the system.
     *
     * @param shell The shell to check binary presence.
     */
    static void checkSystemTcpdump(Shell shell) {
        try {
            SimpleCommand command = new SimpleCommand("tcpdump --version");
            shell.add(command).waitForFinish();
            int exitCode = command.getExitCode();
            String output = command.getOutput();
            Sentry.capture(
                    "Tcpdump " + (exitCode == 0 ? "present" : "missing (" + exitCode + ")") + "\n"
                            + output
            );
        } catch (Exception exception) {
            Log.w(Constants.TAG, "Failed to check system tcpdump binary.", exception);
        }
    }

    /**
     * Get the tcpdump log file.
     *
     * @param context The application context.
     * @return The tcpdump log file.
     */
    static File getLogFile(Context context) {
        return new File(context.getCacheDir(), Constants.TCPDUMP_LOG);
    }

    /**
     * Get the tcpdump log content.
     *
     * @param context The application context.
     * @return The tcpdump log file content.
     */
    static List<String> getLogs(Context context) {
        File logFile = TcpdumpUtils.getLogFile(context);
        // Check if the log file exists
        if (!logFile.exists()) {
            return Collections.emptyList();
        }
        // hashset, because every hostname should be contained only once
        Set<String> set = new HashSet<>();
        // open the file for reading
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(logFile)))) {
            // read every line of the file into the line-variable, one line at a time
            String nextLine;
            String hostname;
            while ((nextLine = reader.readLine()) != null) {
                hostname = RegexUtils.getTcpdumpHostname(nextLine);
                if (hostname != null) {
                    set.add(hostname);
                }
            }
        } catch (FileNotFoundException exception) {
            Log.e(Constants.TAG, "The tcpdump log file is missing.", exception);
        } catch (IOException exception) {
            Log.e(Constants.TAG, "Can not get cache directory.", exception);
        }

        return new ArrayList<>(set);
    }

    /**
     * Delete log file of tcpdump.
     *
     * @param context The application context.
     */
    static boolean clearLogFile(Context context) {
        // Get the log file
        File file = getLogFile(context);
        // Check if file exists
        if (!file.exists()) {
            return true;
        }
        // Truncate the file content
        try (FileOutputStream outputStream = new FileOutputStream(file, false)) {
            // Only truncate the file
            outputStream.close();   // Useless but help lint
        } catch (IOException exception) {
            Log.e(Constants.TAG, "Error while truncating the tcpdump file!", exception);
            // Return failed to clear the log file
            return false;
        }
        // Return successfully clear the log file
        return true;
    }
}
