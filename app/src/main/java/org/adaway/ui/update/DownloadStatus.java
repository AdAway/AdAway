package org.adaway.ui.update;

import android.content.Context;
import android.text.format.Formatter;

import org.adaway.R;

/**
 * This class represents the application update download status.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class DownloadStatus {
    /**
     * The downloaded bytes.
     */
    final long downloaded;
    /**
     * The total bytes to download.
     */
    final long total;
    /**
     * The download progress percent.
     */
    final int progress;

    /**
     * Constructor.
     *
     * @param downloaded The downloaded bytes.
     * @param total      The total bytes to download.
     */
    DownloadStatus(long downloaded, long total) {
        this.downloaded = downloaded;
        this.total = total;
        this.progress = (int) (downloaded * 100L / total);
    }

    /**
     * Format status to string.
     *
     * @param context The application context.
     * @return The formatted status.
     */
    String format(Context context) {
        return context.getString(
                R.string.update_progress_label,
                Formatter.formatFileSize(context, this.downloaded),
                Formatter.formatFileSize(context, this.total)
        );
    }
}
