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
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.adaway.R;
import org.adaway.db.entity.HostsSource;
import org.adaway.helper.ImportExportHelper;
import org.adaway.ui.dialog.ActivityNotFoundDialogFragment;
import org.adaway.ui.dialog.AlertDialogValidator;
import org.adaway.ui.hostsinstall.HostsInstallSnackbar;
import org.adaway.util.Constants;
import org.adaway.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.app.Activity.RESULT_OK;
import static android.content.Intent.ACTION_GET_CONTENT;
import static android.content.Intent.CATEGORY_OPENABLE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static org.adaway.helper.ImportExportHelper.REQUEST_CODE_IMPORT;
import static org.adaway.helper.ImportExportHelper.REQUEST_CODE_WRITE_STORAGE_PERMISSION;

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

    /**
     * Ensure a permission is granted.<br>
     * If the permission is not granted, a request is shown to user.
     *
     * @param permission The permission to check
     * @return <code>true</code> if the permission is granted, <code>false</code> otherwise.
     */
    private boolean checkPermission(String permission) {
        // Get application context
        Context context = this.getContext();
        if (context == null) {
            // Return permission failed as no context to check
            return false;
        }
        int permissionCheck = ContextCompat.checkSelfPermission(context, permission);
        if (permissionCheck != PERMISSION_GRANTED) {
            // Request write external storage permission
            this.requestPermissions(
                    new String[]{permission},
                    REQUEST_CODE_WRITE_STORAGE_PERMISSION
            );
            // Return permission not granted yes
            return false;
        }
        // Return permission granted
        return true;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Check permission request code
        if (requestCode != REQUEST_CODE_WRITE_STORAGE_PERMISSION) {
            return;
        }
        // Check results
        if (grantResults.length == 0 || grantResults[0] != PERMISSION_GRANTED) {
            return;
        }
        // Restart action according granted permission
        switch (permissions[0]) {
            case READ_EXTERNAL_STORAGE:
                importFromBackup();
                break;
            case WRITE_EXTERNAL_STORAGE:
                exportToBackup();
                break;
        }
    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Store activity
        this.mActivity = this.getActivity();
        this.setHasOptionsMenu(true);
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


    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.backup_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // Check item identifier
        switch (item.getItemId()) {
            case R.id.menu_import:
                // Check read storage permission
                if (checkPermission(READ_EXTERNAL_STORAGE)) {
                    importFromBackup();
                }
                return true;
            case R.id.menu_export:
                // Check write storage permission
                if (checkPermission(WRITE_EXTERNAL_STORAGE)) {
                    exportToBackup();
                }
                return true;
            default:
                // Delegate item selection
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Check request code
        if (requestCode != REQUEST_CODE_IMPORT) {
            return;
        }
        // Check result
        if (resultCode != RESULT_OK) {
            return;
        }
        // Check data
        if (data != null && data.getData() != null) {
            // Get selected file URI
            Uri backupUri = data.getData();
            Log.d(Constants.TAG, "Backup URI: " + backupUri.toString());
            // Import user backup
            ImportExportHelper.importFromBackup(this.getContext(), backupUri);
        }
    }

    /**
     * Import from a user backup.
     */
    private void importFromBackup() {
        Intent intent = new Intent(ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(CATEGORY_OPENABLE);
        // Start file picker activity
        try {
            startActivityForResult(intent, REQUEST_CODE_IMPORT);
        } catch (ActivityNotFoundException exception) {
            // Show dialog to install file picker
            FragmentManager fragmentManager = getFragmentManager();
            if (fragmentManager != null) {
                ActivityNotFoundDialogFragment.newInstance(
                        R.string.no_file_manager_title,
                        R.string.no_file_manager,
                        "market://details?id=org.openintents.filemanager",
                        "OI File Manager"
                ).show(fragmentManager, "notFoundDialog");
            }
        }
    }

    /**
     * Exports to a user backup.
     */
    private void exportToBackup() {
        ImportExportHelper.exportToBackup(this.mActivity);
    }

    /**
     * Add a hosts source.
     */
    private void addSource() {
        // Create dialog view
        LayoutInflater factory = LayoutInflater.from(mActivity);
        View view = factory.inflate(R.layout.hosts_sources_dialog, null);
        // Move cursor to end of EditText
        EditText inputEditText = view.findViewById(R.id.hosts_add_dialog_url);
        Editable inputEditContent = inputEditText.getText();
        inputEditText.setSelection(inputEditContent.length());
        // Create dialog
        AlertDialog alertDialog = new MaterialAlertDialogBuilder(this.mActivity)
                .setTitle(R.string.hosts_add_dialog_title)
                .setCancelable(true)
                .setView(view)
                // Setup buttons
                .setPositiveButton(
                        R.string.button_add,
                        (dialog, which) -> {
                            String url = inputEditText.getText().toString();
                            if (HostsSource.isValidUrl(url)) {
                                // Insert hosts source into database
                                this.mViewModel.addSourceFromUrl(url);
                            }
                            dialog.dismiss();
                        }
                )
                .setNegativeButton(
                        R.string.button_cancel,
                        (dialog, which) -> dialog.dismiss()
                )
                .create();
        // Display dialog
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
        // Create dialog view
        LayoutInflater factory = LayoutInflater.from(mActivity);
        View view = factory.inflate(R.layout.hosts_sources_dialog, null);
        // Set source URL
        EditText inputEditText = view.findViewById(R.id.hosts_add_dialog_url);
        inputEditText.setText(editedSource.getUrl());
        // Move cursor to end of EditText
        Editable inputEditContent = inputEditText.getText();
        inputEditText.setSelection(inputEditContent.length());
        // Create dialog builder
        AlertDialog alertDialog = new MaterialAlertDialogBuilder(this.mActivity)
                .setTitle(R.string.hosts_edit_dialog_title)
                .setCancelable(true)
                .setView(view)
                // Setup buttons
                .setPositiveButton(getResources().getString(R.string.button_save),
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
                )
                .setNegativeButton(getResources().getString(R.string.button_cancel),
                        (dialog, which) -> {
                            // Close dialog
                            dialog.dismiss();
                            // Finish action mode
                            HostsSourcesFragment.this.mActionMode.finish();
                        }
                )
                .create();
        // Display dialog
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
