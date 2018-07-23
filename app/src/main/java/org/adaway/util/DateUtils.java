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

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.adaway.R;

import android.content.Context;

public class DateUtils {
    /**
     * Builds date string out of long value containing unix date
     *
     * @param input
     * @return formatted date string
     */
    public static String longToDateString(Context context, long input) {
        if (input == 0) {
            return context.getString(R.string.hosts_not_available);
        } else {
            Date date = new Date(input);
            DateFormat dateformat = DateFormat.getDateInstance(DateFormat.MEDIUM);

            return dateformat.format(date);
        }
    }

    /**
     * Builds date string out of long value containing unix date
     *
     * @param date
     * @return formatted date string
     */
    public static String dateToString(Context context, Date date) {
        if (date == null) {
            return context.getString(R.string.hosts_not_available);
        } else {
            DateFormat dateformat = DateFormat.getDateInstance(DateFormat.MEDIUM);
            return dateformat.format(date);
        }
    }

    /**
     * Returns current unix date in GMT as long value
     *
     * @return current date as long
     */
    public static long getCurrentLongDate() {
        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"));

        return cal.getTime().getTime();
    }
}
