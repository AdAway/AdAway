package org.adaway.ui.home;

import java.io.Serializable;

/**
 * This enumerate represents the status of the hosts file.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
enum HostsStatus implements Serializable {
    /**
     * Hosts file is installed.
     */
    ENABLED,
    /**
     * Update is available for hosts file.
     */
    UPDATE_AVAILABLE,
    /**
     * Hosts file is not installed.
     */
    DISABLED
}
