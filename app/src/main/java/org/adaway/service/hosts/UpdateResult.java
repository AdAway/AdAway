package org.adaway.service.hosts;

import org.adaway.util.StatusCodes;

/**
 * This class represents the update result.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class UpdateResult {
    /**
     * The result code (from {@link StatusCodes}).
     */
    int mCode = StatusCodes.CHECKING;
    /**
     * The number of downloads.
     */
    int mNumberOfDownloads = 0;
    /**
     * The number of failed downloads.
     */
    int mNumberOfFailedDownloads = 0;

    @Override
    public String toString() {
        return "UpdateResult{" +
                "mCode=" + mCode +
                ", mNumberOfDownloads=" + mNumberOfDownloads +
                ", mNumberOfFailedDownloads=" + mNumberOfFailedDownloads +
                '}';
    }
}
