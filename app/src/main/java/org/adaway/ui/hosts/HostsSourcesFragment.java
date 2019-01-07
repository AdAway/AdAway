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

package org.adaway.ui.hosts;

import android.app.Activity;
import android.app.AlertDialog;
import android.arch.lifecycle.ViewModelProviders;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.recyclerview.extensions.ListAdapter;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import org.adaway.R;
import org.adaway.db.entity.HostsSource;
import org.adaway.ui.dialog.AlertDialogValidator;
import org.adaway.ui.hostsinstall.HostsInstallSnackbar;

/**
 * This class is a {@link Fragment} to display and manage hosts sources.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class HostsSourcesFragment extends Fragment implements HostsSourcesViewCallback {
    /**
     * The current activity (<code>null</code> if view is not created).
     */
    private Activity mActivity;
    /**
     * The view model (<code>null</code> if view is not created).
     */
    private HostsSourcesViewModel mViewModel;
    /**
     * The current action mode when item is selection (<code>null</code> if no action started).
     */
    private ActionMode mActionMode;
    /**
     * The action mode callback (<code>null</code> if view is not created).
     */
    private ActionMode.Callback mActionCallback;
    /**
     * The hosts source related to the current action (<code>null</code> if view is not created).
     */
    private HostsSource mActionSource;
    /**
     * The view related hosts source of the current action (<code>null</code> if view is not created).
     */
    private View mActionSourceView;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Store activity
        this.mActivity = this.getActivity();
        // Initialize view model
        this.mViewModel = ViewModelProviders.of(this).get(HostsSourcesViewModel.class);
        // Create fragment view
        View view = inflater.inflate(R.layout.hosts_sources_fragment, container, false);
        /*
         * Configure snackbar.
         */
        // Get lists layout to attached snackbar to
        CoordinatorLayout coordinatorLayout = view.findViewById(R.id.coordinator);
        // Create install snackbar
        HostsInstallSnackbar installSnackbar = new HostsInstallSnackbar(coordinatorLayout);
        installSnackbar.setIgnoreEventDuringInstall(true);
        // Bind snakbar to view models
        this.mViewModel.getHostsSources().observe(this, installSnackbar.createObserver());
        /*
         * Configure recycler view.
         */
        // Store recycler view
        RecyclerView recyclerView = view.findViewById(R.id.hosts_sources_list);
        recyclerView.setHasFixedSize(true);
        // Defile recycler layout
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this.mActivity);
        recyclerView.setLayoutManager(linearLayoutManager);
        // Create recycler adapter
        ListAdapter adapter = new HostsSourcesAdapter(this);
        recyclerView.setAdapter(adapter);
        // Bind adapter to view model
        this.mViewModel.getHostsSources().observe(this, adapter::submitList);
        /*
         * Create action mode.
         */
        // Create action mode callback to display edit/delete menu
        this.mActionCallback = new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
                // Get menu inflater
                MenuInflater inflater = actionMode.getMenuInflater();
                // Set action mode title
                actionMode.setTitle(R.string.checkbox_list_context_title);
                // Inflate edit/delete menu
                inflater.inflate(R.menu.checkbox_list_context, menu);
                // Return action created
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
                // Nothing special to do
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode actionMode, MenuItem item) {
                // Check item identifier
                switch (item.getItemId()) {
                    case R.id.checkbox_list_context_edit:
                        HostsSourcesFragment.this.editSource();
                        return true;
                    case R.id.checkbox_list_context_delete:
                        HostsSourcesFragment.this.deleteSource();
                        return true;
                    default:
                        return false;
                }
            }

            @Override
            public void onDestroyActionMode(ActionMode actionMode) {
                // Clear view background color
                if (HostsSourcesFragment.this.mActionSourceView != null) {
                    HostsSourcesFragment.this.mActionSourceView.setBackgroundColor(Color.TRANSPARENT);
                }
                // Clear current source and its view
                HostsSourcesFragment.this.mActionSource = null;
                HostsSourcesFragment.this.mActionSourceView = null;
                // Clear action mode
                HostsSourcesFragment.this.mActionMode = null;
            }
        };
        /*
         * Add floating action button.
         */
        // Get floating action button
        FloatingActionButton button = view.findViewById(R.id.hosts_sources_add);
        // Set click listener to display menu add entry
        button.setOnClickListener(actionButton -> {
            // Display menu add entry
            HostsSourcesFragment.this.addSource();
        });
        // Return fragment view
        return view;
    }


    @Override
    public void toggleEnabled(HostsSource source) {
        this.mViewModel.toggleSourceEnabled(source);
    }

    @Override
    public boolean startAction(HostsSource source, View sourceView) {
        // Check if there is already a current action
        if (this.mActionMode != null) {
            return false;
        }
        // Store current source and its view
        this.mActionSource = source;
        this.mActionSourceView = sourceView;
        // Get current item background color
        int currentItemBackgroundColor = this.getResources().getColor(R.color.selected_background);
        // Apply background color to view
        this.mActionSourceView.setBackgroundColor(currentItemBackgroundColor);
        // Start action mode and store it
        this.mActionMode = this.mActivity.startActionMode(this.mActionCallback);
        // Return event consumed
        return true;
    }

    /**
     * Add a hosts source.
     */
    private void addSource() {
        // Create dialog builder
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder.setTitle(R.string.hosts_add_dialog_title);
        builder.setCancelable(true);
        // Create dialog view
        LayoutInflater factory = LayoutInflater.from(mActivity);
        View view = factory.inflate(R.layout.hosts_sources_dialog, null);
        builder.setView(view);
        // Move cursor to end of EditText
        EditText inputEditText = view.findViewById(R.id.hosts_add_dialog_url);
        Editable inputEditContent = inputEditText.getText();
        inputEditText.setSelection(inputEditContent.length());
        // Setup buttons
        builder.setPositiveButton(
                R.string.button_add,
                (dialog, which) -> {
                    String url = inputEditText.getText().toString();
                    if (HostsSource.isValidUrl(url)) {
                        // Insert hosts source into database
                        this.mViewModel.addSourceFromUrl(url);
                    }
                    dialog.dismiss();
                }
        );
        builder.setNegativeButton(
                R.string.button_cancel,
                (dialog, which) -> dialog.dismiss()
        );
        // Display dialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
        // Set button validation behavior
        inputEditText.addTextChangedListener(
                new AlertDialogValidator(alertDialog, HostsSource::isValidUrl, false)
        );
    }

    /**
     * Edit selected hosts source.
     */
    private void editSource() {
        // Check action source
        if (this.mActionSource == null) {
            return;
        }
        HostsSource editedSource = this.mActionSource;
        // Create dialog builder
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder.setTitle(R.string.hosts_edit_dialog_title);
        builder.setCancelable(true);
        // Create dialog view
        LayoutInflater factory = LayoutInflater.from(mActivity);
        View view = factory.inflate(R.layout.hosts_sources_dialog, null);
        builder.setView(view);
        // Set source URL
        EditText inputEditText = view.findViewById(R.id.hosts_add_dialog_url);
        inputEditText.setText(editedSource.getUrl());
        // Move cursor to end of EditText
        Editable inputEditContent = inputEditText.getText();
        inputEditText.setSelection(inputEditContent.length());
        // Setup buttons
        builder.setPositiveButton(getResources().getString(R.string.button_save),
                (dialog, which) -> {
                    // Close dialog
                    dialog.dismiss();
                    // Finish action mode
                    HostsSourcesFragment.this.mActionMode.finish();
                    // Check url validity
                    String url = inputEditText.getText().toString();
                    if (HostsSource.isValidUrl(url)) {
                        // Update hosts source into database
                        this.mViewModel.updateSourceUrl(editedSource, url);
                    }
                }
        );
        builder.setNegativeButton(getResources().getString(R.string.button_cancel),
                (dialog, which) -> {
                    // Close dialog
                    dialog.dismiss();
                    // Finish action mode
                    HostsSourcesFragment.this.mActionMode.finish();
                }
        );
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
        // Set button validation behavior
        inputEditText.addTextChangedListener(
                new AlertDialogValidator(alertDialog, HostsSource::isValidUrl, true)
        );
    }

    /**
     * Delete selected hosts source.
     */
    private void deleteSource() {
        // Check current source
        if (this.mActionSource == null) {
            return;
        }
        // Remove related hosts source
        this.mViewModel.removeSource(this.mActionSource);
        // Finish action mode
        this.mActionMode.finish();
    }
}
