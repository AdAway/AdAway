/*
 * Copyright (C) 2011 Dominik Schürmann <dominik@dominikschuermann.de>
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

import org.adaway.R;
import org.adaway.helper.DatabaseHelper;
import org.adaway.util.Constants;
import org.adaway.util.HostsSourcesCursorAdapter;
import org.adaway.util.ValidationUtils;
import org.adaway.util.Log;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.view.MenuInflater;
import android.text.Editable;
import android.text.InputType;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

public class HostsSourcesFragment extends ListFragment {
    private Activity mActivity;
    private DatabaseHelper mDatabaseHelper;
    private Cursor mCursor;
    private HostsSourcesCursorAdapter mAdapter;

    private long mCurrentRowId;

    /**
     * Options Menu
     */
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.checkbox_list, menu);
    }

    /**
     * Context Menu on Long Click
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = (MenuInflater) mActivity.getMenuInflater();
        menu.setHeaderTitle(R.string.checkbox_list_context_title);
        inflater.inflate(R.menu.checkbox_list_context, menu);
    }

    /**
     * Context Menu Items
     */
    @Override
    public boolean onContextItemSelected(android.view.MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();

        switch (item.getItemId()) {
        case R.id.checkbox_list_context_delete:
            menuDeleteEntry(info);
            return true;
        case R.id.checkbox_list_context_edit:
            menuEditEntry(info);
            return true;
        default:
            return super.onContextItemSelected(item);
        }
    }

    /**
     * Delete entry based on selection in context menu
     * 
     * @param info
     */
    private void menuDeleteEntry(AdapterContextMenuInfo info) {
        mCurrentRowId = info.id; // row id from cursor

        mDatabaseHelper.deleteHostsSource(mCurrentRowId);
        updateView();
    }

    /**
     * Edit entry based on selection in context menu
     * 
     * @param info
     */
    private void menuEditEntry(AdapterContextMenuInfo info) {
        mCurrentRowId = info.id; // set global RowId to row id from cursor to use inside save button
        int position = info.position;
        View v = info.targetView;

        TextView urlTextView = (TextView) v.findViewWithTag("url_" + position);

        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder.setCancelable(true);
        builder.setTitle(getString(R.string.checkbox_list_edit_dialog_title));

        // build view from layout
        LayoutInflater factory = LayoutInflater.from(mActivity);
        final View dialogView = factory.inflate(R.layout.lists_url_dialog, null);
        final EditText inputEditText = (EditText) dialogView.findViewById(R.id.list_dialog_url);
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

                        if (ValidationUtils.isValidUrl(input)) {
                            mDatabaseHelper.updateHostsSourceURL(mCurrentRowId, input);
                            updateView();
                        } else {
                            AlertDialog alertDialog = new AlertDialog.Builder(mActivity).create();
                            alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
                            alertDialog.setTitle(R.string.no_url_title);
                            alertDialog.setMessage(getString(org.adaway.R.string.no_url));
                            alertDialog.setButton(getString(R.string.button_close),
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dlg, int sum) {
                                            dlg.dismiss();
                                        }
                                    });
                            alertDialog.show();
                        }
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
     * Handle Checkboxes clicks here, because to enable context menus on longClick we had to disable
     * focusable and clickable on checkboxes in layout xml.
     */
    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        mCurrentRowId = id;

        // Checkbox tags are defined by cursor position in HostsCursorAdapter, so we can get
        // checkboxes by position of cursor
        CheckBox cBox = (CheckBox) v.findViewWithTag("checkbox_" + position);

        if (cBox != null) {
            if (cBox.isChecked()) {
                cBox.setChecked(false);
                // change status based on row id from cursor
                mDatabaseHelper.updateHostsSourceStatus(mCurrentRowId, 0);
            } else {
                cBox.setChecked(true);
                mDatabaseHelper.updateHostsSourceStatus(mCurrentRowId, 1);
            }
        } else {
            Log.e(Constants.TAG, "Checkbox could not be found!");
        }
    }

    /**
     * Menu Options
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
        case R.id.menu_add:
            menuAddEntry();
            return true;

        default:
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Add Entry Menu Action
     */
    public void menuAddEntry() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder.setCancelable(true);
        builder.setTitle(getString(R.string.checkbox_list_add_dialog_title));

        // build view from layout
        LayoutInflater factory = LayoutInflater.from(mActivity);
        final View dialogView = factory.inflate(R.layout.lists_url_dialog, null);
        final EditText inputEditText = (EditText) dialogView.findViewById(R.id.list_dialog_url);
        // set EditText
        inputEditText.setText(getString(R.string.hosts_add_dialog_input));
        inputEditText.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
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
                        addEntry(input);
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
     * Add new entry based on input
     * 
     * @param input
     */
    private void addEntry(String input) {
        if (input != null) {
            if (ValidationUtils.isValidUrl(input)) {
                mDatabaseHelper.insertHostsSource(input);
                updateView();
            } else {
                AlertDialog alertDialog = new AlertDialog.Builder(mActivity).create();
                alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
                alertDialog.setTitle(R.string.no_url_title);
                alertDialog.setMessage(getString(org.adaway.R.string.no_url));
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

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mActivity = this.getActivity();

        mDatabaseHelper = new DatabaseHelper(mActivity); // open db
        registerForContextMenu(getListView()); // register long press context menu

        // build content of list
        mCursor = mDatabaseHelper.getHostsSourcesCursor();
        mActivity.startManagingCursor(mCursor); // closing of cursor is done this way

        // dislayFields and displayViews are handled in custom adapter!
        String[] displayFields = new String[] {};
        int[] displayViews = new int[] {};
        mAdapter = new HostsSourcesCursorAdapter(mActivity, R.layout.checkbox_list_two_entry,
                mCursor, displayFields, displayViews);
        setListAdapter(mAdapter);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true); // enable options menu for this fragment
    }

    /**
     * Inflate the layout for this fragment
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.checkbox_list_two, container, false);
    }

    /**
     * Refresh List by requerying the Cursor and updating the adapter of the view
     */
    private void updateView() {
        mCursor.requery(); // TODO: requery is deprecated
        mAdapter.notifyDataSetChanged();
    }

    /**
     * Close DB onDestroy
     */
    @Override
    public void onDestroyView() {
        mDatabaseHelper.close();
        super.onDestroy();
    }

}