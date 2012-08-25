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

package org.adaway.util;

import java.io.File;
import java.io.IOException;

import org.adaway.R;
import org.rootcommands.Shell;
import org.rootcommands.Toolbox;
import org.rootcommands.command.SimpleBinaryCommand;

import android.content.Context;
import android.widget.Toast;

public class TcpdumpUtils {

    /**
     * Start Tcpdump with RootTools
     * 
     * @param context
     * @return returns true if starting worked
     */
    public static boolean startTcpdump(Context context, Shell shell) {
        Log.d(Constants.TAG, "Starting tcpdump...");

        String cachePath = null;
        try {
            cachePath = context.getCacheDir().getCanonicalPath();
            String filePath = cachePath + Constants.FILE_SEPERATOR + Constants.TCPDUMP_LOG;

            // create log file before using it with tcpdump
            File file = new File(filePath);
            file.createNewFile();
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
        String parameters = "-i any -p -l -v -t -s 512 'udp dst port 53' >> " + cachePath
                + Constants.FILE_SEPERATOR + Constants.TCPDUMP_LOG + " 2>&1 &";

        SimpleBinaryCommand tcpdumpCommand = new SimpleBinaryCommand(context,
                Constants.TCPDUMP_EXECUTEABLE, parameters);

        try {
            shell.add(tcpdumpCommand).waitForFinish();
        } catch (Exception e) {
            Log.e(Constants.TAG, "Exception while starting tcpdump", e);
            return false;
        }

        return true;
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
            Log.e(Constants.TAG, "Can not get cache dir!", e);
        }
    }

    /**
     * Stop tcpdump
     * 
     * @param context
     */
    public static void stopTcpdump(Context context, Shell shell) {
        Toolbox tb = new Toolbox(shell);
        try {
            tb.killAllBinary(Constants.TCPDUMP_EXECUTEABLE);
        } catch (Exception e) {
            Log.e(Constants.TAG, "Exception while killing tcpdump", e);
        }
    }

    /**
     * Checks if tcpdump is running
     * 
     * @return true if tcpdump is running
     */
    public static boolean isTcpdumpRunning(Shell shell) {
        Toolbox tb = new Toolbox(shell);

        try {
            if (tb.isProcessRunning(Constants.TCPDUMP_EXECUTEABLE)) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            Log.e(Constants.TAG, "Exception while checking tcpdump", e);
            return false;
        }
    }
}
