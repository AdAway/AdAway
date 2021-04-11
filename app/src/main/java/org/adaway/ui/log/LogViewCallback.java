package org.adaway.ui.log;

import androidx.annotation.NonNull;

import org.adaway.db.entity.ListType;

/**
 * This class is represents the {@link LogActivity} callback.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public interface LogViewCallback {
    /**
     * Add a {@link org.adaway.db.entity.HostListItem}.
     *
     * @param hostName The item host name.
     * @param type     The item type.
     */
    void addListItem(@NonNull String hostName, @NonNull ListType type);

    /**
     * Remove a {@link org.adaway.db.entity.HostListItem}
     *
     * @param hostName The item host name.
     */
    void removeListItem(@NonNull String hostName);

    /**
     * Open an host into the user browser.
     *
     * @param hostName The host name to open.
     */
    void openHostInBrowser(@NonNull String hostName);

    /**
     * Get color value from color identifier.
     *
     * @param colorId The color identifier.
     * @return The related color value.
     */
    int getColor(int colorId);
}
