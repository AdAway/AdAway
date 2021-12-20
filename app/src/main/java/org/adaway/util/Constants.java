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

public class Constants {
    public static final String TAG = "AdAway";

    public static final String PREFS_NAME = "preferences";

    public static final String LOCALHOST_IPV4 = "127.0.0.1";
    public static final String LOCALHOST_IPV6 = "::1";
    public static final String BOGUS_IPV4 = "0.0.0.0";
    public static final String LOCALHOST_HOSTNAME = "localhost";

    public static final String HOSTS_FILENAME = "hosts";
    public static final String DEFAULT_HOSTS_FILENAME = "default_hosts";
    public static final String LINE_SEPARATOR = System.getProperty("line.separator", "\n");
    public static final String FILE_SEPARATOR = System.getProperty("file.separator", "/");

    public static final String COMMAND_CHOWN = "chown 0:0";
    public static final String COMMAND_CHMOD_644 = "chmod 644";

    public static final String ANDROID_SYSTEM_PATH = System.getProperty("java.home", "/system");
    public static final String ANDROID_SYSTEM_ETC_HOSTS = ANDROID_SYSTEM_PATH + FILE_SEPARATOR
            + "etc" + FILE_SEPARATOR + HOSTS_FILENAME;
}
