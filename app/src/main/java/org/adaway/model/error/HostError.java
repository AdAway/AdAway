package org.adaway.model.error;

import androidx.annotation.StringRes;

import org.adaway.R;

/**
 * This enumeration represents the hosts error case.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public enum HostError {
    // Source model errors
    NO_CONNECTION(R.string.status_no_connection),
    DOWNLOAD_FAIL(R.string.status_download_fail_subtitle_new),
    // Host install model errors
    SYMLINK_MISSING(R.string.apply_symlink_missing_status),
    SYMLINK_FAILED(R.string.apply_symlink_fail_title),
    PRIVATE_FILE_FAIL(R.string.no_private_file_status),
    NOT_ENOUGH_SPACE(R.string.apply_not_enough_space_title),
    COPY_FAIL(R.string.apply_copy_fail_status),
    REVERT_FAIL(R.string.revert_failed_status),
    APPLY_FAIL(R.string.apply_fail_status),
    // VPN model error
    ENABLE_VPN_FAIL(R.string.enable_vpn_fail),
    DISABLE_VPN_FAIL(R.string.disable_vpn_fail),
    // TODO Check if used?
    APN_PROXY(0),
    REMOUNT_FAIL(0);

    @StringRes
    private final int messageKey;

    HostError(int messageKey) {
        this.messageKey = messageKey;
    }

    @StringRes
    public int getMessageKey() {
        return messageKey;
    }
}
