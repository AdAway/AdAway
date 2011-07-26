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

package org.adaway;

import org.adaway.utils.CheckboxCursorAdapter;
import org.adaway.utils.Constants;
import org.adaway.utils.DatabaseHelper;
import org.adaway.utils.Helper;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class Whitelist extends ListActivity {

    private Context mContext;
    private DatabaseHelper mDatabaseHelper;
    private Cursor mCursor;
    private CheckboxCursorAdapter mAdapter;

    private long mCurrentRowId;

    /**
     * Options Menu
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.checkbox_list, menu);
        return true;
    }

    /**
     * Context Menu on Long Click
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        menu.setHeaderTitle(R.string.checkbox_list_context_title);
        inflater.inflate(R.menu.checkbox_list_context, menu);
    }

    /**
     * Context Menu Items
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
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

        mDatabaseHelper.deleteWhitelistItem(mCurrentRowId);
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

        CheckBox cBox = (CheckBox) v.findViewWithTag(position);

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setCancelable(true);
        // builder.setIcon(android.R.drawable.ic_input_add);
        builder.setTitle(getString(R.string.checkbox_list_edit_dialog_title));

        // Set an EditText view to get user input
        final EditText inputEditText = new EditText(mContext);
        inputEditText.setText(cBox.getText());

        // move cursor to end of EditText
        Editable inputEditContent = inputEditText.getText();
        inputEditText.setSelection(inputEditContent.length());

        builder.setView(inputEditText);

        builder.setPositiveButton(getResources().getString(R.string.button_save),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();

                        String input = inputEditText.getText().toString();

                        if (Helper.isValidHostname(input)) {
                            mDatabaseHelper.updateWhitelistItemURL(mCurrentRowId, input);
                            updateView();
                        } else {
                            AlertDialog alertDialog = new AlertDialog.Builder(mContext).create();
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
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        mCurrentRowId = id;

        // Checkbox tags are defined by cursor position in HostsCursorAdapter, so we can get
        // checkboxes by position of cursor
        CheckBox cBox = (CheckBox) v.findViewWithTag(position);

        if (cBox != null) {
            if (cBox.isChecked()) {
                cBox.setChecked(false);
                // change status based on row id from cursor
                mDatabaseHelper.updateWhitelistItemStatus(mCurrentRowId, 0);
            } else {
                cBox.setChecked(true);
                mDatabaseHelper.updateWhitelistItemStatus(mCurrentRowId, 1);
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

        case R.id.menu_add_qrcode:
            // Use Barcode Scanner
            IntentIntegrator.initiateScan(this, R.string.no_barcode_scanner_title,
                    R.string.no_barcode_scanner, R.string.button_yes, R.string.button_no);
            return true;

        default:
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Add Entry Menu Action
     */
    public void menuAddEntry() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setCancelable(true);
        builder.setIcon(android.R.drawable.ic_input_add);
        builder.setTitle(getString(R.string.checkbox_list_add_dialog_title));

        // Set an EditText view to get user input
        final EditText inputEditText = new EditText(mContext);

        // move cursor to end of EditText
        Editable inputEditContent = inputEditText.getText();
        inputEditText.setSelection(inputEditContent.length());

        builder.setView(inputEditText);

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
            if (Helper.isValidHostname(input)) {
                mDatabaseHelper.insertWhitelistItem(input);
                updateView();
            } else {
                AlertDialog alertDialog = new AlertDialog.Builder(mContext).create();
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

    /**
     * Barcode Scanner Result Parsing
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode,
                intent);

        if (scanResult != null) {
            addEntry(scanResult.getContents());
        }
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = this;

        mDatabaseHelper = new DatabaseHelper(mContext); // open db
        setContentView(R.layout.checkbox_list); // set view
        registerForContextMenu(getListView()); // register long press context menu

        // build content of list
        mCursor = mDatabaseHelper.getWhitelistCursor();
        startManagingCursor(mCursor); // closing of cursor is done this way

        String[] displayFields = new String[] { "url" };
        int[] displayViews = new int[] { R.id.checkbox_list_enabled };
        mAdapter = new CheckboxCursorAdapter(mContext, R.layout.checkbox_list_entry, mCursor,
                displayFields, displayViews);
        setListAdapter(mAdapter);
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
    protected void onDestroy() {
        mDatabaseHelper.close();
        super.onDestroy();
    }

}