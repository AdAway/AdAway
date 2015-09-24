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

package org.adaway.helper;

import org.adaway.R;
import org.adaway.util.Constants;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceHelper {
    public static boolean getUpdateCheck(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME,
                Context.MODE_PRIVATE);
        return prefs.getBoolean(context.getString(R.string.pref_update_check_key),
                Boolean.parseBoolean(context.getString(R.string.pref_update_check_def)));
    }

    public static boolean getNeverReboot(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME,
                Context.MODE_PRIVATE);
        return prefs.getBoolean(context.getString(R.string.pref_never_reboot_key),
                Boolean.parseBoolean(context.getString(R.string.pref_never_reboot_def)));
    }

    public static void setNeverReboot(Context context, boolean value) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(context.getString(R.string.pref_never_reboot_key), value);
        editor.commit();
    }

    public static boolean getEnableIpv6(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME,
                Context.MODE_PRIVATE);
        return prefs.getBoolean(context.getString(R.string.pref_enable_ipv6_key),
                Boolean.parseBoolean(context.getString(R.string.pref_enable_ipv6_def)));
    }

    public static boolean getUpdateCheckDaily(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME,
                Context.MODE_PRIVATE);
        return prefs.getBoolean(context.getString(R.string.pref_update_check_daily_key),
                Boolean.parseBoolean(context.getString(R.string.pref_update_check_daily_def)));
    }

    public static boolean getAutomaticUpdateDaily(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME,
                Context.MODE_PRIVATE);
        return prefs.getBoolean(context.getString(R.string.pref_automatic_update_daily_key),
                Boolean.parseBoolean(context.getString(R.string.pref_automatic_update_daily_def)));
    }

    public static boolean getUpdateOnlyOnWifi(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME,
                Context.MODE_PRIVATE);
        return prefs.getBoolean(context.getString(R.string.pref_update_only_on_wifi_key),
                Boolean.parseBoolean(context.getString(R.string.pref_update_only_on_wifi_def)));
    }

    public static boolean getWhitelistRules(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME,
                Context.MODE_PRIVATE);
        return prefs.getBoolean(context.getString(R.string.pref_whitelist_rules_key),
                Boolean.parseBoolean(context
                        .getString(R.string.pref_whitelist_rules_def))
        );
    }

    public static boolean getRedirectionRules(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME,
                Context.MODE_PRIVATE);
        return prefs.getBoolean(context.getString(R.string.pref_redirection_rules_key),
                Boolean.parseBoolean(context
                        .getString(R.string.pref_redirection_rules_def))
        );
    }

    public static String getRedirectionIP(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME,
                Context.MODE_PRIVATE);
        return prefs.getString(context.getString(R.string.pref_redirection_ip_key),
                context.getString(R.string.pref_redirection_ip_def));
    }

    public static String getApplyMethod(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME,
                Context.MODE_PRIVATE);
        return prefs.getString(context.getString(R.string.pref_apply_method_key),
                context.getString(R.string.pref_apply_method_def));
    }

    public static String getCustomTarget(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME,
                Context.MODE_PRIVATE);
        return prefs.getString(context.getString(R.string.pref_custom_target_key),
                context.getString(R.string.pref_custom_target_def));
    }

    public static boolean getWebserverEnabled(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(
                Constants.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(context.getString(R.string.pref_webserver_enabled_key),
                Boolean.parseBoolean(context.getString(R.string.pref_webserver_enabled_def)));
    }

    public static boolean getWebserverOnBoot(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME,
                Context.MODE_PRIVATE);
        return prefs.getBoolean(context.getString(R.string.pref_webserver_on_boot_key),
                Boolean.parseBoolean(context.getString(R.string.pref_webserver_on_boot_def)));
    }

    public static boolean getDebugEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME,
                Context.MODE_PRIVATE);
        return prefs.getBoolean(context.getString(R.string.pref_enable_debug_key),
                Boolean.parseBoolean(context.getString(R.string.pref_enable_debug_def)));
    }
}
