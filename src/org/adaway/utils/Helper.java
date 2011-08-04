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

import java.text.DateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.os.StatFs;
import android.util.Log;
import android.webkit.URLUtil;

public class Helper {
    /*
     * http://stackoverflow.com/questions/106179/regular-expression-to-match-hostname-or-ip-address/
     * 3824105#3824105 with added underscore to match more hosts
     */
    static final private String HOSTNAME_REGEX = "^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-\\_]{0,61}[a-zA-Z0-9])\\.)+([a-zA-Z0-9]{2,5})$";
    private static Pattern mHostnamePattern;
    private static Matcher mHostnameMatcher;

    /*
     * http://stackoverflow.com/questions/46146/what-are-the-java-regular-expressions-for-matching-ipv4
     * -and-ipv6-strings
     */
    static final private String IPV4_REGEX = "\\A(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}\\z";
    private static Pattern mIPv4Pattern;
    private static Matcher mIPv4Matcher;

    /*
     * http://forums.dartware.com/viewtopic.php?t=452
     */
    static final private String IPV6_REGEX = "^(((?=(?>.*?::)(?!.*::)))(::)?([0-9A-F]{1,4}::?){0,5}|([0-9A-F]{1,4}:){6})(\2([0-9A-F]{1,4}(::?|$)){0,2}|((25[0-5]|(2[0-4]|1\\d|[1-9])?\\d)(\\.|$)){4}|[0-9A-F]{1,4}:[0-9A-F]{1,4})(?<![^:]:|\\.)\\z";
    private static Pattern mIPv6Pattern;
    private static Matcher mIPv6Matcher;

    static {
        mHostnamePattern = Pattern.compile(HOSTNAME_REGEX);
        mIPv4Pattern = Pattern.compile(IPV4_REGEX);
        mIPv6Pattern = Pattern.compile(IPV6_REGEX, Pattern.CASE_INSENSITIVE);
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

        return mHostnameMatcher.find();
    }

    /**
     * Check if input is a valid IPv4 address
     */
    static public boolean isValidIPv4(String input) {
        mIPv4Matcher = mIPv4Pattern.matcher(input);

        return mIPv4Matcher.find();
    }

    /**
     * Check if input is a valid IPv6 address
     */
    static public boolean isValidIPv6(String input) {
        mIPv6Matcher = mIPv6Pattern.matcher(input);

        return mIPv6Matcher.find();
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
     * Check if there is enough space on internal partition
     * 
     * @param size
     *            size of file to put on partition
     * @param path
     *            path where to put the file
     * 
     * @return <code>true</code> if it will fit on partition of <code>path</code>,
     *         <code>false</code> if it will not fit.
     */
    public static boolean hasEnoughSpaceOnPartition(String path, long size) {
        StatFs stat = new StatFs(path);
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks();

        if (size < availableBlocks * blockSize) {
            return true;
        } else {
            Log.e(Constants.TAG, "Not enough space on partition!");
            return false;
        }
    }

    public static String longToDate(long input) {
        Date date = new Date(input);
        DateFormat dataformat = DateFormat.getDateInstance(DateFormat.LONG);

        return dataformat.format(date);
    }

    public static long getCurrentLongDate() {
        Date date = new Date();

        return date.getTime();
    }
}
