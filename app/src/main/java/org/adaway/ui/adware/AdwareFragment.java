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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import org.adaway.R;

import java.util.List;

/**
 * This class is a {@link Fragment} to scan and uninstall adware.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class AdwareFragment extends Fragment {
    /**
     * The adware install list view.
     */
    private ListView mListView;
    /**
     * The status text.
     */
    private TextView mStatusText;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Create fragment view
        View view = inflater.inflate(R.layout.adware_fragment, container, false);
        // Get list view
        this.mListView = view.findViewById(R.id.adware_list);
        // Bind list onclick listener
        this.mListView.setOnItemClickListener((parent, view1, position, id) -> {
            // Get clicked adware
            AdwareInstall adwareInstall = (AdwareInstall) parent.getItemAtPosition(position);
            // Uninstall adware
            AdwareFragment.this.uninstallAdware(adwareInstall);
        });
        // Get status text
        this.mStatusText = view.findViewById(R.id.adware_status_text);
        /*
         * Get model and bind it to view.
         */
        // Get the model scope
        FragmentActivity activity = requireActivity();
        // Get the model
        AdwareViewModel model = new ViewModelProvider(activity).get(AdwareViewModel.class);
        // Bind model to views
        model.getAdware().observe(getViewLifecycleOwner(), data -> {
            if (data == null) {
                this.displayStatusText(R.string.adware_scanning);
            } else if (data.isEmpty()) {
                this.displayStatusText(R.string.adware_empty);
            } else {
                this.displayAdware(data);
            }
        });
        // Return created view
        return view;
    }

    /**
     * Display a status text.
     *
     * @param text The status text to display.
     */
    private void displayStatusText(int text) {
        // Set text
        this.mStatusText.setText(text);
        // Show the text
        this.mStatusText.setVisibility(View.VISIBLE);
        this.mListView.setVisibility(View.GONE);
    }

    /**
     * Display the installed adware.
     *
     * @param data The adware to show.
     */
    private void displayAdware(List<AdwareInstall> data) {
        // Create adapter
        String[] from = new String[]{
                AdwareInstall.APPLICATION_NAME_KEY,
                AdwareInstall.PACKAGE_NAME_KEY
        };
        int[] to = new int[]{
                R.id.checkbox_list_text,
                R.id.checkbox_list_subtext
        };
        SimpleAdapter adapter = new SimpleAdapter(this.getContext(),
                data,
                R.layout.list_two_entries,
                from,
                to
        );
        // Update list
        adapter.notifyDataSetChanged();
        // Show the list
        this.mListView.setAdapter(adapter);
        this.mStatusText.setVisibility(View.GONE);
        this.mListView.setVisibility(View.VISIBLE);
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
