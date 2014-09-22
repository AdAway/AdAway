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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.adaway.R;
import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.adaway.util.ScanAdwareLoader;

import com.actionbarsherlock.app.SherlockListFragment;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Button;

public class ScanAdwareListFragment extends SherlockListFragment implements
        LoaderManager.LoaderCallbacks<List<Map<String, String>>> {
    private Activity mActivity;
    private SimpleAdapter mAdapter;
    private Button mStartButton;

    @Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        Map<String, String> item = (Map<String, String>) listView.getItemAtPosition(position);

        String packageName = item.get("package_name");

        // offer uninstall
        Intent i = new Intent(Intent.ACTION_DELETE);
        i.setData(Uri.parse("package:" + packageName));
        startActivity(i);
    }

    /**
     * Resume is called after rotating and also after deinstalling a app and coming back to AdAway
     */
    @Override
    public void onResume() {
        super.onResume();

        // Start out with a progress indicator.
        setListShown(false);
        
        // disable Start scanning button
        mStartButton.setEnabled(false);

        // Prepare the loader. Either re-connect with an existing one,
        // or start a new one.
        getLoaderManager().initLoader(0, null, this);
    }

    /**
     * Define Adapter and Loader on create of Activity
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mActivity = this.getActivity();
        mStartButton = (Button) mActivity.findViewById(R.id.scan_adware_start_button);

        // register long press context menu
        registerForContextMenu(getListView());

        // Give some text to display if there is no data. In a real
        // application this would come from a resource.
        setEmptyText(mActivity.getString(R.string.scan_adware_empty));

        // Create an empty adapter we will use to display the loaded data.
        String[] from = new String[]{};
        int[] to = new int[]{};
        List<Map<String, String>> data = new ArrayList<Map<String, String>>();
        mAdapter = new SimpleAdapter(getActivity(), data, android.R.layout.two_line_list_item,
                from, to);
        setListAdapter(mAdapter);

        // Start out with a progress indicator.
        setListShown(false);
    }

    @Override
    public Loader<List<Map<String, String>>> onCreateLoader(int id, Bundle args) {
        return new ScanAdwareLoader(mActivity);
    }

    @Override
    public void onLoadFinished(Loader<List<Map<String, String>>> loader,
                               List<Map<String, String>> data) {
        // Set the new data in the adapter.
        // for (String item : data) {
        // mAdapter.add(item);
        // }
        Log.d(Constants.TAG, "data: " + data);
        // TODO: real swapping the data to the already defined adapter doesn't work
        // Workaround: recreate adapter!
        // http://stackoverflow.com/questions/2356091/android-add-function-of-arrayadapter-not-working
        // mAdapter = new ArrayAdapter<String>(mActivity, android.R.layout.two_line_list_item,
        // data);

        String[] from = new String[]{"app_name", "package_name"};
        int[] to = new int[]{android.R.id.text1, android.R.id.text2};
        mAdapter = new SimpleAdapter(getActivity(), data, android.R.layout.two_line_list_item,
                from, to);

        mAdapter.notifyDataSetChanged();
        setListAdapter(mAdapter);

        // The list should now be shown.
        if (isResumed()) {
            setListShown(true);
        } else {
            setListShownNoAnimation(true);
        }

        // enable Start scanning button
        mStartButton.setEnabled(true);
    }

    @Override
    public void onLoaderReset(Loader<List<Map<String, String>>> loader) {
        // Clear the data in the adapter.
        // Not available in SimpleAdapter!
        // mAdapter.clear();
    }
}