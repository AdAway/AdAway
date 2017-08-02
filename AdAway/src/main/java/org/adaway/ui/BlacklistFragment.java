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

package org.adaway.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.text.Editable;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.adaway.R;
import org.adaway.provider.AdAwayContract.Blacklist;
import org.adaway.provider.ProviderHelper;
import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.adaway.util.RegexUtils;

/**
 * This class is a {@link ListFragment} to display and manage black-listed hosts.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class BlacklistFragment extends ListFragment implements
        LoaderManager.LoaderCallbacks<Cursor>, ListsFragmentPagerAdapter.AddItemActionListener {
    /**
     * The blacklist fields display in this view.
     */
    private static final String[] BLACKLIST_SUMMARY_PROJECTION = new String[]{
            Blacklist._ID,
            Blacklist.HOSTNAME,
            Blacklist.ENABLED
    };
    /**
     * The blacklist cursor adapter.
     */
    private CursorAdapter mAdapter;
    /**
     * The position of current list item (<code>-1</code> if no current list item).
     */
    private int mCurrentListItemPosition = -1;
    /**
     * The current action mode when item is selection (<code>null</code> if no action started).
     */
    private ActionMode mActionMode;


    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
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
                        BlacklistFragment.this.editEntry();
                        return true;
                    case R.id.checkbox_list_context_delete:
                        BlacklistFragment.this.deleteEntry();
                        return true;
                    default:
                        return false;
                }
            }

            @Override
            public void onDestroyActionMode(ActionMode actionMode) {
                // Get current list item child view
                View childView = listView.getChildAt(BlacklistFragment.this.mCurrentListItemPosition);
                // Clear background color
                childView.setBackgroundColor(Color.TRANSPARENT);
                // Clear current list item position
                BlacklistFragment.this.mCurrentListItemPosition = -1;
                // Clear action mode
                BlacklistFragment.this.mActionMode = null;
            }
        };
        // Set item long click listener to start action
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                // Check if there is already a current action
                if (BlacklistFragment.this.mActionMode != null) {
                    return false;
                }
                // Store current list item position
                BlacklistFragment.this.mCurrentListItemPosition = position;
                // Start action mode and store it
                BlacklistFragment.this.mActionMode = BlacklistFragment.this.getActivity().startActionMode(callback);
                // Get current item background color
                int currentItemBackgroundColor = BlacklistFragment.this.getResources().getColor(R.color.selected_background);
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
        this.mAdapter = new ListsCursorAdapter(this.getActivity());
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
        ProviderHelper.updateBlacklistItemEnabled(this.getActivity(), id, !checked);
    }

    /*
     * LoaderCallback.
     */

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // Create and return cursor loader
        return new CursorLoader(
                this.getActivity(),
                Blacklist.CONTENT_URI,                          // Look for blacklist items
                BlacklistFragment.BLACKLIST_SUMMARY_PROJECTION, // Columns to display
                null,                                           // No selection
                null,                                           // No selection
                Blacklist.DEFAULT_SORT                          // Sort by hostname ASC
        );
    }

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

    /*
     * AddItemActionListener.
     */

    @Override
    public void addItem() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this.getActivity());
        builder.setCancelable(true);
        builder.setTitle(getString(R.string.checkbox_list_add_dialog_title));

        // build view from layout
        LayoutInflater factory = LayoutInflater.from(this.getActivity());
        final View dialogView = factory.inflate(R.layout.lists_hostname_dialog, null);
        final EditText inputEditText = dialogView.findViewById(R.id.list_dialog_hostname);

        // move cursor to end of EditText
        Editable inputEditContent = inputEditText.getText();
        inputEditText.setSelection(inputEditContent.length());

        builder.setView(dialogView);

        builder.setPositiveButton(getResources().getString(R.string.button_add),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();

                        String input = inputEditText.getText().toString();
                        insertItem(input);
                    }
                });
        builder.setNegativeButton(getResources().getString(R.string.button_cancel),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * Edit selected list entry.
     */
    private void editEntry() {
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
        View listItemView = listView.getChildAt(wantedChild);
        // Get URL text view
        TextView hostnameTextView = listItemView.findViewWithTag(ListsCursorAdapter.HOSTNAME_TEXTVIEW_TAG);


        final Activity activity = this.getActivity();

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setCancelable(true);
        builder.setTitle(getString(R.string.checkbox_list_edit_dialog_title));

        // build view from layout
        LayoutInflater factory = LayoutInflater.from(activity);
        final View dialogView = factory.inflate(R.layout.lists_hostname_dialog, null);
        final EditText inputEditText = dialogView.findViewById(R.id.list_dialog_hostname);
        inputEditText.setText(hostnameTextView.getText());

        // move cursor to end of EditText
        Editable inputEditContent = inputEditText.getText();
        inputEditText.setSelection(inputEditContent.length());

        builder.setView(dialogView);

        builder.setPositiveButton(getResources().getString(R.string.button_save),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Close dialog
                        dialog.dismiss();
                        // Finish action mode
                        BlacklistFragment.this.mActionMode.finish();

                        String input = inputEditText.getText().toString();

                        if (RegexUtils.isValidHostname(input)) {
                            ProviderHelper.updateBlacklistItemHostname(
                                    activity,
                                    itemId,
                                    input
                            );
                        } else {
                            AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
                            alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
                            alertDialog.setTitle(R.string.no_hostname_title);
                            alertDialog.setMessage(getString(org.adaway.R.string.no_hostname));
                            alertDialog.setButton(getString(R.string.button_close),
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    });
                            alertDialog.show();
                        }
                    }
                });
        builder.setNegativeButton(getResources().getString(R.string.button_cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Close dialog
                dialog.dismiss();
                // Finish action mode
                BlacklistFragment.this.mActionMode.finish();
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * Delete selected list entry.
     */
    private void deleteEntry() {
        // Check current list item position
        if (this.mCurrentListItemPosition == -1) {
            return;
        }
        // Get current list item identifier
        long itemId = this.mAdapter.getItemId(this.mCurrentListItemPosition);
        // Delete related hosts source
        ProviderHelper.deleteBlacklistItem(this.getActivity(), itemId);
        // Finish action mode
        BlacklistFragment.this.mActionMode.finish();
    }

    /**
     * Insert an entry.
     *
     * @param host The host to insert.
     */
    private void insertItem(String host) {
        // Check parameter
        if (host == null) {
            return;
        }
        // Get activity
        Activity activity = this.getActivity();
        // Check if host is valid
        if (RegexUtils.isValidHostname(host)) {
            // Insert host to black list
            ProviderHelper.insertBlacklistItem(activity, host);
        } else {
            AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
            alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
            alertDialog.setTitle(R.string.no_hostname_title);
            alertDialog.setMessage(getString(org.adaway.R.string.no_hostname));
            alertDialog.setButton(getString(R.string.button_close),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dlg, int sum) {
                            dlg.dismiss();
                        }
                    });
            alertDialog.show();
        }
    }
}