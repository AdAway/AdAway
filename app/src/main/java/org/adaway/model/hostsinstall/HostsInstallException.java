package org.adaway.model.hostsinstall;

/**
 * This class in an {@link Exception} thrown on hosts install error.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class HostsInstallException extends Exception {

    private final HostsInstallError installError;

    /**
     * Constructor.
     *
     * @param installError The install error type.
     * @param message      The install error message.
     */
    HostsInstallException(HostsInstallError installError, String message) {
        super(message);
        this.installError = installError;
    }

    /**
     * Constructor.
     *
     * @param installError The install error type.
     * @param message      The install error message.
     * @param cause        The cause of this exception.
     */
    HostsInstallException(HostsInstallError installError, String message, Throwable cause) {
        super(message, cause);
        this.installError = installError;
    }

    public HostsInstallError getInstallError() {
        return this.installError;
    }
}
