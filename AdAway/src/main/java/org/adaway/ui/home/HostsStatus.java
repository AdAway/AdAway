package org.adaway.ui.home;

import java.io.Serializable;

/**
 * Created by jcdenton on 25/02/2018.
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
