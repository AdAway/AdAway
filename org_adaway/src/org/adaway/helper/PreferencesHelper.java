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

package org.adaway.helper;

import org.adaway.R;
import org.adaway.util.Constants;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferencesHelper {
    public static boolean getUpdateCheck(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, 0);
        return prefs.getBoolean(context.getString(R.string.pref_update_check_key),
                Boolean.parseBoolean(context.getString(R.string.pref_update_check_def)));
    }

    public static boolean getNeverReboot(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, 0);
        return prefs.getBoolean(context.getString(R.string.pref_never_reboot_key),
                Boolean.parseBoolean(context.getString(R.string.pref_never_reboot_def)));
    }

    public static void setNeverReboot(Context context, boolean value) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, 0);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(context.getString(R.string.pref_never_reboot_key), value);
        editor.commit();
    }

    public static String getRedirectionIP(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, 0);
        return prefs.getString(context.getString(R.string.pref_redirection_ip_key),
                context.getString(R.string.pref_redirection_ip_def));
    }

    public static String getApplyMethod(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, 0);
        return prefs.getString(context.getString(R.string.pref_apply_method_key),
                context.getString(R.string.pref_apply_method_def));
    }
}