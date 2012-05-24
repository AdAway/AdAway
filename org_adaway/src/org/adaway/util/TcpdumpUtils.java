/*
 * Copyright (C) 2011 Dominik Schürmann <dominik@dominikschuermann.de>
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

package org.adaway.util;

import java.io.File;
import java.io.IOException;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;

import android.content.Context;
import android.widget.Toast;

import com.stericson.RootTools.RootTools;

public class TcpdumpUtils {

    /**
     * Update Tcpdump
     * 
     * @param context
     */
    public static void updateTcpdump(Context context) {
        // update mechanism
        int oldVersion = PreferenceHelper.getTcpdumpVersion(context);

        if (oldVersion < Constants.TCPDUMP_VERSION) {
            Log.i(Constants.TAG, "Updating tcpdump binary from " + oldVersion + " to "
                    + Constants.TCPDUMP_VERSION);

            removeTcpdump(context);
            installTcpdump(context);
            PreferenceHelper.setTcpdumpVersion(context, Constants.TCPDUMP_VERSION);
        } else {
            installTcpdump(context);
        }
    }

    /**
     * Install Tcpdump in /data/data/org.adaway/files if not already there
     * 
     * @param context
     */
    public static void installTcpdump(Context context) {
        if (RootTools.installBinary(context, R.raw.tcpdump, Constants.TCPDUMP_EXECUTEABLE, "777")) {
            Log.i(Constants.TAG, "Installed tcpdump if not already existing.");
        } else {
            Log.e(Constants.TAG, "Tcpdump could not be installed.");
        }
    }

    /**
     * Remove Tcpdump, to reinstall it on update
     * 
     * @param context
     */
    public static void removeTcpdump(Context context) {
        try {
            String filesPath = context.getFilesDir().getCanonicalPath();

            String command = Constants.COMMAND_RM + " " + filesPath + Constants.FILE_SEPERATOR
                    + Constants.TCPDUMP_EXECUTEABLE;

            RootTools.sendShell(command, -1);
        } catch (Exception e) {
            Log.e(Constants.TAG, "Problem while removing tcpdump: " + e);
            e.printStackTrace();
        }
    }

    /**
     * Start Tcpdump with RootTools
     * 
     * @param context
     * @return returns true if starting worked
     */
    public static boolean startTcpdump(final Context context) {
        Log.d(Constants.TAG, "Starting tcpdump...");

        try {
            String cachePath = context.getCacheDir().getCanonicalPath();
            // "-i any": listen on any network interface
            // "-p": disable promiscuous mode (doesn't work anyway)
            // "-l": Make stdout line buffered. Useful if you want to see the data while
            // capturing it.
            // "-v": verbose
            // "-t": don't print a timestamp
            // "-s 0": capture first 512 bit of packet to get DNS content
            String parameters = " -i any -p -l -v -t -s 512 'udp dst port 53' >> " + cachePath
                    + Constants.FILE_SEPERATOR + Constants.TCPDUMP_LOG + " 2>&1 &";

            // If rom contains tcpdump...
            if (RootTools.findBinary(Constants.TCPDUMP_EXECUTEABLE)) {
                Log.i(Constants.TAG, "Rom contains tcpdump, using this one...");

                try {
                    String command = Constants.TCPDUMP_EXECUTEABLE + parameters;
                    RootTools.sendShell(command, -1);

                    return true;
                } catch (Exception e) {
                    Log.e(Constants.TAG, "Problem while starting tcpdump: " + e);
                    e.printStackTrace();

                    return false;
                }
            } else {
                Log.i(Constants.TAG,
                        "Rom does NOT cotain tcpdump, installing tcpdump in files dir...");

                updateTcpdump(context);
                RootTools.runBinary(context, Constants.TCPDUMP_EXECUTEABLE, parameters);

                return true;
            }

        } catch (IOException e) {
            Log.e(Constants.TAG, "Problem while getting cache directory: " + e);
            e.printStackTrace();

            return false;
        }
    }

    /**
     * Deletes log file of tcpdump
     * 
     * @param context
     */
    public static void deleteLog(Context context) {
        try {
            String cachePath = context.getCacheDir().getCanonicalPath();
            String filePath = cachePath + Constants.FILE_SEPERATOR + Constants.TCPDUMP_LOG;

            File file = new File(filePath);
            if (file.exists()) {
                file.delete();
                Toast toast = Toast.makeText(context, R.string.toast_tcpdump_log_deleted,
                        Toast.LENGTH_SHORT);
                toast.show();
            } else {
                Log.e(Constants.TAG, "Tcpdump log is not existing!");
            }
        } catch (IOException e) {
            Log.e(Constants.TAG, "Can not get cache dir: " + e);
            e.printStackTrace();
        }
    }

    /**
     * Stop tcpdump
     * 
     * @param context
     */
    public static void stopTcpdump(Context context) {
        RootTools.killProcess(Constants.TCPDUMP_EXECUTEABLE);
    }

    /**
     * Checks if tcpdump is running with RootTools
     * 
     * @return true if tcpdump is running
     */
    public static boolean isTcpdumpRunning() {
        if (RootTools.isProcessRunning(Constants.TCPDUMP_EXECUTEABLE)) {
            return true;
        } else {
            return false;
        }
    }
}
