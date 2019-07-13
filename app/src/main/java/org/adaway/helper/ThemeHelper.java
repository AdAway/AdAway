package org.adaway.helper;

import androidx.appcompat.app.AppCompatActivity;

import org.adaway.R;

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
     * @param activity The activity to apply theme.
     */
    public static void applyTheme(AppCompatActivity activity) {
        applyTheme(activity, true);
    }

    /**
     * Apply the user selected theme.
     *
     * @param activity  The activity to apply theme.
     * @param actionBar {@code true} to enable action bar, {@code false} to disable it.
     */
    public static void applyTheme(AppCompatActivity activity, boolean actionBar) {
        if (!PreferenceHelper.getDarkTheme(activity.getApplicationContext())) {
            if (actionBar) {
                activity.setTheme(R.style.Theme_AdAway_Light);
            } else {
                activity.setTheme(R.style.Theme_AdAway_Light_NoActionBar);
            }
        } else if (!actionBar) {
            activity.setTheme(R.style.Theme_AdAway_Dark_NoActionBar);
        }
    }
}
