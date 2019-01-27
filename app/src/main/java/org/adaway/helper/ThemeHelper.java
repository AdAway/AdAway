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
        if (!PreferenceHelper.getDarkTheme(activity.getApplicationContext())) {
            activity.setTheme(R.style.Theme_AdAway_Light);
        }
    }
}
