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
import org.adaway.provider.AdAwayContract.RedirectionList;
import org.adaway.provider.ProviderHelper;
import org.adaway.util.Constants;
import org.adaway.util.RedirectionCursorAdapter;
import org.adaway.util.ValidationUtils;
import org.adaway.util.Log;

import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

public class RedirectionListFragment extends ListFragment implements
        LoaderManager.LoaderCallbacks<Cursor> {
    private Activity mActivity;
    private RedirectionCursorAdapter mAdapter;

    private long mCurrentRowId;

    /**
     * Options Menu
     */
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        // if not cleared before we have double menu entries on rotate of device
        // menu.clear();
        inflater.inflate(R.menu.lists_fragment, menu);
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
        ProviderHelper.deleteRedirectionListItem(mActivity, mCurrentRowId);
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

        TextView hostnameTextView = (TextView) v.findViewWithTag("hostname_" + position);
        TextView ipTextView = (TextView) v.findViewWithTag("ip_" + position);

        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder.setCancelable(true);
        builder.setTitle(getString(R.string.checkbox_list_edit_dialog_title));

        // build view from layout
        LayoutInflater factory = LayoutInflater.from(mActivity);
        final View dialogView = factory.inflate(R.layout.lists_redirection_dialog, null);
        final EditText hostnameEditText = (EditText) dialogView
                .findViewById(R.id.list_dialog_hostname);
        final EditText ipEditText = (EditText) dialogView.findViewById(R.id.list_dialog_ip);

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

                        if (ValidationUtils.isValidHostname(hostname)) {
                            if (ValidationUtils.isValidIP(ip)) {
                                ProviderHelper.updateRedirectionListItemHostnameAndIp(mActivity,
                                        mCurrentRowId, hostname, ip);
                            } else {
                                AlertDialog alertDialog = new AlertDialog.Builder(mActivity)
                                        .create();
                                alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
                                alertDialog.setTitle(R.string.no_ip_title);
                                alertDialog.setMessage(getString(org.adaway.R.string.no_ip));
                                alertDialog.setButton(getString(R.string.button_close),
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dlg, int sum) {
                                                dlg.dismiss();
                                            }
                                        });
                                alertDialog.show();
                            }
                        } else {
                            AlertDialog alertDialog = new AlertDialog.Builder(mActivity).create();
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
                ProviderHelper.updateRedirectionListItemEnabled(mActivity, mCurrentRowId, false);
            } else {
                cBox.setChecked(true);
                ProviderHelper.updateRedirectionListItemEnabled(mActivity, mCurrentRowId, true);
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
        final View dialogView = factory.inflate(R.layout.lists_redirection_dialog, null);
        final EditText hostnameEditText = (EditText) dialogView
                .findViewById(R.id.list_dialog_hostname);
        final EditText ipEditText = (EditText) dialogView.findViewById(R.id.list_dialog_ip);

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

                        addEntry(hostname, ip);
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
    private void addEntry(String hostname, String ip) {
        if (hostname != null) {
            if (ValidationUtils.isValidHostname(hostname)) {
                if (ValidationUtils.isValidIP(ip)) {
                    ProviderHelper.insertRedirectionListItem(mActivity, hostname, ip);
                } else {
                    AlertDialog alertDialog = new AlertDialog.Builder(mActivity).create();
                    alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
                    alertDialog.setTitle(R.string.no_ip_title);
                    alertDialog.setMessage(getString(org.adaway.R.string.no_ip));
                    alertDialog.setButton(getString(R.string.button_close),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dlg, int sum) {
                                    dlg.dismiss();
                                }
                            });
                    alertDialog.show();
                }
            } else {
                AlertDialog alertDialog = new AlertDialog.Builder(mActivity).create();
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
     * Define Adapter and Loader on create of Activity
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mActivity = this.getActivity();

        // register long press context menu
        registerForContextMenu(getListView());

        // Give some text to display if there is no data. In a real
        // application this would come from a resource.
        setEmptyText(getString(R.string.checkbox_list_empty) + "\n\n"
                + getString(R.string.checkbox_list_empty_text));

        // We have a menu item to show in action bar.
        setHasOptionsMenu(true);

        // dislayFields and displayViews are handled in custom adapter!
        String[] displayFields = new String[] {};
        int[] displayViews = new int[] {};
        mAdapter = new RedirectionCursorAdapter(mActivity, R.layout.checkbox_list_two_entry, null,
                displayFields, displayViews, 0);
        setListAdapter(mAdapter);

        // Start out with a progress indicator.
        setListShown(false);

        // Prepare the loader. Either re-connect with an existing one,
        // or start a new one.
        getLoaderManager().initLoader(0, null, this);
    }

    // These are the rows that we will retrieve.
    static final String[] REDIRECTION_LIST_SUMMARY_PROJECTION = new String[] { RedirectionList._ID,
            RedirectionList.HOSTNAME, RedirectionList.IP, RedirectionList.ENABLED };

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // This is called when a new Loader needs to be created. This
        // sample only has one Loader, so we don't care about the ID.
        Uri baseUri = RedirectionList.CONTENT_URI;

        // Now create and return a CursorLoader that will take care of
        // creating a Cursor for the data being displayed.
        return new CursorLoader(getActivity(), baseUri, REDIRECTION_LIST_SUMMARY_PROJECTION, null,
                null, RedirectionList.DEFAULT_SORT);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // Swap the new cursor in. (The framework will take care of closing the
        // old cursor once we return.)
        mAdapter.swapCursor(data);

        // The list should now be shown.
        if (isResumed()) {
            setListShown(true);
        } else {
            setListShownNoAnimation(true);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // This is called when the last Cursor provided to onLoadFinished()
        // above is about to be closed. We need to make sure we are no
        // longer using it.
        mAdapter.swapCursor(null);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true); // enable options menu for this fragment
    }
}