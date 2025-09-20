package org.adaway.ui.update;

import android.content.Context;

/**
 * This class represents the application update download status.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public interface DownloadStatus {
    /**
     * Get the download progress percent.
     */
    int getProgress();

    /**
     * Format status to string.
     *
     * @param context The application context.
     * @return The formatted status.
     */
    String format(Context context);
}
