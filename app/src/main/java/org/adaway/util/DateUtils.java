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

import android.content.Context;
import android.content.res.Resources;

import org.adaway.R;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

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

    /**
     * Get the approximate delay from a date to now.
     *
     * @param context The application context.
     * @param from    The date from which computes the delay.
     * @return The approximate delay.
     */
    public static String getApproximateDelay(Context context, Date from) {
        // Get resource for plurals
        Resources resources = context.getResources();
        // Get current date
        Date date = new Date();
        // Get delay between from and now in minutes
        long delay = (date.getTime() - from.getTime()) / (1000 * 60);
        // Check if delay is lower than an hour
        if (delay < 60) {
            int minutes = (int) delay;
            return resources.getQuantityString(R.plurals.hosts_source_minutes, minutes, minutes);
        }
        // Get delay in hours
        delay /= 60;
        // Check if delay is lower than a day
        if (delay < 24) {
            int hours = (int) delay;
            return resources.getQuantityString(R.plurals.hosts_source_hours, hours, hours);
        }
        // Get delay in days
        delay /= 24;
        // Check if delay is lower than a month
        if (delay < 30) {
            int days = (int) delay;
            return resources.getQuantityString(R.plurals.hosts_source_days, days, days);
        }
        // Get delay in months
        int months = (int) delay / 30;
        return resources.getQuantityString(R.plurals.hosts_source_months, months, months);
    }
}
