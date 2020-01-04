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
    NO_CONNECTION(R.string.error_no_connection_message, R.string.error_no_connection_details),
    DOWNLOAD_FAILED(R.string.error_download_failed_message, R.string.error_no_connection_details),
    // Host install model errors
    PRIVATE_FILE_FAILED(R.string.error_private_file_failed_message, R.string.error_private_file_failed_details),
    NOT_ENOUGH_SPACE(R.string.error_not_enough_space_message, R.string.error_not_enough_space_details),
    COPY_FAIL(R.string.error_copy_failed_message, R.string.error_copy_failed_details),
    REVERT_FAIL(R.string.error_revert_failed_message, R.string.error_revert_failed_details),
    // VPN model error
    ENABLE_VPN_FAIL(R.string.error_enable_vpn_failed_message, R.string.error_enable_vpn_failed_details),
    DISABLE_VPN_FAIL(R.string.error_disable_vpn_failed_message, R.string.error_disable_vpn_failed_details);

    @StringRes
    private final int messageKey;
    @StringRes
    private final int detailsKey;

    HostError(int messageKey, int detailsKey) {
        this.messageKey = messageKey;
        this.detailsKey = detailsKey;
    }

    @StringRes
    public int getMessageKey() {
        return this.messageKey;
    }

    @StringRes
    public int getDetailsKey() {
        return this.detailsKey;
    }
}
