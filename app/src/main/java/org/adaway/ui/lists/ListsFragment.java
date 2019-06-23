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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.adaway.R;
import org.adaway.helper.ImportExportHelper;
import org.adaway.ui.dialog.ActivityNotFoundDialogFragment;
import org.adaway.ui.hostsinstall.HostsInstallSnackbar;
import org.adaway.util.Constants;
import org.adaway.util.Log;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.app.Activity.RESULT_OK;
import static android.content.Intent.ACTION_GET_CONTENT;
import static android.content.Intent.CATEGORY_OPENABLE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static org.adaway.helper.ImportExportHelper.REQUEST_CODE_IMPORT;
import static org.adaway.helper.ImportExportHelper.REQUEST_CODE_WRITE_STORAGE_PERMISSION;

/**
 * This class is a fragment to display black list, white list and redirect list fragments.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class ListsFragment extends Fragment {
    /**
     * The fragment activity (<code>null</code> if view not created).
     */
    private FragmentActivity mActivity;

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
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Store activity
        this.mActivity = this.getActivity();
        // Enable option menu
        this.setHasOptionsMenu(true);
        // Create fragment view
        View view = inflater.inflate(R.layout.lists_fragment, container, false);
        /*
         * Configure snackbar.
         */
        // Get lists layout to attached snackbar to
        CoordinatorLayout coordinatorLayout = view.findViewById(R.id.coordinator);
        // Create install snackbar
        HostsInstallSnackbar installSnackbar = new HostsInstallSnackbar(coordinatorLayout);
        // Bind snakbar to view models
        ListsViewModel listsViewModel = ViewModelProviders.of(this).get(ListsViewModel.class);
        listsViewModel.getBlackListItems().observe(this, installSnackbar.createObserver());
        listsViewModel.getWhiteListItems().observe(this, installSnackbar.createObserver());
        listsViewModel.getRedirectionListItems().observe(this, installSnackbar.createObserver());
        /*
         * Configure tabs.
         */
        // Get view pager
        final ViewPager viewPager = view.findViewById(R.id.lists_view_pager);
        // Create pager adapter
        final ListsFragmentPagerAdapter pagerAdapter = new ListsFragmentPagerAdapter(this.getActivity(), this.getFragmentManager());
        // Set view pager adapter
        viewPager.setAdapter(pagerAdapter);
        // Get navigation view
        BottomNavigationView navigationView = view.findViewById(R.id.navigation);
        // Add view pager on page listener to set selected tab according the selected page
        viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                navigationView.getMenu().getItem(position).setChecked(true);
                pagerAdapter.ensureActionModeCanceled();
            }
        });
        // Add navigation view item selected listener to change view pager current item
        navigationView.setOnNavigationItemSelectedListener(item -> {
            switch (item.getItemId()) {
                case R.id.lists_navigation_blacklist:
                    viewPager.setCurrentItem(0);
                    return true;
                case R.id.lists_navigation_whitelist:
                    viewPager.setCurrentItem(1);
                    return true;
                case R.id.lists_navigation_redirection_list:
                    viewPager.setCurrentItem(2);
                    return true;
            }
            return false;
        });
        /*
         * Configure add action button.
         */
        // Get the add action button
        FloatingActionButton addActionButton = view.findViewById(R.id.lists_add);
        // Set add action button listener
        addActionButton.setOnClickListener(clickedView -> {
            // Get current fragment position
            int currentItemPosition = viewPager.getCurrentItem();
            // Add item to the current fragment
            pagerAdapter.addItem(currentItemPosition);
        });
        // Return fragment view
        return view;
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
            // Import from backup
            ImportExportHelper.importFromBackup(this.getContext(), backupUri);
        }
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

    /*
     * Menu related.
     */

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
}