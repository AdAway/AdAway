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

import android.content.Context;

import static org.adaway.util.Constants.WEBSERVER_EXECUTABLE;
import static org.adaway.util.ShellUtils.isBundledExecutableRunning;
import static org.adaway.util.ShellUtils.killBundledExecutable;
import static org.adaway.util.ShellUtils.runBundledExecutable;

/**
 * This class is an utility class to control web server execution.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class WebServerUtils {
    /**
     * Start the web server in new thread with RootTools
     *
     * @param context The application context.
     */
    public static void startWebServer(Context context) {
        Log.d(Constants.TAG, "Starting web server...");

        String parameters = " > /dev/null 2>&1";
        runBundledExecutable(context, WEBSERVER_EXECUTABLE, parameters);
    }

    /**
     * Stop the web server.
     */
    public static void stopWebServer() {
        killBundledExecutable(WEBSERVER_EXECUTABLE);
    }

    /**
     * Checks if web server is running
     *
     * @return <code>true</code> if webs server is running, <code>false</code> otherwise.
     */
    public static boolean isWebServerRunning() {
        return isBundledExecutableRunning(WEBSERVER_EXECUTABLE);
    }
}
