package org.adaway.model.hostsinstall;

/**
 * This enumeration represents the hosts installation status.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public enum HostsInstallStatus {
    /**
     * The hosts file is installed.
     */
    INSTALLED,
    /**
     * The hosts file is outdated.
     */
    OUTDATED,
    /**
     * The hosts file is the system original file.
     */
    ORIGINAL,
    /**
     * The hosts file is being installed or updated.
     */
    WORK_IN_PROGRESS
}
