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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.adaway.R;
import org.adaway.ui.hostsinstall.HostsInstallSnackbar;

/**
 * This class is a fragment to display black list, white list and redirect list fragments.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class ListsFragment extends Fragment {
    /**
     * The tab to display argument.
     */
    public static final String TAB = "org.adaway.lists.tab";
    /**
     * The blacklist tab index.
     */
    public static final int BLACKLIST_TAB = 0;
    /**
     * The whitelist tab index.
     */
    public static final int WHITELIST_TAB = 1;
    /**
     * The redirection tab index.
     */
    public static final int REDIRECTION_TAB = 2;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
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
                case R.id.lists_navigation_blocked:
                    viewPager.setCurrentItem(0);
                    return true;
                case R.id.lists_navigation_allowed:
                    viewPager.setCurrentItem(1);
                    return true;
                case R.id.lists_navigation_redirected:
                    viewPager.setCurrentItem(2);
                    return true;
            }
            return false;
        });
        // Display requested tab
        Bundle arguments = getArguments();
        if (arguments != null) {
            viewPager.setCurrentItem(arguments.getInt(TAB, BLACKLIST_TAB));
        }
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
//                if (checkPermission(READ_EXTERNAL_STORAGE)) {
//                    importFromBackup();
//                }
                return true;
            case R.id.menu_export:
                // Check write storage permission
//                if (checkPermission(WRITE_EXTERNAL_STORAGE)) {
//                    exportToBackup();
//                }
                return true;
            default:
                // Delegate item selection
                return super.onOptionsItemSelected(item);
        }
    }
}