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

package org.adaway.ui.lists.type;

import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.LiveData;
import androidx.paging.PagingData;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.adaway.R;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.ListType;
import org.adaway.ui.dialog.AlertDialogValidator;
import org.adaway.util.RegexUtils;

/**
 * This class is a {@link AbstractListFragment} to display and manage redirected hosts.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class RedirectedHostsFragment extends AbstractListFragment {
    @Override
    protected boolean isTwoRowsItem() {
        return true;
    }

    @Override
    protected LiveData<PagingData<HostListItem>> getData() {
        return this.mViewModel.getRedirectedListItems();
    }

    @Override
    public void addItem() {
        // Create dialog view
        LayoutInflater factory = LayoutInflater.from(this.mActivity);
        View view = factory.inflate(R.layout.lists_redirected_dialog, null);
        EditText hostnameEditText = view.findViewById(R.id.list_dialog_hostname);
        EditText ipEditText = view.findViewById(R.id.list_dialog_ip);
        // Create dialog
        AlertDialog alertDialog = new MaterialAlertDialogBuilder(this.mActivity)
                .setCancelable(true)
                .setTitle(R.string.list_add_dialog_redirect)
                .setView(view)
                // Setup buttons
                .setPositiveButton(
                        R.string.button_add,
                        (dialog, which) -> {
                            // Close dialog
                            dialog.dismiss();
                            // Check if hostname and IP are valid
                            String hostname = hostnameEditText.getText().toString();
                            String ip = ipEditText.getText().toString();
                            if (RegexUtils.isValidHostname(hostname) && RegexUtils.isValidIP(ip)) {
                                // Insert host to redirection list
                                this.mViewModel.addListItem(ListType.REDIRECTED, hostname, ip);
                            }
                        }
                )
                .setNegativeButton(
                        R.string.button_cancel,
                        (dialog, which) -> dialog.dismiss()
                )
                .create();
        // Show dialog
        alertDialog.show();
        // Set button validation behavior
        AlertDialogValidator validator = new AlertDialogValidator(
                alertDialog,
                input -> {
                    String hostname = hostnameEditText.getText().toString();
                    String ip = ipEditText.getText().toString();
                    return RegexUtils.isValidHostname(hostname) && RegexUtils.isValidIP(ip);
                },
                false
        );
        hostnameEditText.addTextChangedListener(validator);
        ipEditText.addTextChangedListener(validator);
    }

    @Override
    protected void editItem(HostListItem item) {
        // Create dialog view
        LayoutInflater factory = LayoutInflater.from(this.mActivity);
        View view = factory.inflate(R.layout.lists_redirected_dialog, null);
        // Set hostname and IP
        EditText hostnameEditText = view.findViewById(R.id.list_dialog_hostname);
        EditText ipEditText = view.findViewById(R.id.list_dialog_ip);
        hostnameEditText.setText(item.getHost());
        ipEditText.setText(item.getRedirection());
        // Move cursor to end of EditText
        Editable hostnameEditContent = hostnameEditText.getText();
        hostnameEditText.setSelection(hostnameEditContent.length());
        // Create dialog
        AlertDialog alertDialog = new MaterialAlertDialogBuilder(this.mActivity)
                .setCancelable(true)
                .setTitle(getString(R.string.list_edit_dialog_redirect))
                .setView(view)
                // Set buttons
                .setPositiveButton(R.string.button_save,
                        (dialog, which) -> {
                            // Close dialog
                            dialog.dismiss();
                            // Check hostname and IP validity
                            String hostname = hostnameEditText.getText().toString();
                            String ip = ipEditText.getText().toString();
                            if (RegexUtils.isValidHostname(hostname) && RegexUtils.isValidIP(ip)) {
                                // Update list item
                                this.mViewModel.updateListItem(item, hostname, ip);
                            }
                        }
                )
                .setNegativeButton(
                        R.string.button_cancel,
                        (dialog, which) -> dialog.dismiss()
                )
                .create();
        // Show dialog
        alertDialog.show();
        // Set button validation behavior
        AlertDialogValidator validator = new AlertDialogValidator(
                alertDialog,
                input -> {
                    String hostname = hostnameEditText.getText().toString();
                    String ip = ipEditText.getText().toString();
                    return RegexUtils.isValidHostname(hostname) && RegexUtils.isValidIP(ip);
                },
                true
        );
        hostnameEditText.addTextChangedListener(validator);
        ipEditText.addTextChangedListener(validator);
    }
}