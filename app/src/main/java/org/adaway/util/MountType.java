package org.adaway.util;

/**
 * This class is an enum to define mount type.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public enum MountType {
    /**
     * Mount as read only.
     */
    READ_ONLY("ro"),
    /**
     * Mount as read/write.
     */
    READ_WRITE("rw");

    private final String option;

    MountType(String option) {
        this.option = option;
    }

    /**
     * Get related command line option.
     * @return The related command line option.
     */
    public String getOption() {
        return this.option;
    }
}
