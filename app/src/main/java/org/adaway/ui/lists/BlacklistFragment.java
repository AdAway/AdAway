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
import org.adaway.ui.dialog.AlertDialogValidator;
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
    protected static final String[] BLACKLIST_SUMMARY_PROJECTION = new String[]{
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

    @Override
    protected void addItem() {
        FragmentActivity activity = this.getActivity();
        // Create dialog builder
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setCancelable(true);
        builder.setTitle(R.string.list_add_dialog_black);
        // Create dialog view
        LayoutInflater factory = LayoutInflater.from(activity);
        View view = factory.inflate(R.layout.lists_black_dialog, null);
        EditText inputEditText = view.findViewById(R.id.list_dialog_hostname);
        builder.setView(view);
        // Setup buttons
        builder.setPositiveButton(
                R.string.button_add,
                (dialog, which) -> {
                    // Close dialog
                    dialog.dismiss();
                    // Check if hostname is valid
                    String hostname = inputEditText.getText().toString();
                    if (RegexUtils.isValidHostname(hostname)) {
                        // Insert host to black list
                        ProviderHelper.insertBlacklistItem(activity, hostname);
                    }
                });
        builder.setNegativeButton(
                R.string.button_cancel,
                (dialog, which) -> dialog.dismiss()
        );
        // Show dialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
        // Set button validation behavior
        inputEditText.addTextChangedListener(
                new AlertDialogValidator(alertDialog, RegexUtils::isValidHostname, false)
        );
    }

    @Override
    protected void enableItem(long itemId, boolean enabled) {
        ProviderHelper.updateBlacklistItemEnabled(this.getActivity(), itemId, enabled);
    }

    @Override
    protected void editItem(final long itemId, View itemView) {
        // Get hostname text
        TextView hostnameTextView = itemView.findViewWithTag(ListsCursorAdapter.HOSTNAME_TEXTVIEW_TAG);
        CharSequence hostnameText = hostnameTextView.getText();
        // Create dialog builder
        Activity activity = this.getActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setCancelable(true);
        builder.setTitle(R.string.list_edit_dialog_black);
        // Create dialog view
        LayoutInflater factory = LayoutInflater.from(activity);
        View view = factory.inflate(R.layout.lists_black_dialog, null);
        builder.setView(view);
        // Set hostname
        EditText inputEditText = view.findViewById(R.id.list_dialog_hostname);
        inputEditText.setText(hostnameText);
        // Move cursor to end of EditText
        Editable inputEditContent = inputEditText.getText();
        inputEditText.setSelection(inputEditContent.length());
        // Setup buttons
        builder.setPositiveButton(
                R.string.button_save,
                (dialog, which) -> {
                    // Close dialog
                    dialog.dismiss();
                    // Check hostname validity
                    String hostname = inputEditText.getText().toString();
                    if (RegexUtils.isValidHostname(hostname)) {
                        ProviderHelper.updateBlacklistItemHostname(
                                activity,
                                itemId,
                                hostname
                        );
                    }
                });
        builder.setNegativeButton(
                R.string.button_cancel
                , (dialog, which) -> dialog.dismiss()
        );
        // Show dialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
        // Set button validation behavior
        inputEditText.addTextChangedListener(
                new AlertDialogValidator(alertDialog, RegexUtils::isValidHostname, true)
        );
    }

    @Override
    protected void deleteItem(long itemId) {
        ProviderHelper.deleteBlacklistItem(this.getActivity(), itemId);
    }

    @Override
    protected CursorAdapter getCursorAdapter() {
        return new ListsCursorAdapter(this.getActivity(), R.layout.checkbox_list_entry);
    }
}