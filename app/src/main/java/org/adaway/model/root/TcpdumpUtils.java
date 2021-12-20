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

package org.adaway.model.root;

import android.content.Context;

import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static org.adaway.util.ShellUtils.isBundledExecutableRunning;
import static org.adaway.util.ShellUtils.killBundledExecutable;
import static org.adaway.util.ShellUtils.mergeAllLines;
import static org.adaway.util.ShellUtils.runBundledExecutable;

import timber.log.Timber;

class TcpdumpUtils {
    private static final String TCPDUMP_EXECUTABLE = "tcpdump";
    private static final String TCPDUMP_LOG = "dns_log.txt";
    private static final String TCPDUMP_HOSTNAME_REGEX = "(?:A\\?|AAAA\\?)\\s(\\S+)\\.\\s";
    private static final Pattern TCPDUMP_HOSTNAME_PATTERN = Pattern.compile(TCPDUMP_HOSTNAME_REGEX);

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
    static boolean isTcpdumpRunning() {
        return isBundledExecutableRunning(TCPDUMP_EXECUTABLE);
    }

    /**
     * Start tcpdump tool.
     *
     * @param context The application context.
     * @return returns true if starting worked
     */
    static boolean startTcpdump(Context context) {
        Timber.d("Starting tcpdump...");
        checkSystemTcpdump();

        File file = getLogFile(context);
        try {
            // Create log file before using it with tcpdump if not exists
            if (!file.exists() && !file.createNewFile()) {
                return false;
            }
        } catch (IOException e) {
            Timber.e(e, "Problem while getting cache directory!");
            return false;
        }

        // "-i any": listen on any network interface
        // "-p": disable promiscuous mode (doesn't work anyway)
        // "-l": Make stdout line buffered. Useful if you want to see the data while
        // capturing it.
        // "-v": verbose
        // "-t": don't print a timestamp
        // "-s 0": capture first 512 bit of packet to get DNS content
        String parameters = "-i any -p -l -v -t -s 512 'udp dst port 53' >> " + file.toString() + " 2>&1";

        return runBundledExecutable(context, TCPDUMP_EXECUTABLE, parameters);
    }

    /**
     * Stop tcpdump.
     */
    static void stopTcpdump() {
        killBundledExecutable(TCPDUMP_EXECUTABLE);
    }

    /**
     * Check if tcpdump binary in bundled in the system.
     */
    static void checkSystemTcpdump() {
        try {
            Shell.Result result = Shell.su("tcpdump --version").exec();
            int exitCode = result.getCode();
            String output = mergeAllLines(result.getOut());
            String msg = "Tcpdump " + (
                            exitCode == 0 ?
                                    "present" :
                                    "missing (" + exitCode + ")"
                    ) + "\n" + output;
            Timber.i(msg);
        } catch (Exception exception) {
            Timber.w(exception, "Failed to check system tcpdump binary.");
        }
    }

    /**
     * Get the tcpdump log file.
     *
     * @param context The application context.
     * @return The tcpdump log file.
     */
    static File getLogFile(Context context) {
        return new File(context.getCacheDir(), TCPDUMP_LOG);
    }

    /**
     * Get the tcpdump log content.
     *
     * @param context The application context.
     * @return The tcpdump log file content.
     */
    static List<String> getLogs(Context context) {
        Path logPath = getLogFile(context).toPath();
        // Check if the log file exists
        if (!Files.exists(logPath)) {
            return emptyList();
        }
        try (Stream<String> lines = Files.lines(logPath)) {
            return lines
                    .map(TcpdumpUtils::getTcpdumpHostname)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        } catch (IOException exception) {
            Timber.e(exception, "Can not get cache directory.");
            return emptyList();
        }
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
            Timber.e(exception, "Error while truncating the tcpdump file!");
            // Return failed to clear the log file
            return false;
        }
        // Return successfully clear the log file
        return true;
    }

    /**
     * Gets hostname out of tcpdump log line.
     *
     * @param input One line from dns log.
     * @return A hostname or {code null} if no DNS query in the input.
     */
    private static String getTcpdumpHostname(String input) {
        Matcher tcpdumpHostnameMatcher = TCPDUMP_HOSTNAME_PATTERN.matcher(input);
        if (tcpdumpHostnameMatcher.find()) {
            return tcpdumpHostnameMatcher.group(1);
        } else {
            Timber.d("Does not find: %s.", input);
            return null;
        }
    }
}
