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

import org.adaway.helper.PreferenceHelper;
import org.sufficientlysecure.rootcommands.Shell;
import org.sufficientlysecure.rootcommands.Toolbox;
import org.sufficientlysecure.rootcommands.command.SimpleExecutableCommand;

import android.content.Context;

public class WebserverUtils {

    /**
     * Start Webserver in new Thread with RootTools
     * 
     * @param context
     */
    public static void startWebserver(Context context, Shell shell) {
        Log.d(Constants.TAG, "Starting webserver...");

        try {
            SimpleExecutableCommand webserverCommand = new SimpleExecutableCommand(context,
                    Constants.WEBSERVER_EXECUTEABLE, " > /dev/null 2>&1 &");

            shell.add(webserverCommand).waitForFinish();
        } catch (Exception e) {
            Log.e(Constants.TAG, "Exception while starting webserver", e);
        }
    }

    /**
     * Start Webserver in new Thread with RootTools on Boot if enabled in preferences
     * 
     * @param context
     */
    public static void startWebserverOnBoot(Context context) {
        // start webserver on boot if enabled in preferences
        if (PreferenceHelper.getWebserverOnBoot(context)) {
            try {
                Shell rootShell = Shell.startRootShell();
                startWebserver(context, rootShell);
                rootShell.close();
            } catch (Exception e) {
                Log.e(Constants.TAG, "Problem while starting webserver on boot!", e);
            }
        }
    }

    /**
     * Stop webserver
     * 
     * @param context
     */
    public static void stopWebserver(Context context, Shell shell) {
        try {
            Toolbox tb = new Toolbox(shell);
            tb.killAllExecutable(Constants.WEBSERVER_EXECUTEABLE);
        } catch (Exception e) {
            Log.e(Constants.TAG, "Exception while killing webserver", e);
        }
    }

    /**
     * Checks if werbserver is running
     * 
     * @return true if webserver is running
     */
    public static boolean isWebserverRunning(Shell shell) {
        try {
            Toolbox tb = new Toolbox(shell);

            if (tb.isBinaryRunning(Constants.WEBSERVER_EXECUTEABLE)) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            Log.e(Constants.TAG, "Exception while checking webserver process", e);
            return false;
        }
    }

}
