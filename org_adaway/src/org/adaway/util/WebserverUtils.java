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

import android.content.Context;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.stericson.RootTools.RootTools;

public class WebserverUtils {

    /**
     * Install Webserver in /data/data/org.adaway/files if not already there
     * 
     * @param context
     * @throws RemountException
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
     * Start Webserver in new Thread with RootTools
     * 
     * @param context
     * @throws CommandException
     */
    public static void startWebserver(final Context context) {
        RootTools.runBinary(context, Constants.WEBSERVER_EXECUTEABLE, "");

        Toast.makeText(context, context.getString(R.string.button_webserver_toggle_checked), 3)
                .show();
    }

    /**
     * Stop Webserver
     * 
     * @param context
     * @throws CommandException
     */
    public static void stopWebserver(Context context) {
        RootTools.killProcess(Constants.WEBSERVER_EXECUTEABLE);

        Toast.makeText(context, context.getString(R.string.button_webserver_toggle_unchecked), 3)
                .show();
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

    /**
     * Set ToggleButton checked if webserver is running
     * 
     * @param webserverToggle
     */
    public static void setWebserverToggle(ToggleButton webserverToggle) {
        if (isWebserverRunning()) {
            webserverToggle.setChecked(true);
        } else {
            webserverToggle.setChecked(false);
        }
    }
}
