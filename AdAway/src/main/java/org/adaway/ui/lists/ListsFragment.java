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

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.adaway.R;
import org.adaway.helper.ImportExportHelper;
import org.adaway.util.Constants;
import org.adaway.util.Log;

/**
 * This class is a fragment to display black list, white list and redirect list fragments.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class ListsFragment extends Fragment {
    /**
     * The request code to identify the write external storage permission in {@link android.support.v4.app.Fragment#onRequestPermissionsResult(int, java.lang.String[], int[])}.
     */
    private final static int REQUEST_CODE_WRITE_STORAGE_PERMISSION = 10;
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
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            // Request write external storage permission
            this.requestPermissions(
                    new String[]{permission},
                    ListsFragment.REQUEST_CODE_WRITE_STORAGE_PERMISSION
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
         * Configure tabs.
         */
        // Get actionbar
        final ActionBar actionBar = ((AppCompatActivity) this.getActivity()).getSupportActionBar();
        // Get view pager
        final ViewPager viewPager = view.findViewById(R.id.lists_view_pager);
        // Create pager adapter
        final ListsFragmentPagerAdapter pagerAdapter = new ListsFragmentPagerAdapter(this.getActivity(), this.getFragmentManager());
        // Set view pager adapter
        viewPager.setAdapter(pagerAdapter);
        // Create each tab
        for (int index = 0; index < pagerAdapter.getCount(); index++) {
            ActionBar.Tab tab = actionBar.newTab();
            tab.setText(pagerAdapter.getPageTitle(index));
            tab.setTabListener(new ActionBar.TabListener() {
                @Override
                public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft) {
                    viewPager.setCurrentItem(tab.getPosition());
                }

                @Override
                public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft) {
                    // Nothing special to do
                }

                @Override
                public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft) {
                    // Nothing special to do
                }
            });
            actionBar.addTab(tab, index);
        }
        // Enable tab navigation mode
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        // Add view pager on page listener to set selected tab according the selected page
        viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                actionBar.setSelectedNavigationItem(position);
                pagerAdapter.ensureActionModeCanceled();
            }
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
        if (requestCode != ImportExportHelper.REQUEST_CODE_IMPORT) {
            return;
        }
        // Check result
        if (resultCode != Activity.RESULT_OK) {
            return;
        }
        // Check data
        if (data != null && data.getData() != null) {
            // Get selected file URI
            Uri userListsUri = data.getData();
            Log.d(Constants.TAG, "User lists URI: " + userListsUri.toString());
            // Import user lists
            ImportExportHelper.importLists(this.getContext(), userListsUri);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Check permission request code
        if (requestCode != ListsFragment.REQUEST_CODE_WRITE_STORAGE_PERMISSION) {
            return;
        }
        // Check results
        if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        // Check permissions
        if (permissions.length < 0) {
            return;
        }
        // Restart action according granted permission
        switch (permissions[0]) {
            case Manifest.permission.READ_EXTERNAL_STORAGE:
                ImportExportHelper.importLists(this.getActivity());
                break;
            case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                ImportExportHelper.exportLists(this.getContext());
                break;
        }
    }

    /*
     * Menu related.
     */

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        /*
         * Restore actionbar.
         */
        // Get actionbar
        final ActionBar actionBar = ((AppCompatActivity) this.getActivity()).getSupportActionBar();
        // Clear all tabs
        actionBar.removeAllTabs();
        // Restore navigation mode
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.lists_fragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Check item identifier
        switch (item.getItemId()) {
            case R.id.menu_import:
                // Check read storage permission
                if (this.checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    // Import user lists
                    ImportExportHelper.importLists(this.mActivity);
                }
                return true;
            case R.id.menu_export:
                // Check write storage permission
                if (this.checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    // Export user lists
                    ImportExportHelper.exportLists(this.mActivity);
                }
                return true;
            default:
                // Delegate item selection
                return super.onOptionsItemSelected(item);
        }
    }
}