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

package org.adaway.helper;

import java.io.IOException;
import java.util.List;

import org.adaway.R;
import org.adaway.util.CommandException;
import org.adaway.util.Constants;

import android.content.Context;
import android.util.Log;

import com.stericson.RootTools.RootTools;
import com.stericson.RootTools.RootToolsException;

public class WebserverHelper {

    /**
     * Install Webserver in /data/data/org.adaway/files
     * 
     * @param context
     */
    public static void installWebserver(Context context) {
        if (RootTools.installBinary(context, R.raw.mongoose, "", "777")) {
            Log.i(Constants.TAG, "Installed webserver");
        } else {
            Log.e(Constants.TAG, "Webserver could not be installed");
        }
    }

    /**
     * Start Webserver
     * 
     * @param context
     * @throws CommandException
     */
    public static void startWebserver(Context context) throws CommandException {
        String privateFilesPath = null;
        String privateCachePath = null;
        try {
            // /data/data/org.adaway/files
            privateFilesPath = context.getFilesDir().getCanonicalPath();
            // /data/data/org.adaway/cache
            privateCachePath = context.getCacheDir().getCanonicalPath();
        } catch (IOException e) {
            Log.e(Constants.TAG,
                    "Problem occured while trying to locate private files and cache directories!");
            e.printStackTrace();
        }

        String commandStartWebserver = privateFilesPath + Constants.FILE_SEPERATOR + "mongoose -e "
                + privateCachePath + Constants.FILE_SEPERATOR + "error_log.txt";

        Log.d(Constants.TAG, "shell command: " + commandStartWebserver);

        List<String> output = null;
        try {
            // execute commands: copy, chown, chmod
            output = RootTools.sendShell(new String[] { commandStartWebserver }, 1);

            Log.d(Constants.TAG, "output of sendShell commands: " + output.toString());
        } catch (IOException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();

            throw new CommandException();
        } catch (InterruptedException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();

            throw new CommandException();
        } catch (RootToolsException e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();

            throw new CommandException();
        }
    }
}
