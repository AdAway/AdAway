package org.adaway.ui.hosts;

import android.view.View;

import org.adaway.db.entity.HostsSource;

/**
 * This class is represents the {@link HostsSourcesFragment} callback.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public interface HostsSourcesViewCallback {
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
     * @param sourceView The hosts source related view.
     * @return <code>true</code> if the action was started, <code>false</code> otherwise.
     */
    boolean startAction(HostsSource source, View sourceView);
}
