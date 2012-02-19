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
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.adaway.util.Log;

public class HostsParser {
    private HashSet<String> mBlacklist;
    private HashSet<String> mWhitelist;
    private HashMap<String, String> mRedirectionList;

    private Matcher mHostsParserMatcher;
    private Pattern mHostsParserPattern;

    private boolean mWhitelistImport;

    public HostsParser(BufferedReader input, boolean whitelistImport) throws IOException {
        mWhitelistImport = whitelistImport;
        parse(input);
    }

    public HashSet<String> getBlacklist() {
        return mBlacklist;
    }

    public HashSet<String> getWhitelist() {
        return mWhitelist;
    }

    public HashMap<String, String> getRedirectionList() {
        return mRedirectionList;
    }

    /**
     * Parse hosts file from BufferedReader
     * 
     * @param reader
     * @throws IOException
     */
    private void parse(BufferedReader reader) throws IOException {
        String nextLine = new String();
        String currentIp = new String();
        String currentHostname = new String();
        mBlacklist = new HashSet<String>();
        mWhitelist = new HashSet<String>();
        mRedirectionList = new HashMap<String, String>();

        // use whitelist import pattern
        if (mWhitelistImport) {
            mHostsParserPattern = RegexUtils.hostsParserWhitelistImportPattern;
        } else {
            mHostsParserPattern = RegexUtils.hostsParserPattern;
        }
        while ((nextLine = reader.readLine()) != null) {
            mHostsParserMatcher = mHostsParserPattern.matcher(nextLine);

            // try {
            if (mHostsParserMatcher.matches()) {
                // for (int i = 0; i <= mHostsParserMatcher.groupCount(); i++) {
                // Log.d(Constants.TAG, "group (" + i + "): " + mHostsParserMatcher.group(i));
                // }

                currentIp = mHostsParserMatcher.group(1);
                currentHostname = mHostsParserMatcher.group(2);

                // check if ip is 127.0.0.1 or 0.0.0.0
                if (currentIp.equals(Constants.LOCALHOST_IPv4)
                        || currentIp.equals(Constants.BOGUS_IPv4)) {
                    mBlacklist.add(currentHostname);
                } else if (currentIp.equals(Constants.WHITELIST_ENTRY)) {
                    mWhitelist.add(currentHostname);
                } else {
                    mRedirectionList.put(currentHostname, currentIp);
                }
            } else {
                Log.d(Constants.TAG, "Does not match: " + nextLine);
            }
            // } catch (Exception e) {
            // Log.e(Constants.TAG, "Error in HostsParser");
            // e.printStackTrace();
            // }
        }

        // strip localhost entry
        mBlacklist.remove(Constants.LOCALHOST_HOSTNAME);
    }
}
