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
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.THashSet;

import static org.adaway.util.Constants.WHITELIST_ENTRY;
import static org.adaway.util.RegexUtils.HOSTS_PARSER_PATTERN;

/**
 * A parser to build sets out of hosts files. Redirection Lists have higher priority than whitelist
 * or blacklist items.
 */
public class HostsParser {
    private Set<String> mBlacklist;
    private Set<String> mWhitelist;
    private Map<String, String> mRedirectionList;

    private boolean mParseWhitelist;
    private boolean mParseRedirections;

    public HostsParser(BufferedReader input, boolean parseWhitelist, boolean parseRedirections)
            throws IOException {
        mBlacklist = new THashSet<>();
        mWhitelist = new THashSet<>();
        mRedirectionList = new THashMap<>();

        mParseWhitelist = parseWhitelist;
        mParseRedirections = parseRedirections;
        parse(input);
    }

    public Set<String> getBlacklist() {
        return mBlacklist;
    }

    public Set<String> getWhitelist() {
        return mWhitelist;
    }

    public Map<String, String> getRedirectList() {
        return mRedirectionList;
    }

    /**
     * Add blacklist to this hosts file
     *
     * @param blacklist
     */
    public void addBlacklist(Set<String> blacklist) {
        mBlacklist.addAll(blacklist);
    }

    /**
     * Add whitelist to this host file. This supports simple regex in entries.
     *
     * @param whitelist
     */
    public void addWhitelist(Set<String> whitelist) {
        mWhitelist.addAll(whitelist);
    }

    /**
     * Add redirection rules as map.
     * <p/>
     * These mappings will replace any mappings that this map had for any of the keys currently in
     * the specified map.
     *
     * @param redirectionList
     */
    public void addRedirectList(Map<String, String> redirectionList) {
        mRedirectionList.putAll(redirectionList);
    }

    /**
     * Remove whitelist entries from blacklist with regex,
     */
    public void compileList() {
        Log.d(Constants.TAG, "Compiling all whitelist regex");

        // remove whitelist items from blacklist using regex
        Set<Pattern> whitelistPattern = new THashSet<>();
        String regexItem;
        for (String item : mWhitelist) {
            // convert example*.* to regex: ^example.*\\..*$
            regexItem = RegexUtils.wildcardToRegex(item);
            whitelistPattern.add(Pattern.compile(regexItem));
        }

        if (whitelistPattern.size() > 0) {
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
                        // workaround for some devices that throws jni exceptions: don't use
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
        Set<String> redirectionRemove = new THashSet<>(mRedirectionList.keySet());
        mBlacklist.removeAll(redirectionRemove);
    }

    /**
     * Parse hosts file from BufferedReader
     *
     * @param reader
     * @throws IOException
     */
    private void parse(BufferedReader reader) throws IOException {
        String nextLine;
        // use whitelist import pattern
        while ((nextLine = reader.readLine()) != null) {
            Matcher mHostsParserMatcher = HOSTS_PARSER_PATTERN.matcher(nextLine);
            if (!mHostsParserMatcher.matches()) {
                Log.d(Constants.TAG, "Does not match: " + nextLine);
                continue;
            }
            // Check IP address validity or while list entry (if allowed)
            String ip = mHostsParserMatcher.group(1);
            if (!RegexUtils.isValidIP(ip) && (!WHITELIST_ENTRY.equals(ip) || !mParseWhitelist)) {
                Log.d(Constants.TAG, "IP address is not valid: " + ip);
                continue;
            }
            // Check hostname
            String hostname = mHostsParserMatcher.group(2);
            if (!RegexUtils.isValidWhitelistHostname(hostname)) {
                Log.d(Constants.TAG, "hostname is not valid: " + hostname);
                continue;
            }
            // Add valid ip and hostname to the right list
            addToList(ip, hostname);
        }

        // strip localhost entry from blacklist and redirection list
        mBlacklist.remove(Constants.LOCALHOST_HOSTNAME);
        mRedirectionList.remove(Constants.LOCALHOST_HOSTNAME);
    }

    private void addToList(String ip, String hostname) {
        // check if ip is 127.0.0.1 or 0.0.0.0
        if (ip.equals(Constants.LOCALHOST_IPv4)
                || ip.equals(Constants.BOGUS_IPv4)
                || ip.equals(Constants.LOCALHOST_IPv6)) {
            mBlacklist.add(hostname);
        } else if (ip.equals(WHITELIST_ENTRY)) {
            mWhitelist.add(hostname);
        } else if (mParseRedirections) {
            mRedirectionList.put(hostname, ip);
        }
    }
}
