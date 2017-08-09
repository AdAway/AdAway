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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.ListFragment;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.adaway.R;
import org.adaway.provider.AdAwayContract.Blacklist;
import org.adaway.provider.ProviderHelper;
import org.adaway.util.RegexUtils;

/**
 * This class is a {@link ListFragment} to display and manage black-listed hosts.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class BlacklistFragment extends AbstractListFragment {
    /**
     * The blacklist fields display in this view.
     */
    private static final String[] BLACKLIST_SUMMARY_PROJECTION = new String[]{
            Blacklist._ID,
            Blacklist.HOSTNAME,
            Blacklist.ENABLED
    };

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
     * Insert an entry.
     *
     * @param host The host to insert.
     */
    protected void insertItem(String host) {
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

    @Override
    protected void editItem(final long itemId, View itemView) {
        final Activity activity = this.getActivity();
        // Get URL text view
        TextView hostnameTextView = itemView.findViewWithTag(ListsCursorAdapter.HOSTNAME_TEXTVIEW_TAG);

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
                        dialog.dismiss();

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
                dialog.dismiss();
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    @Override
    protected void deleteItem(long itemId) {
        // Delete related hosts source
        ProviderHelper.deleteBlacklistItem(this.getActivity(), itemId);
    }

    @Override
    protected CursorAdapter getCursorAdapter(FragmentActivity activity) {
        return new ListsCursorAdapter(activity);
    }
}