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

import android.app.AlertDialog;

import androidx.lifecycle.LiveData;

import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import org.adaway.R;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.ListType;
import org.adaway.ui.dialog.AlertDialogValidator;
import org.adaway.util.RegexUtils;

import java.util.List;

/**
 * This class is a {@link AbstractListFragment} to display and manage black-listed hosts.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class BlackListFragment extends AbstractListFragment {
    @Override
    protected LiveData<List<HostListItem>> getData() {
        return this.mViewModel.getBlackListItems();
    }

    @Override
    protected void addItem() {
        // Create dialog builder
        AlertDialog.Builder builder = new AlertDialog.Builder(this.mActivity);
        builder.setCancelable(true);
        builder.setTitle(R.string.list_add_dialog_black);
        // Create dialog view
        LayoutInflater factory = LayoutInflater.from(this.mActivity);
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
                        this.mViewModel.addListItem(ListType.BLACK_LIST, hostname, null);
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
    protected void editItem(HostListItem item) {
        // Create dialog builder
        AlertDialog.Builder builder = new AlertDialog.Builder(this.mActivity);
        builder.setCancelable(true);
        builder.setTitle(R.string.list_edit_dialog_black);
        // Create dialog view
        LayoutInflater factory = LayoutInflater.from(this.mActivity);
        View view = factory.inflate(R.layout.lists_black_dialog, null);
        builder.setView(view);
        // Set hostname
        EditText inputEditText = view.findViewById(R.id.list_dialog_hostname);
        inputEditText.setText(item.getHost());
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
                        // Update list item
                        this.mViewModel.updateListItem(item, hostname, null);
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
}