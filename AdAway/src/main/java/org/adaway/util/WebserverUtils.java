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

import java.io.IOException;

public class WebserverUtils {
    /**
     * Start the web server in new thread with RootTools
     *
     * @param context The application context.
     */
    public static void startWebServer(Context context) {
        Log.d(Constants.TAG, "Starting web server...");

        Shell shell = null;
        try {
            shell = Shell.startRootShell();
            SimpleExecutableCommand webServerCommand = new SimpleExecutableCommand(
                    context,
                    Constants.WEBSERVER_EXECUTEABLE,
                    " > /dev/null 2>&1 &"
            );
            shell.add(webServerCommand).waitForFinish();
        } catch (Exception e) {
            Log.e(Constants.TAG, "Exception while starting web server", e);
        } finally {
            if (shell != null) {
                try {
                    shell.close();
                } catch (IOException exception) {
                    Log.d(Constants.TAG, "Error while closing root shell.", exception);
                }
            }
        }
    }

    /**
     * Start web server in new Thread with RootTools on Boot if enabled in preferences.
     *
     * @param context The application context.
     */
    public static void startWebServerOnBoot(Context context) {
        // start web server on boot if enabled in preferences
        if (PreferenceHelper.getWebServerOnBoot(context)) {
            WebserverUtils.startWebServer(context);
        }
    }

    /**
     * Stop the web server.
     */
    public static void stopWebServer() {
        Shell shell = null;
        try {
            shell = Shell.startRootShell();
            Toolbox tb = new Toolbox(shell);
            tb.killAllExecutable(Constants.WEBSERVER_EXECUTEABLE);
        } catch (Exception e) {
            Log.e(Constants.TAG, "Exception while killing web server", e);
        } finally {
            if (shell != null) {
                try {
                    shell.close();
                } catch (IOException exception) {
                    Log.d(Constants.TAG, "Error while closing root shell.", exception);
                }
            }
        }
    }

    /**
     * Checks if web server is running
     *
     * @return <code>true</code> if webs server is running, <code>false</code> otherwise.
     */
    public static boolean isWebServerRunning() {
        boolean running = false;
        Shell shell = null;
        try {
            shell = Shell.startRootShell();
            Toolbox tb = new Toolbox(shell);

            if (tb.isBinaryRunning(Constants.WEBSERVER_EXECUTEABLE)) {
                running = true;
            }
        } catch (Exception e) {
            Log.e(Constants.TAG, "Exception while checking web server process", e);
        } finally {
            if (shell != null) {
                try {
                    shell.close();
                } catch (IOException exception) {
                    Log.d(Constants.TAG, "Error while closing root shell.", exception);
                }
            }
        }
        return running;
    }
}
