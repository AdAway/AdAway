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

    private static final String HOSTS_PARSER = "^\\s*([^#\\s]+)\\s+([^#\\s]+)\\s*(?:#.*)*$";
    static final Pattern HOSTS_PARSER_PATTERN = Pattern.compile(HOSTS_PARSER);

    /**
     * Check whether a hostname is valid.
     *
     * @param hostname The hostname to validate.
     * @return return {@code true} if hostname is valid, {@code false} otherwise.
     */
    public static boolean isValidHostname(String hostname) {
        return InternetDomainName.isValid(hostname);
    }

    /**
     * Check whether a wildcard hostname is valid.
     * Wildcard hostname is an hostname with one of the following wildcard:
     * <ul>
     * <li>{@code *} for any character sequence,</li>
     * <li>{@code ?} for any character</li>
     * </ul>
     * <p/>
     * Wildcard validation is quite tricky, because wildcards can be placed anywhere and can match with
     * anything. To make sure we don't dismiss certain valid wildcard host names, we trim wildcards
     * or replace them with an alphanumeric character for further validation.<br/>
     * We only reject whitelist host names which cannot match against valid host names under any circumstances.
     *
     * @param hostname The wildcard hostname to validate.
     * @return return {@code true} if wildcard hostname is valid, {@code false} otherwise.
     */
    public static boolean isValidWildcardHostname(String hostname) {
        // Clear wildcards from host name then validate it
        String clearedHostname = hostname.replaceAll("\\*", "").replaceAll("\\?", "");
        // Replace wildcards from host name by an alphanumeric character
        String replacedHostname = hostname.replaceAll("\\*", "a").replaceAll("\\?", "a");
        // Check if any hostname is valid
        return isValidHostname(clearedHostname) || isValidHostname(replacedHostname);
    }

    /**
     * Check if an IP address is valid.
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
            if (tcpdumpHostnameMatcher.matches()) {
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
    static String wildcardToRegex(String wildcard) {
        StringBuilder regex = new StringBuilder(wildcard.length());
        regex.append('^');
        for (int i = 0, is = wildcard.length(); i < is; i++) {
            char c = wildcard.charAt(i);
            switch (c) {
                case '*':
                    regex.append(".*");
                    break;
                case '?':
                    regex.append(".");
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
                    regex.append("\\");
                    regex.append(c);
                    break;
                default:
                    regex.append(c);
                    break;
            }
        }
        regex.append('$');
        return regex.toString();
    }
}
