package org.adaway;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

public class SharedPrefs {
    public final static String PREFS_NAME = "preferences";

    public static boolean getHttps(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        return prefs.getBoolean(context.getString(R.string.pref_key_https), Boolean.parseBoolean(context.getString(R.string.pref_def_https)));
    }

    public static boolean getCheckSyntax(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        return prefs.getBoolean(context.getString(R.string.pref_key_check_syntax), Boolean.parseBoolean(context.getString(R.string.pref_def_check_syntax)));

    }

    public static String getRedirectionIP(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        return prefs.getString(context.getString(R.string.pref_redirection_ip), context.getString(R.string.pref_def_redirection_ip));
    }

    // public static void setHttps(Context context, boolean newValue) {
    // SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
    // Editor prefsEditor = prefs.edit();
    // prefsEditor.putBoolean(context.getString(R.string.pref_key_https), newValue);
    // prefsEditor.commit();
    // }
}