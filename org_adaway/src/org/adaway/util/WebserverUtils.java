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

import org.adaway.R;
import org.adaway.helper.PreferencesHelper;

import android.content.Context;

import com.stericson.RootTools.RootTools;

public class WebserverUtils {

    /**
     * Update Webserver
     * 
     * @param context
     */
    public static void updateWebserver(Context context) {
        // update mechanism
        int oldVersion = PreferencesHelper.getWebserverVersion(context);

        if (oldVersion < Constants.WEBSERVER_VERSION) {
            Log.i(Constants.TAG, "Updating webserver binary from " + oldVersion + " to "
                    + Constants.WEBSERVER_VERSION);

            removeWebserver(context);
            installWebserver(context);
            PreferencesHelper.setWebserverVersion(context, Constants.WEBSERVER_VERSION);
        } else {
            installWebserver(context);
        }
    }

    /**
     * Install Webserver in /data/data/org.adaway/files if not already there
     * 
     * @param context
     */
    public static void installWebserver(Context context) {
        if (RootTools.installBinary(context, R.raw.blank_webserver,
                Constants.WEBSERVER_EXECUTEABLE, "777")) {
            Log.i(Constants.TAG, "Installed webserver if not already existing.");
        } else {
            Log.e(Constants.TAG, "Webserver could not be installed.");
        }
    }

    /**
     * Remove Webserver, to reinstall it on update
     * 
     * @param context
     */
    public static void removeWebserver(Context context) {
        try {
            String filesPath = context.getFilesDir().getCanonicalPath();

            String command = Constants.COMMAND_RM + " " + filesPath + Constants.FILE_SEPERATOR
                    + Constants.WEBSERVER_EXECUTEABLE;

            RootTools.sendShell(command);
        } catch (Exception e) {
            Log.e(Constants.TAG, "Problem while removing webserver: " + e);
            e.printStackTrace();
        }
    }

    /**
     * Start Webserver in new Thread with RootTools
     * 
     * @param context
     */
    public static void startWebserver(final Context context) {
        Log.d(Constants.TAG, "Starting webserver...");
        RootTools.runBinary(context, Constants.WEBSERVER_EXECUTEABLE, " > /dev/null 2>&1 &");
    }

    /**
     * Start Webserver in new Thread with RootTools on Boot if enabled in preferences
     * 
     * @param context
     */
    public static void startWebserverOnBoot(final Context context) {
        // start webserver on boot if enabled in preferences
        if (PreferencesHelper.getWebserverOnBoot(context)) {
            startWebserver(context);
        }
    }

    /**
     * Stop Webserver
     * 
     * @param context
     * @throws CommandException
     */
    public static void stopWebserver(Context context) {
        RootTools.killProcess(Constants.WEBSERVER_EXECUTEABLE);
    }

    /**
     * Checks if webserver is running with RootTools
     * 
     * @return true if webserver is running
     */
    public static boolean isWebserverRunning() {
        if (RootTools.isProcessRunning(Constants.WEBSERVER_EXECUTEABLE)) {
            return true;
        } else {
            return false;
        }
    }
}
