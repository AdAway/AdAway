package org.adaway.model.hostsinstall;

/**
 * This enumeration represents the hosts install error case.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public enum HostsInstallError {
    PRIVATE_FILE_FAIL,
    DOWNLOAD_FAIL,
    NO_CONNECTION,
    APPLY_FAIL,
    SYMLINK_MISSING,
    NOT_ENOUGH_SPACE,
    REMOUNT_FAIL,
    COPY_FAIL,
    REVERT_FAIL,
    APN_PROXY,
    SYMLINK_FAILED,
}
