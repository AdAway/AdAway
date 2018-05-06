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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.webkit.URLUtil;

public class RegexUtils {
    /*
     * Allow hostnames like: localserver example.com example.host.org
     */
    static final private String HOSTNAME_REGEX = "[a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-\\_\\.]{0,61}[a-zA-Z0-9]";
    private static Pattern mHostnamePattern;
    private static Matcher mHostnameMatcher;

    /*
     * Allow also hostnames like: localse?ver example*.com exam?le*.host.org
     */
    static final private String WHITELIST_HOSTNAME_REGEX = "[a-zA-Z0-9\\*\\?]|[a-zA-Z0-9\\*\\?][a-zA-Z0-9\\-\\_\\.\\*\\?]{0,61}[a-zA-Z0-9\\*\\?]";
    private static Pattern mWhitelistHostnamePattern;
    private static Matcher mWhitelistHostnameMatcher;

    /*
     * http://stackoverflow.com/questions/46146/what-are-the-java-regular-expressions-for-matching-ipv4
     * -and-ipv6-strings
     */
    static final private String IPV4_REGEX = "(?:25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}";
    private static Pattern mIPv4Pattern;
    private static Matcher mIPv4Matcher;

    /*
     * http://forums.dartware.com/viewtopic.php?t=452
     */
    static final private String IPV6_REGEX = "(((?=(?>.*?::)(?!.*::)))(::)?([0-9A-F]{1,4}::?){0,5}|([0-9A-F]{1,4}:){6})(\2([0-9A-F]{1,4}(::?|$)){0,2}|((25[0-5]|(2[0-4]|1\\d|[1-9])?\\d)(\\.|$)){4}|[0-9A-F]{1,4}:[0-9A-F]{1,4})(?<![^:]:|\\.)";
    private static Pattern mIPv6Pattern;
    private static Matcher mIPv6Matcher;

    /*
     * To find hostname in DNS log
     */
    static final private String TCPDUMP_HOSTNAME_REGEX = "(A\\?|AAAA\\?)\\s(\\S+)\\.\\s";
    private static Pattern mTcpdumpHostnamePattern;
    private static Matcher mTcpdumpHostnameMatcher;

    /*
     * Simplified expression to parse lines in hosts files from hosts sources
     */
    static final private String SIMPLE_IPV6_REGEX = "[0-9A-F\\:\\.]+";

    static final private String HOSTS_PARSER = "^\\s*((?:" + IPV4_REGEX + ")|(?:"
            + SIMPLE_IPV6_REGEX + "))\\s+(" + HOSTNAME_REGEX + ")\\s*(?:\\#.*)*\\s*$";
    public static Pattern hostsParserPattern;

    // with whitelist entries for import function
    static final private String HOSTS_PARSER_WHITELIST_IMPORT = "^\\s*((?:" + IPV4_REGEX + ")|(?:"
            + SIMPLE_IPV6_REGEX + ")|(?:" + Constants.WHITELIST_ENTRY + "))\\s+("
            + WHITELIST_HOSTNAME_REGEX + ")\\s*(?:\\#.*)*\\s*$";
    public static Pattern hostsParserWhitelistImportPattern;

    static {
        mHostnamePattern = Pattern.compile(HOSTNAME_REGEX);
        mWhitelistHostnamePattern = Pattern.compile(WHITELIST_HOSTNAME_REGEX);
        mIPv4Pattern = Pattern.compile(IPV4_REGEX);
        mIPv6Pattern = Pattern.compile(IPV6_REGEX, Pattern.CASE_INSENSITIVE);
        mTcpdumpHostnamePattern = Pattern.compile(TCPDUMP_HOSTNAME_REGEX);
        hostsParserPattern = Pattern.compile(HOSTS_PARSER, Pattern.CASE_INSENSITIVE);
        hostsParserWhitelistImportPattern = Pattern.compile(HOSTS_PARSER_WHITELIST_IMPORT,
                Pattern.CASE_INSENSITIVE);
    }

    /**
     * Just a wrapper
     *
     * @param input
     * @return
     */
    static public boolean isValidUrl(String input) {
        return URLUtil.isValidUrl(input);
    }

    /**
     * I could not find any android class that provides checking of an hostnames, thus I am using
     * regex
     *
     * @param input
     * @return return true if input is valid hostname
     */
    static public boolean isValidHostname(String input) {
        mHostnameMatcher = mHostnamePattern.matcher(input);

        try {
            return mHostnameMatcher.matches();
        } catch (Exception e) {
            Log.e(Constants.TAG, "Error in isValidHostname", e);
            // workaround for some devices that throws jni exceptions: just accept everything
            return true;
        }
    }

    /**
     * Same as above but also allow * and ?
     *
     * @param input
     * @return
     */
    static public boolean isValidWhitelistHostname(String input) {
        mWhitelistHostnameMatcher = mWhitelistHostnamePattern.matcher(input);

        try {
            return mWhitelistHostnameMatcher.matches();
        } catch (Exception e) {
            Log.e(Constants.TAG, "Error in isValidHostname", e);
            // workaround for some devices that throws jni exceptions: just accept everything
            return true;
        }
    }

    /**
     * Check if input is a valid IPv4 address
     */
    static public boolean isValidIPv4(String input) {
        mIPv4Matcher = mIPv4Pattern.matcher(input);

        try {
            return mIPv4Matcher.matches();
        } catch (Exception e) {
            Log.e(Constants.TAG, "Error in isValidIPv4", e);
            // workaround for some devices that throws jni exceptions: just accept everything
            return true;
        }
    }

    /**
     * Check if input is a valid IPv6 address
     */
    static public boolean isValidIPv6(String input) {
        mIPv6Matcher = mIPv6Pattern.matcher(input);

        try {
            return mIPv6Matcher.matches();
        } catch (Exception e) {
            Log.e(Constants.TAG, "Error in isValidIPv6", e);
            // workaround for some devices that throws jni exceptions: just accept everything
            return true;
        }
    }

    /**
     * Check if input is a valid IP address
     */
    static public boolean isValidIP(String input) {
        Log.d(Constants.TAG, "input: " + input);
        Log.d(Constants.TAG, "isvalidipv4: " + isValidIPv4(input));
        Log.d(Constants.TAG, "isvalidipv6: " + isValidIPv6(input));

        return (isValidIPv4(input) || isValidIPv6(input));
    }

    /**
     * Gets hostname out of tcpdump log line
     *
     * @param input one line from dns log
     * @return
     */
    static public String getTcpdumpHostname(String input) {
        mTcpdumpHostnameMatcher = mTcpdumpHostnamePattern.matcher(input);

        try {
            if (mTcpdumpHostnameMatcher.find()) {
                return mTcpdumpHostnameMatcher.group(2);
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
