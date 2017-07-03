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
import android.net.Uri;
import android.os.Bundle;
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
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.adaway.R;
import org.adaway.provider.AdAwayContract.HostsSources;
import org.adaway.provider.ProviderHelper;
import org.adaway.util.Constants;
import org.adaway.util.HostsSourcesCursorAdapter;
import org.adaway.util.Log;
import org.adaway.util.RegexUtils;

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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Store activity
        this.mActivity = this.getActivity();
        // Create fragment view
        View view = inflater.inflate(R.layout.hosts_sources_fragment, container, false);
        /*
         * Hosts sources list.
         */
        // Store list view
        this.mListView = view.findViewById(R.id.hosts_sources_list);
        // Set item click listener to enable/disable hosts source
        this.mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // Checkbox tags are defined by cursor position in HostsCursorAdapter, so we can get
                // checkboxes by position of cursor
                CheckBox cBox = view.findViewWithTag("checkbox_" + position);
                if (cBox == null) {
                    Log.w(Constants.TAG, "Checkbox could not be found for hosts source.");
                    return;
                }
                // Get current status
                boolean checked = cBox.isChecked();
                // Set new status
                cBox.setChecked(!checked);
                ProviderHelper.updateHostsSourceEnabled(HostsSourcesFragment.this.mActivity, id, !checked);
            }
        });
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
                        editEntry();
                        return true;
                    case R.id.checkbox_list_context_delete:
                        menuDeleteEntry();
                        return true;
                    default:
                        return false;
                }
            }

            @Override
            public void onDestroyActionMode(ActionMode actionMode) {
                // Clear current list item position
                HostsSourcesFragment.this.mCurrentListItemPosition = -1;
                // Clear action mode
                HostsSourcesFragment.this.mActionMode = null;
            }
        };
        // Set item long click listener to start action
        this.mListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                // Check if there is already a current action
                if (HostsSourcesFragment.this.mActionMode != null) {
                    return false;
                }
                // Store current list item position
                HostsSourcesFragment.this.mCurrentListItemPosition = position;
                // Start action mode and store it
                HostsSourcesFragment.this.mActionMode = HostsSourcesFragment.this.mActionMode = getActivity().startActionMode(callback);
                // Select the view
                view.setSelected(true);
                // Return event consumed
                return true;
            }
        });
        /*
         * Add floating action button.
         */
        // Get floating action button
        FloatingActionButton button = view.findViewById(R.id.hosts_sources_add);
        // Set click listener to display menu add entry
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Display menu add entry
                HostsSourcesFragment.this.addEntry();
            }
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

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // This is called when a new Loader needs to be created. This
        // sample only has one Loader, so we don't care about the ID.
        Uri baseUri = HostsSources.CONTENT_URI;

        // Now create and return a CursorLoader that will take care of
        // creating a Cursor for the data being displayed.
        return new CursorLoader(getActivity(), baseUri, HOSTS_SOURCES_SUMMARY_PROJECTION, null,
                null, HostsSources.DEFAULT_SORT);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // Swap the new cursor in. (The framework will take care of closing the
        // old cursor once we return.)
        this.mAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // This is called when the last Cursor provided to onLoadFinished()
        // above is about to be closed. We need to make sure we are no
        // longer using it.
        this.mAdapter.swapCursor(null);
    }


    /**
     * Add Entry Menu Action
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
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();

                        String input = inputEditText.getText().toString();
                        insertHostsSource(input);
                    }
                }
        );
        builder.setNegativeButton(
                getResources().getString(R.string.button_cancel),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }
        );
        AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * Edit entry based on selection in context menu
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
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();

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
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    }
                            );
                            alertDialog.show();
                        }
                    }
                }
        );
        builder.setNegativeButton(getResources().getString(R.string.button_cancel),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }
        );
        AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * Delete entry based on selection in context menu.
     */
    private void menuDeleteEntry() {
        // Check current list item position
        if (this.mCurrentListItemPosition == -1) {
            return;
        }
        // Get current list item identifier
        long itemId = this.mAdapter.getItemId(this.mCurrentListItemPosition);
        // Delete related hosts source
        ProviderHelper.deleteHostsSource(this.mActivity, itemId);
    }

    /**
     * Add new entry based on url
     *
     * @param url The URL of the hosts source.
     */
    private void insertHostsSource(String url) {
        // Check parameter
        if (url == null) {
            return;
        }
        // Check if URL is valid
        if (RegexUtils.isValidUrl(url)) {
            // insert hosts source into database
            ProviderHelper.insertHostsSource(this.mActivity, url);
        } else {
            AlertDialog alertDialog = new AlertDialog.Builder(this.mActivity).create();
            alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
            alertDialog.setTitle(R.string.no_url_title);
            alertDialog.setMessage(getString(R.string.no_url));
            alertDialog.setButton(
                    AlertDialog.BUTTON_NEUTRAL,
                    getString(R.string.button_close),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    }
            );
            alertDialog.show();
        }
    }
}