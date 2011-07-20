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
import android.text.InputType;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.webkit.URLUtil;
import android.widget.EditText;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class HostsSources extends ListActivity {

    private HostsDatabase mHostsDatabase;

    private Context mContext;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.hosts_sources, menu);
        return true;
    }

    /**
     * Menu Options
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
        case R.id.menu_add:
            addEntry();
            return true;

        case R.id.menu_add_qrcode:
            // Use Barcode Scanner
            IntentIntegrator.initiateScan(this);

            return true;

        default:
            return super.onOptionsItemSelected(item);
        }
    }

    public void addEntry() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setIcon(android.R.drawable.ic_input_add);
        builder.setTitle(getString(R.string.hosts_add_dialog_title));

        // Set an EditText view to get user input
        final EditText inputEditText = new EditText(this);
        inputEditText.setText(getString(R.string.hosts_add_dialog_input));
        inputEditText.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        builder.setView(inputEditText);

        builder.setPositiveButton(getResources().getString(R.string.button_add),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();

                        String input = inputEditText.getText().toString();

                        if (input != null) {
                            if (URLUtil.isValidUrl(input)) {
                                mHostsDatabase.insertHostsFile(input);
                                // TODO: refresh view
                            } else {
                                AlertDialog alertDialog = new AlertDialog.Builder(mContext)
                                        .create();
                                alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
                                alertDialog.setTitle(R.string.no_url_title);
                                alertDialog.setMessage(getString(org.adaway.R.string.no_url));
                                alertDialog.setButton(getString(R.string.button_close),
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dlg, int sum) {
                                                // do nothing, close
                                            }
                                        });
                                alertDialog.show();
                            }
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
     * Barcode Scanner Result Parsing
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode,
                intent);

        if (scanResult != null) {
            if (URLUtil.isValidUrl(scanResult.getContents())) {
                mHostsDatabase.insertHostsFile(scanResult.getContents());
                // TODO: refresh view
            } else {
                AlertDialog alertDialog = new AlertDialog.Builder(mContext).create();
                alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
                alertDialog.setTitle(R.string.no_url_title);
                alertDialog.setMessage(getString(org.adaway.R.string.no_url));
                alertDialog.setButton(getString(R.string.button_close),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dlg, int sum) {
                                // do nothing, close
                            }
                        });
                alertDialog.show();
            }
        }
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = this;

        mHostsDatabase = new HostsDatabase(mContext);

        setContentView(R.layout.hosts_list);

        HostsDatabase db = new HostsDatabase(mContext);
        Cursor cur = db.getHostsCursor();

        String[] displayFields = new String[] { "url" };
        int[] displayViews = new int[] { R.id.hosts_entry_enabled };
        setListAdapter(new HostsCursorAdapter(this, R.layout.hosts_list_entry, cur, displayFields,
                displayViews));
    }

}