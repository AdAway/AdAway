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

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashSet;

import org.adaway.helper.ValidationHelper;

import android.content.Context;
import android.util.Log;

public class HostsParser {
    private HashSet<String> mHostnames;

    public HostsParser(BufferedReader input, Context context) throws IOException {
        parse(input);
    }

    public HashSet<String> getHostnames() {
        return mHostnames;
    }

    /**
     * Parse hosts file from BufferedReader
     * 
     * @param reader
     * @throws IOException
     */
    private void parse(BufferedReader reader) throws IOException {
        String nextLine = null;
        String currentIp = null;
        String currentHostname = null;
        mHostnames = new HashSet<String>();

        int indexComment = -1;
        int indexWhitespace = -1;
        int indexTab = -1;
        while ((nextLine = reader.readLine()) != null) {
            // trim whitespaces
            nextLine = nextLine.trim();

            // only if its not a comment line
            if (!nextLine.startsWith("#")) {
                // check if there is any comment in that line
                indexComment = nextLine.indexOf('#');
                if (indexComment != -1) {
                    // strip comment from line and go on
                    nextLine = nextLine.substring(0, indexComment);
                }

                // strip whitespaces from begin and end
                nextLine = nextLine.trim();

                // seperate ip and hostname
                indexWhitespace = nextLine.indexOf(' ');
                indexTab = nextLine.indexOf('\t');
                if (indexWhitespace != -1) {
                    currentIp = nextLine.substring(0, indexWhitespace);
                    currentHostname = nextLine.substring(indexWhitespace);
                } else if (indexTab != -1) {
                    currentIp = nextLine.substring(0, indexTab);
                    currentHostname = nextLine.substring(indexTab);
                }

                // Log.d(Constants.TAG, "remaining line: " + nextLine);

                if (currentHostname != null && currentIp != null) {
                    // strip whitespaces from begin and end of hostname and ip
                    currentHostname = currentHostname.trim();
                    currentIp = currentIp.trim();

                    // check if ip is 127.0.0.1 or 0.0.0.0
                    if (currentIp.equals(Constants.LOCALHOST_IPv4) || currentIp.equals(Constants.BOGUS_IPv4)) {
                        // check syntax of hostname
                        if (ValidationHelper.isValidHostname(currentHostname)) {
                            // Log.d(TAG, nextLine + " matched, adding to hostnames");
                            mHostnames.add(currentHostname);
                        } else {
                            Log.d(Constants.TAG, currentHostname + " NOT matched");
                        }
                    }
                }
            }
        }

        // strip localhost entry
        mHostnames.remove(Constants.LOCALHOST_HOSTNAME);
    }
}
