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

package org.adaway.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.util.Log;

public class HostsParser {
    private Context mContext;

    private HashSet<String> hostnames;
    private LinkedList<String> comments;

    public HostsParser(BufferedReader input, Context context) throws IOException {
        mContext = context;
        parse(input);
    }

    public HashSet<String> getHostnames() {
        return hostnames;
    }

    public LinkedList<String> getComments() {
        return comments;
    }

    /**
     * Parse hosts file from BufferedReader
     * 
     * @param reader
     * @throws IOException
     */
    private void parse(BufferedReader reader) throws IOException {
        String nextLine = null;
        hostnames = new HashSet<String>();
        comments = new LinkedList<String>();

        // check for comment only line
        String commentRegex = "^#";
        Pattern commentPattern = Pattern.compile(commentRegex);

        // get preference on checking syntax
        boolean checkSyntax = SharedPrefs.getCheckSyntax(mContext);

        Matcher commentMatcher = null;
        int indexComment = -1;
        int indexWhitespace = -1;
        int indexTab = -1;

        while ((nextLine = reader.readLine()) != null) {

            // check for comment only line
            commentMatcher = commentPattern.matcher(nextLine);
            if (commentMatcher.find()) {
                // Log.d(TAG, nextLine + " is a comment only line");
                comments.add(nextLine);
            } else { // other line
                // check if there is any comment in that line
                indexComment = nextLine.indexOf('#');
                if (indexComment != -1) {
                    // strip comment from line and go on
                    nextLine = nextLine.substring(0, indexComment);
                }

                // strip whitespaces from begin and end
                nextLine = nextLine.trim();

                // strip ip from line
                indexWhitespace = nextLine.indexOf(' ');
                indexTab = nextLine.indexOf('\t');
                if (indexWhitespace != -1) {
                    nextLine = nextLine.substring(indexWhitespace);
                } else if (indexTab != -1) {
                    nextLine = nextLine.substring(indexTab);
                }

                // Log.d(TAG, "remaining line: " + nextLine);

                // strip whitespaces from begin and end
                nextLine = nextLine.trim();

                // check preferences: should we check syntax?
                if (checkSyntax) {
                    if (Helper.isValidHostname(nextLine)) {
                        // Log.d(TAG, nextLine + " matched, adding to hostnames");
                        hostnames.add(nextLine);
                    } else {
                        Log.d(Constants.TAG, nextLine + " NOT matched");
                    }
                } else {
                    // add without checking
                    hostnames.add(nextLine);
                }
            }
        }

        // strip localhost entry
        hostnames.remove(Constants.LOCALHOST_HOSTNAME);
    }
}
