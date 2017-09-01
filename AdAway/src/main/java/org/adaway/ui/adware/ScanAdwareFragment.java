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

package org.adaway.ui.adware;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import org.adaway.R;

import java.util.List;

/**
 * This class is a {@link Fragment} to scan and uninstall adwares.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class ScanAdwareFragment extends Fragment implements LoaderManager.LoaderCallbacks<List<AdwareInstall>> {
    /** The start scan button. */
    private Button mStartButton;
    /** The adware install list view. */
    private ListView mListView;
    /** The scanning loading view. */
    private ProgressBar mProgressbar;
    /** The empty text view. */
    private TextView mEmptyTextView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Create fragment view
        View view = inflater.inflate(R.layout.scan_adware_fragment, container, false);
        // Get start button
        this.mStartButton = view.findViewById(R.id.scan_adware_start_button);
        // Bind start adware scan
        this.mStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ScanAdwareFragment.this.startAdwareScan();
            }
        });
        // Get list view
        this.mListView = view.findViewById(R.id.scan_adware_list);
        // Bind
        this.mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // Get clicked adware
                AdwareInstall adwareInstall = (AdwareInstall) parent.getItemAtPosition(position);
                // Uninstall adware
                ScanAdwareFragment.this.uninstallAdware(adwareInstall);
            }
        });
        // Get loading view
        this.mProgressbar = view.findViewById(R.id.scan_adware_progressbar);
        // Get empty text view
        this.mEmptyTextView = view.findViewById(R.id.scan_adware_empty_textview);
        // Return created view
        return view;
    }

    /*
    * LoaderCallbacks.
     */

    @Override
    public Loader<List<AdwareInstall>> onCreateLoader(int id, Bundle args) {
        // Create loader
        return new AdwareInstallLoader(this.getContext());
    }

    @Override
    public void onLoadFinished(Loader<List<AdwareInstall>> loader, List<AdwareInstall> data) {
        // Stop adware scan
        this.stopAdwareScan();
        // Create adapter
        String[] from = new String[]{
                AdwareInstall.APPLICATION_NAME_KEY,
                AdwareInstall.PACKAGE_NAME_KEY
        };
        int[] to = new int[]{
                android.R.id.text1,
                android.R.id.text2
        };
        SimpleAdapter adapter = new SimpleAdapter(this.getContext(),
                data,
                android.R.layout.two_line_list_item,
                from,
                to
        );
        // Update list
        adapter.notifyDataSetChanged();
        this.mListView.setAdapter(adapter);
    }

    @Override
    public void onLoaderReset(Loader<List<AdwareInstall>> loader) {
        // Stop adware scan
        this.stopAdwareScan();
    }

    /**
     * Start adware scan.
     */
    private void startAdwareScan() {
        // Disable start button
        this.mStartButton.setText(R.string.scan_adware_scanning);
        this.mStartButton.setEnabled(false);
        // Display spinner
        this.mProgressbar.setVisibility(View.VISIBLE);
        // Clear empty view
        this.mListView.setEmptyView(null);
        // Initialize loader
        this.getLoaderManager().initLoader(0, null, this).forceLoad();
    }

    /**
     * Stop adware scan.
     */
    private void stopAdwareScan() {
        // Hide spinner
        this.mProgressbar.setVisibility(View.GONE);
        // Restore start button
        this.mStartButton.setText(R.string.scan_adware_start);
        this.mStartButton.setEnabled(true);
        // Bind empty view
        this.mListView.setEmptyView(this.mEmptyTextView);
    }

    /**
     * Uninstall adware.
     *
     * @param adwareInstall The adware to uninstall.
     */
    private void uninstallAdware(AdwareInstall adwareInstall) {
        Intent intent = new Intent(Intent.ACTION_DELETE);
        intent.setData(Uri.parse("package:" + adwareInstall.get(AdwareInstall.PACKAGE_NAME_KEY)));
        this.startActivity(intent);
    }
}
