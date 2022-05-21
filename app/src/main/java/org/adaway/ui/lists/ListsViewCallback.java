package org.adaway.ui.lists;

import android.view.View;

import org.adaway.db.entity.HostListItem;
import org.adaway.ui.lists.type.AbstractListFragment;

/**
 * This class is represents the {@link AbstractListFragment} callback.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public interface ListsViewCallback {
    /**
     * Toggle item enable status.
     *
     * @param item The list to toggle status.
     */
    void toggleItemEnabled(HostListItem item);

    /**
     * Start an action.
     *
     * @param item       The list to start the action.
     * @param sourceView The list related view.
     * @return <code>true</code> if the action was started, <code>false</code> otherwise.
     */
    boolean startAction(HostListItem item, View sourceView);

    /**
     * Copy an hosts into clipboard.
     *
     * @param item The list to copy hosts.
     */
    boolean copyHostToClipboard(HostListItem item);
}
