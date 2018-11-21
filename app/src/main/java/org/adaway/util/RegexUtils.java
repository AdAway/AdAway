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

import com.google.common.net.InetAddresses;
import com.google.common.net.InternetDomainName;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexUtils {
    /*
     * To find hostname in DNS log
     */
    private static final String TCPDUMP_HOSTNAME_REGEX = "(A\\?|AAAA\\?)\\s(\\S+)\\.\\s";
    private static final Pattern TCPDUMP_HOSTNAME_PATTERN = Pattern.compile(TCPDUMP_HOSTNAME_REGEX);


    private static final String HOSTS_PARSER = "^\\s*([^\\s]+)\\s+([^\\s]+)\\s*(?:\\#.*\\s)*$";
    public static final Pattern HOSTS_PARSER_PATTERN = Pattern.compile(HOSTS_PARSER);


    /**
     * I could not find any android class that provides checking of an hostnames, thus I am using
     * regex
     *
     * @param input
     * @return return true if input is valid hostname
     */
    public static boolean isValidHostname(String input) {
        return InternetDomainName.isValid(input);
    }

    /**
     * Same as {@link RegexUtils#isValidHostname(String)} but also allows * and ? as wildcard.
     * <p/>
     * Wildcard validation is quite tricky, because wildcards can be placed anywhere and can match with
     * anything. To make sure we don't dismiss certain valid wildcard host names, we trim wildcards
     * or replace them with an alphanumeric character for further validation.<br/>
     * We only reject whitelist host names which cannot match against valid host names under any circumstances.
     *
     * @param hostname
     * @return
     */
    public static boolean isValidWhitelistHostname(String hostname) {
        // Clear wildcards from host name then validate it
        String clearedHostname = hostname.replaceAll("\\*", "").replaceAll("\\?", "");
        // Replace wildcards from host name by an alphanumeric character
        String replacedHostname = hostname.replaceAll("\\*", "a").replaceAll("\\?", "a");
        // Check if any hostname is valid
        return isValidHostname(clearedHostname) || isValidHostname(replacedHostname);
    }

    /**
     * Check if the given ip is a valid IP address.
     *
     * @param ip The IP to validate.
     * @return {@code true} if the IP is valid, {@code false} otherwise.
     */
    public static boolean isValidIP(String ip) {
        try {
            InetAddresses.forString(ip);
            return true;
        } catch (IllegalArgumentException exception) {
            Log.d(Constants.TAG, "Invalid IP address: " + ip, exception);
            return false;
        }
    }

    /**
     * Gets hostname out of tcpdump log line
     *
     * @param input one line from dns log
     * @return
     */
    public static String getTcpdumpHostname(String input) {
        Matcher tcpdumpHostnameMatcher = TCPDUMP_HOSTNAME_PATTERN.matcher(input);

        try {
            if (tcpdumpHostnameMatcher.find()) {
                return tcpdumpHostnameMatcher.group(2);
            } else {
                Log.d(Constants.TAG, "Does not find: " + input);
                return null;
            }
        } catch (Exception e) {
            Log.e(Constants.TAG, "Error in getTcpdumpHostname", e);
            // workaround for some devices that throws jni exceptions: just accept everything
            return null;
        }
    }

    /*
     * Transforms String with * and ? characters to regex String, convert "example*.*" to regex
     * "^example.*\\..*$", from http://www.rgagnon.com/javadetails/java-0515.html
     */
    public static String wildcardToRegex(String wildcard) {
        StringBuffer s = new StringBuffer(wildcard.length());
        s.append('^');
        for (int i = 0, is = wildcard.length(); i < is; i++) {
            char c = wildcard.charAt(i);
            switch (c) {
                case '*':
                    s.append(".*");
                    break;
                case '?':
                    s.append(".");
                    break;
                // escape special regex-characters
                case '(':
                case ')':
                case '[':
                case ']':
                case '$':
                case '^':
                case '.':
                case '{':
                case '}':
                case '|':
                case '\\':
                    s.append("\\");
                    s.append(c);
                    break;
                default:
                    s.append(c);
                    break;
            }
        }
        s.append('$');
        return (s.toString());
    }
}
