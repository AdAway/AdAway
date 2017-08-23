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
import org.adaway.provider.AdAwayContract.RedirectionList;
import org.adaway.provider.ProviderHelper;
import org.adaway.util.RegexUtils;

/**
 * This class is a {@link ListFragment} to display and manage redirections.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class RedirectionListFragment extends AbstractListFragment {
    /**
     * The redirection fields display in this view.
     */
    protected static final String[] REDIRECTION_LIST_SUMMARY_PROJECTION = new String[]{
            RedirectionList._ID,
            RedirectionList.HOSTNAME,
            RedirectionList.IP,
            RedirectionList.ENABLED
    };

    /*
     * LoaderCallback.
     */

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // Create and return cursor loader
        return new CursorLoader(
                this.getActivity(),
                RedirectionList.CONTENT_URI,                                 // Look for blacklist items
                RedirectionListFragment.REDIRECTION_LIST_SUMMARY_PROJECTION, // Columns to display
                null,                                                        // No selection
                null,                                                        // No selection
                RedirectionList.DEFAULT_SORT                                 // Sort by hostname ASC
        );
    }

    @Override
    protected void addItem() {
        FragmentActivity activity = this.getActivity();

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setCancelable(true);
        builder.setTitle(getString(R.string.checkbox_list_add_dialog_title));

        // build view from layout
        LayoutInflater factory = LayoutInflater.from(activity);
        final View dialogView = factory.inflate(R.layout.lists_redirection_dialog, null);
        final EditText hostnameEditText = dialogView
                .findViewById(R.id.list_dialog_hostname);
        final EditText ipEditText = dialogView.findViewById(R.id.list_dialog_ip);

        // move cursor to end of EditText
        Editable hostnameEditContent = hostnameEditText.getText();
        hostnameEditText.setSelection(hostnameEditContent.length());

        // move cursor to end of EditText
        Editable ipEditContent = ipEditText.getText();
        ipEditText.setSelection(ipEditContent.length());

        builder.setView(dialogView);

        builder.setPositiveButton(getResources().getString(R.string.button_add),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();

                        String hostname = hostnameEditText.getText().toString();
                        String ip = ipEditText.getText().toString();

                        RedirectionListFragment.this.addItem(hostname, ip);
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
     * Add a new item.
     *
     * @param hostname The redirection hostname.
     * @param ip       The redirection IP address.
     */
    protected void addItem(String hostname, String ip) {
        // Check parameters
        if (hostname == null || ip == null) {
            return;
        }
        // Get activity
        FragmentActivity activity = this.getActivity();
        // Check if host is valid
        if (RegexUtils.isValidHostname(hostname)) {
            // Check if IP is valid
            if (RegexUtils.isValidIP(ip)) {
                ProviderHelper.insertRedirectionListItem(activity, hostname, ip);
            } else {
                // Notify IP is not valid
                AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
                alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
                alertDialog.setTitle(R.string.no_ip_title);
                alertDialog.setMessage(getString(org.adaway.R.string.no_ip));
                alertDialog.setButton(getString(R.string.button_close),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dlg, int sum) {
                                dlg.dismiss();
                            }
                        }
                );
                alertDialog.show();
            }
        } else {
            // Notify host is not valid
            AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
            alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
            alertDialog.setTitle(R.string.no_hostname_title);
            alertDialog.setMessage(getString(org.adaway.R.string.no_hostname));
            alertDialog.setButton(getString(R.string.button_close),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dlg, int sum) {
                            dlg.dismiss();
                        }
                    }
            );
            alertDialog.show();
        }
    }

    @Override
    protected void enableItem(long itemId, boolean enabled) {
        ProviderHelper.updateRedirectionListItemEnabled(this.getActivity(), itemId, enabled);
    }

    @Override
    protected void editItem(final long itemId, View itemView) {
        final FragmentActivity activity = this.getActivity();

        TextView hostnameTextView = itemView.findViewWithTag(ListsCursorAdapter.HOSTNAME_TEXTVIEW_TAG);
        TextView ipTextView = itemView.findViewWithTag(ListsCursorAdapter.IP_TEXTVIEW_TAG);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setCancelable(true);
        builder.setTitle(getString(R.string.checkbox_list_edit_dialog_title));

        // build view from layout
        LayoutInflater factory = LayoutInflater.from(activity);
        final View dialogView = factory.inflate(R.layout.lists_redirection_dialog, null);
        final EditText hostnameEditText = dialogView.findViewById(R.id.list_dialog_hostname);
        final EditText ipEditText = dialogView.findViewById(R.id.list_dialog_ip);

        // set text from list
        hostnameEditText.setText(hostnameTextView.getText());
        ipEditText.setText(ipTextView.getText());

        // move cursor to end of EditText
        Editable hostnameEditContent = hostnameEditText.getText();
        hostnameEditText.setSelection(hostnameEditContent.length());
        Editable ipEditContent = ipEditText.getText();
        ipEditText.setSelection(ipEditContent.length());

        builder.setView(dialogView);

        builder.setPositiveButton(getResources().getString(R.string.button_save),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();

                        String hostname = hostnameEditText.getText().toString();
                        String ip = ipEditText.getText().toString();

                        if (RegexUtils.isValidHostname(hostname)) {
                            if (RegexUtils.isValidIP(ip)) {
                                ProviderHelper.updateRedirectionListItemHostnameAndIp(activity,
                                        itemId, hostname, ip);
                            } else {
                                AlertDialog alertDialog = new AlertDialog.Builder(activity)
                                        .create();
                                alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
                                alertDialog.setTitle(R.string.no_ip_title);
                                alertDialog.setMessage(getString(org.adaway.R.string.no_ip));
                                alertDialog.setButton(getString(R.string.button_close),
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dlg, int sum) {
                                                dlg.dismiss();
                                            }
                                        }
                                );
                                alertDialog.show();
                            }
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

    @Override
    protected void deleteItem(long itemId) {
        // Delete related redirection
        ProviderHelper.deleteRedirectionListItem(this.getActivity(), itemId);
    }

    @Override
    protected CursorAdapter getCursorAdapter() {
        return new ListsCursorAdapter(this.getActivity(), R.layout.checkbox_list_two_entries);
    }
}