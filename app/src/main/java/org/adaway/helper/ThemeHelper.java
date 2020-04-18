package org.adaway.helper;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;

/**
 * This class is a helper to apply user selected theme on the application activity.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public final class ThemeHelper {

    /**
     * Private constructor.
     */
    private ThemeHelper() {

    }

    /**
     * Apply the user selected theme.
     *
     * @param context The context to apply theme.
     */
    public static void applyTheme(Context context) {
        AppCompatDelegate.setDefaultNightMode(
                PreferenceHelper.getDarkTheme(context) ? MODE_NIGHT_YES : MODE_NIGHT_NO
        );
    }
}
