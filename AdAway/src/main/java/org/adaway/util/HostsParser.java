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

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.adaway.util.Log;

import gnu.trove.set.hash.THashSet;
import gnu.trove.map.hash.THashMap;

/**
 * A parser to build sets out of hosts files. Redirection Lists have higher priority than whitelist
 * or blacklist items.
 */
public class HostsParser {
    private THashSet<String> mBlacklist;
    private THashSet<String> mWhitelist;
    private THashMap<String, String> mRedirectionList;

    private Matcher mHostsParserMatcher;
    private Pattern mHostsParserPattern;

    private boolean mParseWhitelist;
    private boolean mParseRedirections;

    public HostsParser(BufferedReader input, boolean parseWhitelist, boolean parseRedirections)
            throws IOException {
        mParseWhitelist = parseWhitelist;
        mParseRedirections = parseRedirections;
        parse(input);
    }

    public THashSet<String> getBlacklist() {
        return mBlacklist;
    }

    public THashSet<String> getWhitelist() {
        return mWhitelist;
    }

    public THashMap<String, String> getRedirectionList() {
        return mRedirectionList;
    }

    /**
     * Parse hosts file from BufferedReader
     *
     * @param reader
     * @throws IOException
     */
    private void parse(BufferedReader reader) throws IOException {
        String nextLine;
        String currentIp;
        String currentHostname;
        mBlacklist = new THashSet<String>();
        mWhitelist = new THashSet<String>();
        mRedirectionList = new THashMap<String, String>();

        // use whitelist import pattern
        if (mParseWhitelist) {
            mHostsParserPattern = RegexUtils.hostsParserWhitelistImportPattern;
        } else {
            mHostsParserPattern = RegexUtils.hostsParserPattern;
        }
        while ((nextLine = reader.readLine()) != null) {
            mHostsParserMatcher = mHostsParserPattern.matcher(nextLine);

            if (mHostsParserMatcher.matches()) {
                // for (int i = 0; i <= mHostsParserMatcher.groupCount(); i++) {
                // Log.d(Constants.TAG, "group (" + i + "): " + mHostsParserMatcher.group(i));
                // }

                currentIp = mHostsParserMatcher.group(1);
                currentHostname = mHostsParserMatcher.group(2);

                // check if ip is 127.0.0.1 or 0.0.0.0
                if (currentIp.equals(Constants.LOCALHOST_IPv4)
                        || currentIp.equals(Constants.BOGUS_IPv4)
                        || currentIp.equals(Constants.LOCALHOST_IPv6)) {
                    mBlacklist.add(currentHostname);
                } else if (currentIp.equals(Constants.WHITELIST_ENTRY)) {
                    mWhitelist.add(currentHostname);
                } else if (mParseRedirections) {
                    mRedirectionList.put(currentHostname, currentIp);
                }
            } else {
                Log.d(Constants.TAG, "Does not match: " + nextLine);
            }
        }

        // strip localhost entry from blacklist and redirection list
        mBlacklist.remove(Constants.LOCALHOST_HOSTNAME);
        mRedirectionList.remove(Constants.LOCALHOST_HOSTNAME);
    }

    /**
     * Add blacklist to this hosts file
     *
     * @param blacklist
     */
    public void addBlacklist(THashSet<String> blacklist) {
        mBlacklist.addAll(blacklist);
    }

    /**
     * Add whitelist to this host file. This supports simple regex in entries.
     *
     * @param whitelist
     */
    public void addWhitelist(THashSet<String> whitelist) {
        mWhitelist.addAll(whitelist);
    }

    /**
     * Add redirection rules as THashMap.
     * <p/>
     * These mappings will replace any mappings that this map had for any of the keys currently in
     * the specified map.
     *
     * @param redirectionList
     */
    public void addRedirectionList(THashMap<String, String> redirectionList) {
        mRedirectionList.putAll(redirectionList);
    }

    /**
     * Remove whitelist entries from blacklist with regex,
     */
    public void compileList() {
        Log.d(Constants.TAG, "Compiling all whitelist regex");

        // remove whitelist items from blacklist using regex
        THashSet<Pattern> whitelistPattern = new THashSet<Pattern>();
        String regexItem;
        for (String item : mWhitelist) {
            // convert example*.* to regex: ^example.*\\..*$
            regexItem = RegexUtils.wildcardToRegex(item);
            whitelistPattern.add(Pattern.compile(regexItem));
        }

        if(whitelistPattern.size()>0) {
            Log.d(Constants.TAG, "Starting whitelist regex");
            Matcher whitelistMatcher;
            String blacklistHostname;
            // go through all blacklist hostnames from host sources
            for (Iterator<String> iterator = mBlacklist.iterator(); iterator.hasNext(); ) {
                blacklistHostname = iterator.next();

                // use all whitelist patterns on this hostname
                for (Pattern pattern : whitelistPattern) {
                    whitelistMatcher = pattern.matcher(blacklistHostname);

                    try {
                        if (whitelistMatcher.find()) {
                            // remove item, because regex fits
                            iterator.remove();
                            break;
                        }
                    } catch (Exception e) {
                        // workaround for some devices that throws jni exceptions: dont use
                        // whitelist
                        Log.e(Constants.TAG, "Error in whitelist regex processing", e);
                    }
                }
            }
            Log.d(Constants.TAG, "Ending whitelist regex");
        } else {
            Log.d(Constants.TAG, "Skipping whitelist regex");
        }

        // remove hostnames that are in redirection list
        THashSet<String> redirectionRemove = new THashSet<String>(mRedirectionList.keySet());
        mBlacklist.removeAll(redirectionRemove);
    }
}
