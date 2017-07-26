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

package org.adaway.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.Editable;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;

import org.adaway.R;
import org.adaway.provider.AdAwayContract.Whitelist;
import org.adaway.provider.ProviderHelper;
import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.adaway.util.RegexUtils;

public class WhitelistFragment extends ListFragment implements
        LoaderManager.LoaderCallbacks<Cursor>, ListsFragmentPagerAdapter.AddItemActionListener {
    private FragmentActivity mActivity;
    private ListsCursorAdapter mAdapter;

    private long mCurrentRowId;

    /**
     * Context Menu on Long Click
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        android.view.MenuInflater inflater = mActivity
                .getMenuInflater();
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

        ProviderHelper.deleteWhitelistItem(mActivity, mCurrentRowId);
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

        CheckBox cBox = v.findViewWithTag(position);

        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder.setCancelable(true);
        builder.setTitle(getString(R.string.checkbox_list_edit_dialog_title));

        // build view from layout
        LayoutInflater factory = LayoutInflater.from(mActivity);
        final View dialogView = factory.inflate(R.layout.lists_whitelist_hostname_dialog, null);
        final EditText inputEditText = dialogView.findViewById(R.id.list_dialog_hostname);
        inputEditText.setText(cBox.getText());

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

                        if (RegexUtils.isValidWhitelistHostname(input)) {
                            ProviderHelper.updateWhitelistItemHostname(mActivity, mCurrentRowId,
                                    input);
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
        CheckBox cBox = v.findViewWithTag(position);

        if (cBox != null) {
            if (cBox.isChecked()) {
                cBox.setChecked(false);
                // change status based on row id from cursor
                ProviderHelper.updateWhitelistItemEnabled(mActivity, mCurrentRowId, false);
            } else {
                cBox.setChecked(true);
                ProviderHelper.updateWhitelistItemEnabled(mActivity, mCurrentRowId, true);
            }
        } else {
            Log.e(Constants.TAG, "Checkbox could not be found!");
        }
    }

    @Override
    public void addItem() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder.setCancelable(true);
        builder.setTitle(getString(R.string.checkbox_list_add_dialog_title));

        // build view from layout
        LayoutInflater factory = LayoutInflater.from(mActivity);
        final View dialogView = factory.inflate(R.layout.lists_whitelist_hostname_dialog, null);
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
                        addEntry(input);
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
     * Add new entry based on input
     *
     * @param input
     */
    private void addEntry(String input) {
        if (input != null) {
            if (RegexUtils.isValidWhitelistHostname(input)) {
                ProviderHelper.insertWhitelistItem(mActivity, input);
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
                        }
                );
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

        mAdapter = new ListsCursorAdapter(mActivity);
        setListAdapter(mAdapter);

        // Start out with a progress indicator.
        setListShown(false);

        // Prepare the loader. Either re-connect with an existing one,
        // or start a new one.
        getLoaderManager().initLoader(0, null, this);
    }

    // These are the rows that we will retrieve.
    static final String[] WHITELIST_SUMMARY_PROJECTION = new String[]{Whitelist._ID,
            Whitelist.HOSTNAME, Whitelist.ENABLED};

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // This is called when a new Loader needs to be created. This
        // sample only has one Loader, so we don't care about the ID.
        Uri baseUri = Whitelist.CONTENT_URI;

        // Now create and return a CursorLoader that will take care of
        // creating a Cursor for the data being displayed.
        return new CursorLoader(getActivity(), baseUri, WHITELIST_SUMMARY_PROJECTION, null, null,
                Whitelist.DEFAULT_SORT);
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
}