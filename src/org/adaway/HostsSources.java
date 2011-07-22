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

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.webkit.URLUtil;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.EditText;
import android.widget.ListView;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class HostsSources extends ListActivity {

    private DatabaseHelper mHostsDatabase;
    private Cursor mCursor;
    private Context mContext;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        menu.setHeaderTitle(R.string.hosts_sources_context_title);
        inflater.inflate(R.menu.hosts_sources_context, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        switch (item.getItemId()) {
        case R.id.hosts_sources_context_delete:
            deleteEntry(info.id);
            return true;
        default:
            return super.onContextItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.hosts_sources, menu);
        return true;
    }

    private void deleteEntry(long id) {
        mHostsDatabase.deleteHostsSource(id);
        buildList();
    }

    /**
     * Handle Checkboxes clicks here, because to enable context menus on longClick we had to disable
     * focusable and clickable on checkboxes in layout xml.
     */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        // TODO Auto-generated method stub
        // super.onListItemClick(l, v, position, id);
        // String selection = l.getItemAtPosition(position).toString();
        // TODO: implement database update here?
        Log.d("adaway", "clicked on id " + id);
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
            // TODO: use translated strings for initiateScan
            IntentIntegrator.initiateScan(this);

            return true;

        default:
            return super.onOptionsItemSelected(item);
        }
    }

    public void menuAddEntry() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setIcon(android.R.drawable.ic_input_add);
        builder.setTitle(getString(R.string.hosts_add_dialog_title));

        // Set an EditText view to get user input
        final EditText inputEditText = new EditText(this);
        inputEditText.setText(getString(R.string.hosts_add_dialog_input));
        inputEditText.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
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
            if (URLUtil.isValidUrl(input)) {
                mHostsDatabase.insertHostsSource(input);
                buildList();
            } else {
                AlertDialog alertDialog = new AlertDialog.Builder(mContext).create();
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

        mContext = this; // open db
        mHostsDatabase = new DatabaseHelper(mContext); // set view
        setContentView(R.layout.hosts_list); // register long press context menu
        registerForContextMenu(getListView()); // build content of list

        buildList();
    }

    private void buildList() {
        mCursor = mHostsDatabase.getHostsSourcesCursor();
        startManagingCursor(mCursor);

        String[] displayFields = new String[] { "url" };
        int[] displayViews = new int[] { R.id.hosts_entry_enabled };
        HostsCursorAdapter adapter = new HostsCursorAdapter(mContext, R.layout.hosts_list_entry,
                mCursor, displayFields, displayViews);
        setListAdapter(adapter);
    }

    // TODO: on destroy close cursor and databse like in zirco browser?

    @Override
    protected void onDestroy() {
        // mCursor.close();
        mHostsDatabase.close();
        super.onDestroy();
    }

}