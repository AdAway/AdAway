package org.adaway.ui.prefs.exclusion;

/**
 * This fragment is the preferences fragment for VPN ad blocker.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public interface ExcludedAppController {
    /**
     * Get installed user applications.
     * @return The installed user applications.
     */
    UserApp[] getUserApplications();

    /**
     * Exclude applications from VPN.
     * @param applications The applications to exclude.
     */
    void excludeApplications(UserApp... applications);

    /**
     * Include applications into VPN.
     * @param applications The application to include.
     */
    void includeApplications(UserApp... applications);
}
