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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Helper {
    /*
     * http://stackoverflow.com/questions/106179/regular-expression-to-match-hostname-or-ip-address/
     * 3824105#3824105 with added underscore to match more hosts
     */
    static final private String mHostnameRegex = "^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-\\_]{0,61}[a-zA-Z0-9])\\.)+([a-zA-Z0-9]{2,5})$";
    private static Pattern mHostnamePattern;

    private static Matcher mHostnameMatcher;

    static {
        mHostnamePattern = Pattern.compile(mHostnameRegex);
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
}
