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

import android.content.Context;
import android.content.SharedPreferences;

import org.adaway.R;
import org.adaway.util.Constants;

public class PreferenceHelper {
    public static boolean getDismissWelcome(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_dismiss_welcome_key),
                context.getResources().getBoolean(R.bool.pref_dismiss_welcome_def)
        );
    }

    public static boolean getDarkTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_dark_theme_key),
                context.getResources().getBoolean(R.bool.pref_dark_theme_def)
        );
    }

    public static boolean getUpdateCheck(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_update_check_key),
                context.getResources().getBoolean(R.bool.pref_update_check_def)
        );
    }

    public static boolean getNeverReboot(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_never_reboot_key),
                context.getResources().getBoolean(R.bool.pref_never_reboot_def)
        );
    }

    public static void setNeverReboot(Context context, boolean value) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(context.getString(R.string.pref_never_reboot_key), value);
        editor.apply();
    }

    /*
     * Not used. Defined by AbstractSystemlessMode.isEnabled().
     */
    public static boolean getSystemlessMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_enable_systemless_key),
                context.getResources().getBoolean(R.bool.pref_enable_systemless_def)
        );
    }

    public static boolean getEnableIpv6(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_enable_ipv6_key),
                context.getResources().getBoolean(R.bool.pref_enable_ipv6_def)
        );
    }

    public static boolean getUpdateCheckDaily(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_update_check_daily_key),
                context.getResources().getBoolean(R.bool.pref_update_check_daily_def)
        );
    }

    public static boolean getAutomaticUpdateDaily(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_automatic_update_daily_key),
                context.getResources().getBoolean(R.bool.pref_automatic_update_daily_def)
        );
    }

    public static boolean getUpdateOnlyOnWifi(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_update_only_on_wifi_key),
                context.getResources().getBoolean(R.bool.pref_update_only_on_wifi_def)
        );
    }

    public static boolean getWhitelistRules(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_whitelist_rules_key),
                context.getResources().getBoolean(R.bool.pref_whitelist_rules_def)
        );
    }

    public static boolean getRedirectionRules(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_redirection_rules_key),
                context.getResources().getBoolean(R.bool.pref_redirection_rules_def)
        );
    }

    public static String getRedirectionIP(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getString(
                context.getString(R.string.pref_redirection_ip_key),
                context.getString(R.string.pref_redirection_ip_def)
        );
    }

    public static String getApplyMethod(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getString(
                context.getString(R.string.pref_apply_method_key),
                context.getString(R.string.pref_apply_method_def)
        );
    }

    public static String getCustomTarget(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getString(
                context.getString(R.string.pref_custom_target_key),
                context.getString(R.string.pref_custom_target_def)
        );
    }

    public static boolean getWebServerEnabled(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_webserver_enabled_key),
                context.getResources().getBoolean(R.bool.pref_webserver_enabled_def)
        );
    }

    public static boolean getWebServerOnBoot(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_webserver_on_boot_key),
                context.getResources().getBoolean(R.bool.pref_webserver_on_boot_def)
        );
    }

    public static boolean getDebugEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_enable_debug_key),
                context.getResources().getBoolean(R.bool.pref_enable_debug_def)
        );
    }

    public static boolean getTelemetryEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_enable_telemetry_key),
                context.getResources().getBoolean(R.bool.pref_enable_telemetry_def)
        );
    }

    public static void setTelemetryEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(context.getString(R.string.pref_enable_telemetry_key), enabled);
        editor.apply();
    }

    public static boolean getDisplayTelemetryConsent(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_display_telemetry_consent_key),
                context.getResources().getBoolean(R.bool.pref_display_telemetry_consent_def)
        );
    }

    public static void setDisplayTelemetryConsent(Context context, boolean displayTelemetryConsent) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(context.getString(R.string.pref_display_telemetry_consent_key), displayTelemetryConsent);
        editor.apply();
    }
}
