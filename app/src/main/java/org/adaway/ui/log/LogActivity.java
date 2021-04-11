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

package org.adaway.ui.log;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.adaway.R;
import org.adaway.databinding.LogActivityBinding;
import org.adaway.databinding.LogRedirectDialogBinding;
import org.adaway.db.entity.ListType;
import org.adaway.helper.ThemeHelper;
import org.adaway.ui.adblocking.ApplyConfigurationSnackbar;
import org.adaway.ui.dialog.AlertDialogValidator;
import org.adaway.util.RegexUtils;

import static java.lang.Boolean.TRUE;

/**
 * This class is an {@link android.app.Activity} to show DNS request log entries.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class LogActivity extends AppCompatActivity implements LogViewCallback {
    private LogActivityBinding binding;
    /**
     * The view model (<code>null</code> if activity is not created).
     */
    private LogViewModel mViewModel;
    /**
     * The snackbar notification (<code>null</code> if activity is not created).
     */
    private ApplyConfigurationSnackbar mApplySnackbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        /*
         * Create activity.
         */
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        this.binding = LogActivityBinding.inflate(getLayoutInflater());
        setContentView(this.binding.getRoot());
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        /*
         * Configure swipe layout.
         */
        this.binding.swipeRefresh.setOnRefreshListener(() -> this.mViewModel.updateLogs());
        /*
         * Configure recycler view.
         */
        // Get recycler view
        this.binding.logList.setHasFixedSize(true);
        // Defile recycler layout
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        this.binding.logList.setLayoutManager(linearLayoutManager);
        // Get view model
        this.mViewModel = new ViewModelProvider(this).get(LogViewModel.class);
        // Create recycler adapter
        LogAdapter adapter = new LogAdapter(this);
        this.binding.logList.setAdapter(adapter);
        /*
         * Configure fab.
         */
        this.binding.toggleLogRecording.setOnClickListener(v -> this.mViewModel.toggleRecording());
        this.mViewModel.isRecording().observe(this, recoding ->
                this.binding.toggleLogRecording.setImageResource(TRUE.equals(recoding) ?
                        R.drawable.ic_pause_24dp :
                        R.drawable.ic_record_24dp
                )
        );
        /*
         * Configure snackbar.
         */
        // Create apply snackbar
        this.mApplySnackbar = new ApplyConfigurationSnackbar(this.binding.swipeRefresh, false, false);
        /*
         * Load data.
         */
        // Bind view model to the list view
        this.mViewModel.getLogs().observe(this, logEntries -> {
            adapter.submitList(logEntries);
            this.binding.swipeRefresh.setRefreshing(false);
        });
        // Mark as loading data
        this.binding.swipeRefresh.setRefreshing(true);
        // Load initial data
        this.mViewModel.updateLogs();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.log_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.sort) {
            this.mViewModel.toggleSort();
            return true;
        } else if (item.getItemId() == R.id.delete) {
            this.mViewModel.clearLogs();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void addListItem(@NonNull String hostName, @NonNull ListType type) {
        // Check view model and snackbar notification
        if (this.mViewModel == null || this.mApplySnackbar == null) {
            return;
        }
        // Check type other than redirection
        if (type != ListType.REDIRECTED) {
            // Insert list item
            this.mViewModel.addListItem(hostName, type, null);
            // Display snackbar notification
            this.mApplySnackbar.notifyUpdateAvailable();
        } else {
            // Create dialog view
            LayoutInflater inflater = LayoutInflater.from(this);
            LogRedirectDialogBinding redirectBinding = LogRedirectDialogBinding.inflate(inflater);
            // Create dialog
            AlertDialog alertDialog = new MaterialAlertDialogBuilder(this)
                    .setCancelable(true)
                    .setTitle(R.string.log_redirect_dialog_title)
                    .setView(redirectBinding.getRoot())
                    // Setup buttons
                    .setPositiveButton(
                            R.string.button_add,
                            (dialog, which) -> {
                                // Close dialog
                                dialog.dismiss();
                                // Check IP is valid
                                String ip = redirectBinding.redirectIp.getText().toString();
                                if (RegexUtils.isValidIP(ip)) {
                                    // Insert list item
                                    this.mViewModel.addListItem(hostName, type, ip);
                                    // Display snackbar notification
                                    this.mApplySnackbar.notifyUpdateAvailable();
                                }
                            }
                    )
                    .setNegativeButton(
                            R.string.button_cancel,
                            (dialog, which) -> dialog.dismiss()
                    )
                    .create();
            // Show dialog
            alertDialog.show();
            // Set button validation behavior
            redirectBinding.redirectIp.addTextChangedListener(
                    new AlertDialogValidator(alertDialog, RegexUtils::isValidIP, false)
            );
        }
    }

    @Override
    public void removeListItem(@NonNull String hostName) {
        if (this.mViewModel != null && this.mApplySnackbar != null) {
            this.mViewModel.removeListItem(hostName);
            this.mApplySnackbar.notifyUpdateAvailable();
        }
    }

    @Override
    public void openHostInBrowser(@NonNull String hostName) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("http://" + hostName));
        startActivity(intent);
    }
}
