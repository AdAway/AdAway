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

package org.adaway.ui.tcpdump;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import org.adaway.R;
import org.adaway.provider.ProviderHelper;
import org.adaway.util.Constants;
import org.adaway.util.Log;

import java.util.List;

public class TcpdumpLogFragment extends ListFragment implements
        LoaderManager.LoaderCallbacks<List<String>> {
    private Activity mActivity;
    private ArrayAdapter<String> mAdapter;

    /**
     * Context Menu on Long Click
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        // Get the info on which item was selected
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;

        // Retrieve the item that was clicked on
        String hostname = mAdapter.getItem(info.position);

        android.view.MenuInflater inflater = mActivity.getMenuInflater();
        menu.setHeaderTitle(hostname);
        inflater.inflate(R.menu.tcpdump_log_context, menu);
    }

    /**
     * Context Menu Items
     */
    @Override
    public boolean onContextItemSelected(android.view.MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();

        String hostname = mAdapter.getItem(info.position);

        switch (item.getItemId()) {
            case R.id.tcpdump_log_context_blacklist:
                ProviderHelper.insertBlacklistItem(mActivity, hostname);
                Toast toastBlacklist = Toast.makeText(mActivity,
                        R.string.toast_tcpdump_added_to_blacklist, Toast.LENGTH_SHORT);
                toastBlacklist.show();
                return true;
            case R.id.tcpdump_log_context_whitelist:
                ProviderHelper.insertWhitelistItem(mActivity, hostname);
                Toast toastWhitelist = Toast.makeText(mActivity,
                        R.string.toast_tcpdump_added_to_whitelist, Toast.LENGTH_SHORT);
                toastWhitelist.show();
                return true;
            case R.id.tcpdump_log_context_browser:
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse("http://" + hostname));
                startActivity(i);
                return true;
            default:
                return super.onContextItemSelected(item);
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
        setEmptyText(mActivity.getString(R.string.tcpdump_log_empty));

        // Create an empty adapter we will use to display the loaded data.
        String[] values = new String[]{};
        mAdapter = new ArrayAdapter<>(mActivity, android.R.layout.simple_list_item_1, values);
        setListAdapter(mAdapter);

        // Start out with a progress indicator.
        setListShown(false);

        // Prepare the loader. Either re-connect with an existing one,
        // or start a new one.
        getLoaderManager().initLoader(0, null, this);
    }

    @NonNull
    @Override
    public Loader<List<String>> onCreateLoader(int id, Bundle args) {
        return new TcpdumpLogLoader(mActivity);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<List<String>> loader, List<String> data) {
        // Set the new data in the adapter.
        // for (String item : data) {
        // mAdapter.add(item);
        // }
        Log.d(Constants.TAG, "data: " + data);
        // TODO: real swapping the data to the already defined adapter doesn't work
        // Workaround: recreate adapter!
        // http://stackoverflow.com/questions/2356091/android-add-function-of-arrayadapter-not-working
        mAdapter = new ArrayAdapter<>(mActivity, android.R.layout.simple_list_item_1, data);
        mAdapter.notifyDataSetChanged();
        setListAdapter(mAdapter);

        // The list should now be shown.
        if (isResumed()) {
            setListShown(true);
        } else {
            setListShownNoAnimation(true);
        }
    }

    @Override
    public void onLoaderReset(@NonNull Loader<List<String>> loader) {
        // Clear the data in the adapter.
        mAdapter.clear();
    }
}