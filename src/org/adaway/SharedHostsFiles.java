package org.adaway;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

public class SharedHostsFiles {
    public final static String PREFS_NAME = "hostnames";

    public static String[] getHostnames(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
//        return prefs.getBoolean(context.getString(R.string.pref_key_https), false);
        return null; // TODO
    }

    // public static void setHttps(Context context, boolean newValue) {
    // SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
    // Editor prefsEditor = prefs.edit();
    // prefsEditor.putBoolean(context.getString(R.string.pref_key_https), newValue);
    // prefsEditor.commit();
    // }
}
