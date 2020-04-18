package org.adaway.ui.prefs.exclusion;

import android.graphics.drawable.Drawable;

/**
 * This class represents an installed user application to exclude / include into the VPN.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class UserApp {
    final CharSequence name;
    final CharSequence packageName;
    final Drawable icon;
    boolean excluded;

    UserApp(CharSequence name, CharSequence packageName, Drawable icon, boolean excluded) {
        this.name = name;
        this.packageName = packageName;
        this.icon = icon;
        this.excluded = excluded;
    }
}
