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

public class Constants {

    public static final String TAG = "AdAway";

    public static final String LOCALHOST_IPv4 = "127.0.0.1";
    public static final String LOCALHOST_HOSTNAME = "hostname";
    public static final String DOWNLOADED_HOSTS_FILENAME = "hosts_downloaded";
    public static final String HOSTS_FILENAME = "hosts";
    public static final String LINE_SEPERATOR = System.getProperty("line.separator");
    public static final String COMMAND_COPY = "cp -f";
    public static final String COMMAND_CHOWN = "chown 0:0";
    public static final String COMMAND_CHMOD = "chmod 644";
    public static final String ANDROID_HOSTS_PATH = "/system/etc";

}
