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

package org.adaway.util;

/**
 * Wraps Android Logging to enable or disable debug output using Constants
 * 
 */
public final class Log {

    public static int v(String tag, String msg) {
        return android.util.Log.v(tag, msg);
    }

    public static int v(String tag, String msg, Throwable tr) {
        return android.util.Log.v(tag, msg, tr);
    }

    public static int d(String tag, String msg) {
        if (Constants.DEBUG) {
            return android.util.Log.d(tag, msg);
        } else {
            return 0;
        }
    }

    public static int d(String tag, String msg, Throwable tr) {
        if (Constants.DEBUG) {
            return android.util.Log.d(tag, msg, tr);
        } else {
            return 0;
        }
    }

    public static int i(String tag, String msg) {
        return android.util.Log.i(tag, msg);
    }

    public static int i(String tag, String msg, Throwable tr) {
        return android.util.Log.i(tag, msg, tr);
    }

    public static int w(String tag, String msg) {
        return android.util.Log.w(tag, msg);
    }

    public static int w(String tag, String msg, Throwable tr) {
        return android.util.Log.w(tag, msg, tr);
    }

    public static int w(String tag, Throwable tr) {
        return android.util.Log.w(tag, tr);
    }

    public static int e(String tag, String msg) {
        return android.util.Log.e(tag, msg);
    }

    public static int e(String tag, String msg, Throwable tr) {
        return android.util.Log.e(tag, msg, tr);
    }

}
