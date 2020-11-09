package org.adaway.ui.hosts;

import android.content.Context;

import org.adaway.db.entity.HostsSource;

/**
 * This class is represents the {@link HostsSourcesFragment} callback.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public interface HostsSourcesViewCallback {
    /**
     * Get the application context.
     *
     * @return The application context.
     */
    Context getContext();

    /**
     * Toggle host source enable status.
     *
     * @param source The hosts source to toggle status.
     */
    void toggleEnabled(HostsSource source);

    /**
     * Start an action.
     *
     * @param source     The hosts source to start the action.
     */
    void edit(HostsSource source);
}
