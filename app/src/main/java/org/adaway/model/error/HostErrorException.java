package org.adaway.model.error;

/**
 * This class in an {@link Exception} thrown on hosts  error.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class HostErrorException extends Exception {
    /**
     * The exception error type.
     */
    private final HostError error;

    /**
     * Constructor.
     *
     * @param error The exception error type.
     */
    public HostErrorException(HostError error) {
        super("An host error " + error.name() + " occurred");
        this.error = error;
    }

    /**
     * Constructor.
     *
     * @param error The exception error type.
     * @param cause The cause of this exception.
     */
    public HostErrorException(HostError error, Throwable cause) {
        super("An host error " + error.name() + " occurred", cause);
        this.error = error;
    }

    /**
     * Get the error type.
     *
     * @return The exception error type
     */
    public HostError getError() {
        return this.error;
    }
}
