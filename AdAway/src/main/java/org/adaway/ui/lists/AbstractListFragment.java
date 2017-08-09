/*
 * Copyright (C) 2011-2012 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This file is part of AdAway.
 * 
 * AdAway is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AdAway is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AdAway.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.adaway.ui.lists;

import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ListView;

import org.adaway.R;
import org.adaway.util.Constants;
import org.adaway.util.Log;

/**
 * This class is a {@link ListFragment} to display and manage lists of {@link ListsFragment}.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public abstract class AbstractListFragment extends ListFragment implements
        LoaderManager.LoaderCallbacks<Cursor> {
    /**
     * The list cursor adapter.
     */
    protected CursorAdapter mAdapter;
    /**
     * The position of current list item (<code>-1</code> if no current list item).
     */
    protected int mCurrentListItemPosition = -1;
    /**
     * The current action mode when item is selection (<code>null</code> if no action started).
     */
    protected ActionMode mActionMode;

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final FragmentActivity activity = this.getActivity();
        /*
         * Configure list.
         */
        // Get list view
        final ListView listView = this.getListView();
        // Give some text to display if there is no data.
        this.setEmptyText(getString(R.string.checkbox_list_empty) + "\n\n"
                + getString(R.string.checkbox_list_empty_text));
        // Start out with a progress indicator
        this.setListShown(false);
        /*
         * Create action mode.
         */
        // Create action mode callback to display edit/delete menu
        final ActionMode.Callback callback = new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
                // Get menu inflater
                MenuInflater inflater = actionMode.getMenuInflater();
                // Set action mode title
                actionMode.setTitle(R.string.checkbox_list_context_title);
                // Inflate edit/delete menu
                inflater.inflate(R.menu.checkbox_list_context, menu);
                // Return action created
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
                // Nothing special to do
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode actionMode, MenuItem item) {
                // Check item identifier
                switch (item.getItemId()) {
                    case R.id.checkbox_list_context_edit:
                        AbstractListFragment.this.editItem();
                        return true;
                    case R.id.checkbox_list_context_delete:
                        AbstractListFragment.this.deleteItem();
                        return true;
                    default:
                        return false;
                }
            }

            @Override
            public void onDestroyActionMode(ActionMode actionMode) {
                // Get current list item child view
                View childView = listView.getChildAt(AbstractListFragment.this.mCurrentListItemPosition);
                // Clear background color
                childView.setBackgroundColor(Color.TRANSPARENT);
                // Clear current list item position
                AbstractListFragment.this.mCurrentListItemPosition = -1;
                // Clear action mode
                AbstractListFragment.this.mActionMode = null;
            }
        };
        // Set item long click listener to start action
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                // Check if there is already a current action
                if (AbstractListFragment.this.mActionMode != null) {
                    return false;
                }
                // Store current list item position
                AbstractListFragment.this.mCurrentListItemPosition = position;
                // Start action mode and store it
                AbstractListFragment.this.mActionMode = activity.startActionMode(callback);
                // Get current item background color
                int currentItemBackgroundColor = AbstractListFragment.this.getResources().getColor(R.color.selected_background);
                // Apply background color to current item view
                view.setBackgroundColor(currentItemBackgroundColor);
                // Return event consumed
                return true;
            }
        });
        /*
         * Load data.
         */
        // Create cursor adapter
        this.mAdapter = this.getCursorAdapter();
        // Bind adapter to list
        this.setListAdapter(this.mAdapter);
        // Prepare the loader. Either re-connect with an existing one, or start a new one.
        this.getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        // Get checkbox
        CheckBox checkBox = v.findViewWithTag(ListsCursorAdapter.ENABLED_CHECKBOX_TAG);
        if (checkBox == null) {
            Log.w(Constants.TAG, "Checkbox could not be found for list entry.");
            return;
        }
        // Get current status
        boolean checked = checkBox.isChecked();
        // Set new status
        checkBox.setChecked(!checked);
        // Enable item
        this.enableItem(id, !checked);
    }

    /*
     * LoaderCallback.
     */

    @Override
    public abstract Loader<Cursor> onCreateLoader(int id, Bundle args);

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // Swap the new cursor in.
        // (The framework will take care of closing the old cursor once we return.)
        this.mAdapter.swapCursor(data);
        // The list should now be shown.
        if (isResumed()) {
            setListShown(true);
        } else {
            setListShownNoAnimation(true);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // This is called when the last Cursor provided to onLoadFinished()
        // above is about to be closed. We need to make sure we are no longer using it.
        this.mAdapter.swapCursor(null);
    }

    /**
     * Add a new item.
     */
    protected abstract void addItem();

    /**
     * Enable or not a list item.
     *
     * @param itemId  The item identifier.
     * @param enabled The item enable state (<code>true</code> if enabled, <code>false</code> otherwise).
     */
    protected abstract void enableItem(long itemId, boolean enabled);

    /**
     * Edit the selected list item.
     */
    protected void editItem() {
        // Check current list item position
        if (this.mCurrentListItemPosition == -1) {
            return;
        }
        // Get current list item identifier
        final long itemId = this.mAdapter.getItemId(this.mCurrentListItemPosition);
        // Get current list item view
        ListView listView = this.getListView();
        int firstPosition = listView.getFirstVisiblePosition() - listView.getHeaderViewsCount();
        int wantedChild = this.mCurrentListItemPosition - firstPosition;
        if (wantedChild < 0 || wantedChild >= listView.getChildCount()) {
            Log.w(Constants.TAG, "Unable to get view for desired position, because it's not being displayed on screen.");
            return;
        }
        View itemView = listView.getChildAt(wantedChild);
        // Edit entry
        this.editItem(itemId, itemView);
        // Finish action mode
        this.mActionMode.finish();
    }

    /**
     * Edit a list item from its identifier and view.
     *
     * @param itemId   The item identifier.
     * @param itemView The item view.
     */
    protected abstract void editItem(long itemId, View itemView);

    /**
     * Delete the selected list item.
     */
    protected void deleteItem() {
        // Check current list item position
        if (this.mCurrentListItemPosition == -1) {
            return;
        }
        // Get current list item identifier
        long itemId = this.mAdapter.getItemId(this.mCurrentListItemPosition);
        // Delete item
        this.deleteItem(itemId);
        // Finish action mode
        this.mActionMode.finish();
    }

    /**
     * Delete a list item from its identifier.
     *
     * @param itemId The item identifier.
     */
    protected abstract void deleteItem(long itemId);

    /**
     * Get the cursor adapter for the list view.
     *
     * @return The cursor adapter for the list view.
     */
    protected abstract CursorAdapter getCursorAdapter();
}