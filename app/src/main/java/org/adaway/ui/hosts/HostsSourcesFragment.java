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

package org.adaway.ui.hosts;

import android.app.Activity;
import android.app.AlertDialog;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.Editable;
import android.text.InputType;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.adaway.R;
import org.adaway.provider.AdAwayContract.HostsSources;
import org.adaway.provider.ProviderHelper;
import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.adaway.util.RegexUtils;

/**
 * This class is a {@link Fragment} to display and manage hosts sources.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class HostsSourcesFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {
    /**
     * The rows to retrieve for the view.
     */
    private static final String[] HOSTS_SOURCES_SUMMARY_PROJECTION = new String[]{
            HostsSources._ID,
            HostsSources.URL,
            HostsSources.ENABLED,
            HostsSources.LAST_MODIFIED_LOCAL,
            HostsSources.LAST_MODIFIED_ONLINE
    };
    /**
     * The current activity (<code>null</code> if view is not created).
     */
    private Activity mActivity;
    /**
     * The hosts sources list view (<code>null</code> if view is not created).
     */
    private ListView mListView;
    /**
     * The hosts sources list adapter.
     */
    private HostsSourcesCursorAdapter mAdapter;
    /**
     * The position of current list item (<code>-1</code> if no current list item).
     */
    private int mCurrentListItemPosition = -1;
    /**
     * The current action mode when item is selection (<code>null</code> if no action started).
     */
    private ActionMode mActionMode;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Store activity
        this.mActivity = this.getActivity();
        // Create fragment view
        View view = inflater.inflate(R.layout.hosts_sources_fragment, container, false);
        /*
         * Configure hosts sources list.
         */
        // Store list view
        this.mListView = view.findViewById(R.id.hosts_sources_list);
        // Set item click listener to enable/disable hosts source
        this.mListView.setOnItemClickListener((parent, listView, position, id) -> {
            // Checkbox tags are defined by cursor position in HostsCursorAdapter, so we can get
            // checkboxes by position of cursor
            CheckBox checkBox = listView.findViewWithTag("checkbox_" + position);
            if (checkBox == null) {
                Log.w(Constants.TAG, "Checkbox could not be found for hosts source.");
                return;
            }
            // Get current status
            boolean checked = checkBox.isChecked();
            // Set new status
            checkBox.setChecked(!checked);
            ProviderHelper.updateHostsSourceEnabled(HostsSourcesFragment.this.mActivity, id, !checked);
        });
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
                        HostsSourcesFragment.this.editEntry();
                        return true;
                    case R.id.checkbox_list_context_delete:
                        HostsSourcesFragment.this.deleteEntry();
                        return true;
                    default:
                        return false;
                }
            }

            @Override
            public void onDestroyActionMode(ActionMode actionMode) {
                // Get current list item child view
                View childView = HostsSourcesFragment.this.mListView.getChildAt(HostsSourcesFragment.this.mCurrentListItemPosition);
                // Clear background color
                childView.setBackgroundColor(Color.TRANSPARENT);
                // Clear current list item position
                HostsSourcesFragment.this.mCurrentListItemPosition = -1;
                // Clear action mode
                HostsSourcesFragment.this.mActionMode = null;
            }
        };
        // Set item long click listener to start action
        this.mListView.setOnItemLongClickListener((parent, listView2, position, id) -> {
            // Check if there is already a current action
            if (HostsSourcesFragment.this.mActionMode != null) {
                return false;
            }
            // Store current list item position
            HostsSourcesFragment.this.mCurrentListItemPosition = position;
            // Start action mode and store it
            HostsSourcesFragment.this.mActionMode = HostsSourcesFragment.this.mActivity.startActionMode(callback);
            // Get current item background color
            int currentItemBackgroundColor = HostsSourcesFragment.this.getResources().getColor(R.color.selected_background);
            // Apply background color to current item view
            listView2.setBackgroundColor(currentItemBackgroundColor);
            // Return event consumed
            return true;
        });
        /*
         * Add floating action button.
         */
        // Get floating action button
        FloatingActionButton button = view.findViewById(R.id.hosts_sources_add);
        // Set click listener to display menu add entry
        button.setOnClickListener(actionButton -> {
            // Display menu add entry
            HostsSourcesFragment.this.addEntry();
        });
        /*
         * Load data.
         */
        // Create and store an empty adapter used to display the loaded data.
        this.mAdapter = new HostsSourcesCursorAdapter(this.mActivity);
        // Apply the adapter to the view
        this.mListView.setAdapter(this.mAdapter);
        // Prepare the loader. Either re-connect with an existing one, or start a new one.
        this.getLoaderManager().initLoader(0, null, this);
        // Return fragment view
        return view;
    }

    /*
     * CursorLoader related.
     */

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // Create and return cursor loader
        return new CursorLoader(
                this.getActivity(),
                HostsSources.CONTENT_URI,                               // Look for blacklist items
                HostsSourcesFragment.HOSTS_SOURCES_SUMMARY_PROJECTION,  // Columns to display
                null,                                                   // No selection
                null,                                                   // No selection
                HostsSources.DEFAULT_SORT                               // Sort by URL ASC
        );
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
        // Swap the new cursor in.
        // (The framework will take care of closing the old cursor once we return.)
        this.mAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        // This is called when the last Cursor provided to onLoadFinished()
        // above is about to be closed. We need to make sure we are no longer using it.
        this.mAdapter.swapCursor(null);
    }


    /**
     * Add a hosts source entry.
     */
    private void addEntry() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder.setCancelable(true);
        builder.setTitle(getString(R.string.checkbox_list_add_dialog_title));

        // build view from layout
        LayoutInflater factory = LayoutInflater.from(mActivity);
        final View dialogView = factory.inflate(R.layout.lists_url_dialog, null);
        final EditText inputEditText = dialogView.findViewById(R.id.list_dialog_url);
        // set EditText
        inputEditText.setText(getString(R.string.hosts_add_dialog_input));
        inputEditText.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        // move cursor to end of EditText
        Editable inputEditContent = inputEditText.getText();
        inputEditText.setSelection(inputEditContent.length());

        builder.setView(dialogView);

        builder.setPositiveButton(
                getResources().getString(R.string.button_add),
                (dialog, which) -> {
                    dialog.dismiss();

                    String input = inputEditText.getText().toString();
                    insertHostsSource(input);
                }
        );
        builder.setNegativeButton(
                getResources().getString(R.string.button_cancel),
                (dialog, which) -> dialog.dismiss()
        );
        AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * Edit selected hosts source entry.
     */
    private void editEntry() {
        // Check current list item position
        if (this.mCurrentListItemPosition == -1) {
            return;
        }
        // Get current list item identifier
        final long itemId = this.mAdapter.getItemId(this.mCurrentListItemPosition);
        // Get current list item view
        int firstPosition = this.mListView.getFirstVisiblePosition() - this.mListView.getHeaderViewsCount();
        int wantedChild = this.mCurrentListItemPosition - firstPosition;
        if (wantedChild < 0 || wantedChild >= this.mListView.getChildCount()) {
            Log.w(Constants.TAG, "Unable to get view for desired position, because it's not being displayed on screen.");
            return;
        }
        View listItemView = this.mListView.getChildAt(wantedChild);
        // Get URL text view
        TextView urlTextView = listItemView.findViewWithTag("url_" + this.mCurrentListItemPosition);

        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder.setCancelable(true);
        builder.setTitle(getString(R.string.checkbox_list_edit_dialog_title));

        // build view from layout
        LayoutInflater factory = LayoutInflater.from(mActivity);
        final View dialogView = factory.inflate(R.layout.lists_url_dialog, null);
        final EditText inputEditText = dialogView.findViewById(R.id.list_dialog_url);
        // set text from list
        inputEditText.setText(urlTextView.getText());
        inputEditText.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        // move cursor to end of EditText
        Editable inputEditContent = inputEditText.getText();
        inputEditText.setSelection(inputEditContent.length());

        builder.setView(dialogView);

        builder.setPositiveButton(getResources().getString(R.string.button_save),
                (dialog, which) -> {
                    // Close dialog
                    dialog.dismiss();
                    // Finish action mode
                    HostsSourcesFragment.this.mActionMode.finish();

                    String input = inputEditText.getText().toString();

                    if (RegexUtils.isValidUrl(input)) {
                        // update in db
                        ProviderHelper.updateHostsSourceUrl(mActivity, itemId, input);
                    } else {
                        AlertDialog alertDialog = new AlertDialog.Builder(mActivity).create();
                        alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
                        alertDialog.setTitle(R.string.no_url_title);
                        alertDialog.setMessage(getString(R.string.no_url));
                        alertDialog.setButton(
                                AlertDialog.BUTTON_NEUTRAL,
                                getString(R.string.button_close),
                                (dialog1, which1) -> dialog1.dismiss()
                        );
                        alertDialog.show();
                    }
                }
        );
        builder.setNegativeButton(getResources().getString(R.string.button_cancel),
                (dialog, which) -> {
                    // Close dialog
                    dialog.dismiss();
                    // Finish action mode
                    HostsSourcesFragment.this.mActionMode.finish();
                }
        );
        AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * Delete selected hosts source entry.
     */
    private void deleteEntry() {
        // Check current list item position
        if (this.mCurrentListItemPosition == -1) {
            return;
        }
        // Get current list item identifier
        long itemId = this.mAdapter.getItemId(this.mCurrentListItemPosition);
        // Delete related hosts source
        ProviderHelper.deleteHostsSource(this.mActivity, itemId);
        // Finish action mode
        this.mActionMode.finish();
    }

    /**
     * Add new hosts source.
     *
     * @param url The URL of the hosts source to add.
     */
    private void insertHostsSource(String url) {
        // Check parameter
        if (url == null) {
            return;
        }
        // Check if URL is valid
        if (RegexUtils.isValidUrl(url)) {
            // Insert hosts source into database
            ProviderHelper.insertHostsSource(this.mActivity, url);
        } else {
            AlertDialog alertDialog = new AlertDialog.Builder(this.mActivity).create();
            alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
            alertDialog.setTitle(R.string.no_url_title);
            alertDialog.setMessage(getString(R.string.no_url));
            alertDialog.setButton(
                    AlertDialog.BUTTON_NEUTRAL,
                    getString(R.string.button_close),
                    (dialog, which) -> dialog.dismiss()
            );
            alertDialog.show();
        }
    }
}