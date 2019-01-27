package org.adaway.ui.adware;

import androidx.annotation.NonNull;

import java.util.HashMap;

/**
 * This class is a POJO to represent an installed adware.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class AdwareInstall extends HashMap<String, String> implements Comparable<AdwareInstall> {
    /**
     * The adware application name.
     */
    final static String APPLICATION_NAME_KEY = "app_name";
    /**
     * The adware package name.
     */
    final static String PACKAGE_NAME_KEY = "package_name";

    /**
     * Constructor.
     *
     * @param applicationName The adware application name.
     * @param packageName     The adware package name.
     */
    AdwareInstall(String applicationName, String packageName) {
        super(2);
        this.put(AdwareInstall.APPLICATION_NAME_KEY, applicationName);
        this.put(AdwareInstall.PACKAGE_NAME_KEY, packageName);
    }

    @Override
    public int compareTo(@NonNull AdwareInstall other) {
        int nameComparison = this.get(AdwareInstall.APPLICATION_NAME_KEY).compareTo(other.get(AdwareInstall.APPLICATION_NAME_KEY));
        if (nameComparison == 0) {
            return this.get(AdwareInstall.PACKAGE_NAME_KEY).compareTo(other.get(AdwareInstall.PACKAGE_NAME_KEY));
        } else {
            return nameComparison;
        }
    }
}
