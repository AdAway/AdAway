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
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.snackbar.Snackbar;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.source.SourceModel;
import org.adaway.model.source.SourceUpdateService;
import org.adaway.ui.adblocking.ApplyConfigurationSnackbar;
import org.adaway.ui.home.HomeActivity;
import org.adaway.ui.source.SourceEditActivity;
import org.adaway.util.AppExecutors;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.adaway.ui.source.SourceEditActivity.SOURCE_ID;

/**
 * This class is a {@link Fragment} to display and manage hosts sources.
 * <p>
 * Uses a categorized view inspired by uBlock Origin, organizing filter lists
 * into expandable categories (Ads, Privacy, Malware, Social, etc.).
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class HostsSourcesFragment extends Fragment implements HostsSourcesViewCallback {
    /**
     * The view model (<code>null</code> if view is not created).
     */
    private HostsSourcesViewModel mViewModel;
    private CategorizedSourcesAdapter mAdapter;
    private CoordinatorLayout coordinatorLayout;
    private ApplyConfigurationSnackbar mApplySnackbar;
    private List<HostsSource> lastSources = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Get activity
        Activity activity = requireActivity();
        // Initialize view model
        this.mViewModel = new ViewModelProvider(this).get(HostsSourcesViewModel.class);
        LifecycleOwner lifecycleOwner = getViewLifecycleOwner();
        // Create fragment view
        View view = inflater.inflate(R.layout.hosts_sources_fragment, container, false);
        setHasOptionsMenu(true);
        
        /*
         * Configure snackbar.
         */
        // Get lists layout to attached snackbar to
        coordinatorLayout = view.findViewById(R.id.coordinator);
        // Create apply snackbar
        ApplyConfigurationSnackbar applySnackbar =
                new ApplyConfigurationSnackbar(coordinatorLayout, this.mViewModel);
        this.mApplySnackbar = applySnackbar;
        // Bind snackbar to view models
        this.mViewModel.getApplyStatus().observe(lifecycleOwner,
                status -> applySnackbar.refreshSourceState());
        this.mViewModel.getHostsSources().observe(lifecycleOwner, this.mViewModel::onSourcesChanged);
        WorkManager.getInstance(requireContext())
                .getWorkInfosForUniqueWorkLiveData(SourceUpdateService.WORK_NAME_NOW)
                .observe(lifecycleOwner, work -> {
                    // REPLACE creates a single update-now request, not an apply chain.
                    if (work == null) return;
                    WorkInfo info = work.size() == 1 ? work.get(0) : null;
                    this.mViewModel.observeAutomaticWork(info == null ? null : info.getId().toString(),
                            info != null && info.getState() == WorkInfo.State.SUCCEEDED,
                            info != null && info.getState() == WorkInfo.State.SUCCEEDED
                                    ? info.getOutputData().getString(
                                            SourceUpdateService.APPLIED_CONFIGURATION) : null);
                });
        
        /*
         * Configure recycler view with categorized adapter.
         */
        RecyclerView recyclerView = view.findViewById(R.id.hosts_sources_list);
        recyclerView.setHasFixedSize(false); // Categories expand/collapse
        
        // Use LinearLayoutManager
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(activity);
        recyclerView.setLayoutManager(linearLayoutManager);
        
        // Create the new categorized adapter (uBlock-style)
        mAdapter = new CategorizedSourcesAdapter(this);
        recyclerView.setAdapter(mAdapter);
        
        // Bind adapter to view model
        this.mViewModel.getHostsSources().observe(lifecycleOwner, sources -> {
            lastSources = sources != null ? new ArrayList<>(sources) : new ArrayList<>();
            mAdapter.updateSources(sources);
        });
        
        /*
         * Add floating action button for adding sources.
         * Shows a bottom sheet with options to browse catalog or add custom.
         */
        FloatingActionButton button = view.findViewById(R.id.hosts_sources_add);
        button.setOnClickListener(actionButton -> showAddSourceOptions());
        
        // Return fragment view
        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.hosts_sources_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        return handleMenuItem(item) || super.onOptionsItemSelected(item);
    }

    boolean handleMenuItem(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_hosts_check_updates) {
            runCheckUpdates();
            return true;
        } else if (id == R.id.action_hosts_update_all) {
            updateAllSources();
            return true;
        } else if (id == R.id.action_hosts_save_filter_set) {
            promptSaveFilterSet();
            return true;
        } else if (id == R.id.action_hosts_apply_filter_set) {
            promptApplyFilterSet();
            return true;
        } else if (id == R.id.action_hosts_schedule_filter_set) {
            promptScheduleFilterSet();
            return true;
        } else if (id == R.id.action_hosts_manage_filter_sets) {
            promptManageFilterSets();
            return true;
        } else if (id == R.id.action_hosts_manage_schedules) {
            startActivity(new Intent(requireContext(), SchedulesActivity.class));
            return true;
        }
        return false;
    }

    @Override
    public void requestAddCustomSource() {
        showAddSourceOptions();
    }

    private void promptSaveFilterSet() {
        TextInputLayout layout = new TextInputLayout(requireContext());
        layout.setHint(getString(R.string.filter_set_name_hint));
        TextInputEditText editText = new TextInputEditText(requireContext());
        layout.addView(editText);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.menu_save_filter_set)
                .setView(layout)
                .setPositiveButton(R.string.button_save, null)
                .setNegativeButton(R.string.button_cancel, null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
                        .setOnClickListener(view -> {
                            String rawName = editText.getText() != null
                                    ? editText.getText().toString() : "";
                            FilterSetStore.SetNameValidation validation =
                                    FilterSetStore.validateSetName(rawName,
                                            FilterSetStore.getSetNames(requireContext()));
                            int error = getFilterSetNameError(validation);
                            if (error != 0) {
                                layout.setError(getString(error));
                                return;
                            }
                            layout.setError(null);
                            String name = FilterSetStore.normalizeActiveProfile(rawName);
                            saveFilterSet(name);
                            Snackbar.make(coordinatorLayout, R.string.filter_set_saved,
                                    Snackbar.LENGTH_SHORT).show();
                            dialog.dismiss();
                        }));
        dialog.show();
    }

    private void promptManageFilterSets() {
        List<String> names = getManageableFilterSetNames();
        if (names.isEmpty()) {
            Snackbar.make(coordinatorLayout, R.string.filter_set_manage_none,
                    Snackbar.LENGTH_SHORT).show();
            return;
        }
        String[] arr = names.toArray(new String[0]);
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.filter_set_manage_title)
                .setItems(arr, (dialog, which) -> showManageFilterSetActions(arr[which]))
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    @NonNull
    private List<String> getManageableFilterSetNames() {
        List<String> names = new ArrayList<>(FilterSetStore.getSetNames(requireContext()));
        names.removeIf(name -> FilterSetStore.isReservedSetName(name));
        Collections.sort(names, (left, right) -> FilterSetStore.canonicalSetName(left)
                .compareTo(FilterSetStore.canonicalSetName(right)));
        return names;
    }

    private void showManageFilterSetActions(@NonNull String name) {
        CharSequence[] actions = new CharSequence[]{
                getString(R.string.filter_set_rename),
                getString(R.string.filter_set_delete)
        };
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.filter_set_manage_actions_title, name))
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        promptRenameFilterSet(name);
                    } else {
                        confirmDeleteFilterSet(name);
                    }
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private void promptRenameFilterSet(@NonNull String name) {
        TextInputLayout layout = new TextInputLayout(requireContext());
        layout.setHint(getString(R.string.filter_set_name_hint));
        TextInputEditText editText = new TextInputEditText(requireContext());
        editText.setText(name);
        editText.setSelectAllOnFocus(true);
        layout.addView(editText);

        androidx.appcompat.app.AlertDialog dialog =
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle(R.string.filter_set_rename_title)
                        .setView(layout)
                        .setPositiveButton(R.string.filter_set_rename, null)
                        .setNegativeButton(R.string.button_cancel, null)
                        .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
                        .setOnClickListener(view -> {
                            String rawName = editText.getText() != null
                                    ? editText.getText().toString() : "";
                            Set<String> existingNames =
                                    new HashSet<>(FilterSetStore.getSetNames(requireContext()));
                            existingNames.remove(name);
                            FilterSetStore.SetNameValidation validation =
                                    FilterSetStore.validateSetName(rawName, existingNames);
                            int error = getFilterSetNameError(validation);
                            if (error != 0) {
                                layout.setError(getString(error));
                                return;
                            }
                            layout.setError(null);
                            String newName = FilterSetStore.normalizeActiveProfile(rawName);
                            if (FilterSetStore.renameSet(requireContext(), name, newName)) {
                                Snackbar.make(coordinatorLayout, R.string.filter_set_renamed,
                                        Snackbar.LENGTH_SHORT).show();
                            }
                            dialog.dismiss();
                        }));
        dialog.show();
    }

    private void confirmDeleteFilterSet(@NonNull String name) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.filter_set_delete_title)
                .setMessage(getString(R.string.filter_set_delete_message, name))
                .setPositiveButton(R.string.filter_set_delete, (dialog, which) -> {
                    if (FilterSetStore.deleteSet(requireContext(), name)) {
                        Snackbar.make(coordinatorLayout, R.string.filter_set_deleted,
                                Snackbar.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private int getFilterSetNameError(@NonNull FilterSetStore.SetNameValidation validation) {
        switch (validation) {
            case EMPTY:
                return R.string.filter_set_name_empty;
            case RESERVED:
                return R.string.filter_set_name_reserved;
            case DUPLICATE:
                return R.string.filter_set_name_duplicate;
            case OK:
            default:
                return 0;
        }
    }

    private void saveFilterSet(String name) {
        Set<String> enabledUrls = new HashSet<>();
        for (HostsSource s : lastSources) {
            if (s.getId() != HostsSource.USER_SOURCE_ID && s.isEnabled()) {
                enabledUrls.add(s.getUrl());
            }
        }
        if (FilterSetStore.PROFILE_CUSTOM.equals(name)) {
            FilterSetStore.saveCustomProfile(requireContext(), enabledUrls);
            return;
        }
        FilterSetStore.saveSet(requireContext(), name, enabledUrls);
        FilterSetStore.setActiveProfile(requireContext(), name);
    }

    private void promptApplyFilterSet() {
        Set<String> names = FilterSetStore.getSetNames(requireContext());
        if (names == null || names.isEmpty()) {
            Snackbar.make(coordinatorLayout, R.string.filter_set_none, Snackbar.LENGTH_SHORT).show();
            return;
        }
        String[] arr = names.toArray(new String[0]);
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.menu_apply_filter_set)
                .setItems(arr, (d, which) -> previewApplyFilterSet(arr[which]))
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private void previewApplyFilterSet(@NonNull String name) {
        android.content.Context appCtx = requireContext().getApplicationContext();
        if (!FilterSetStore.hasSet(appCtx, name) || !FilterSetStore.hasSetUrls(appCtx, name)) {
            Snackbar.make(coordinatorLayout, R.string.filter_set_missing, Snackbar.LENGTH_SHORT)
                    .show();
            return;
        }
        Set<String> targetUrls = FilterSetStore.getSetUrls(appCtx, name);
        FilterProfileDiff diff = FilterProfileDiff.resolve(
                getEnabledSourceUrls(), targetUrls, getAvailableSourceUrls());
        if (diff.isEmptyProfile()) {
            showEmptyFilterSetConfirmation(name, targetUrls, diff);
            return;
        }
        if (diff.isMissingOnlyProfile()) {
            showMissingOnlyFilterSetConfirmation(name, targetUrls, diff);
            return;
        }
        if (!diff.hasChanges() && diff.getMissingCount() == 0) {
            boolean active = name.equals(FilterSetStore.getActiveProfile(appCtx));
            showNoChangeFilterSetDialog(name, targetUrls, active);
            return;
        }
        showApplyFilterSetConfirmation(name, targetUrls, diff);
    }

    private void showEmptyFilterSetConfirmation(
            @NonNull String name, @NonNull Set<String> targetUrls,
            @NonNull FilterProfileDiff diff) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.filter_set_apply_empty_title)
                .setMessage(getString(R.string.filter_set_apply_empty_message,
                        diff.getDisableCount()))
                .setPositiveButton(R.string.checkbox_list_context_apply,
                        (dialog, which) -> applyFilterSet(name, targetUrls))
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private void showMissingOnlyFilterSetConfirmation(
            @NonNull String name, @NonNull Set<String> targetUrls,
            @NonNull FilterProfileDiff diff) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.filter_set_apply_missing_only_title)
                .setMessage(getString(R.string.filter_set_apply_missing_only_message,
                        diff.getDisableCount()))
                .setPositiveButton(R.string.checkbox_list_context_apply,
                        (dialog, which) -> applyFilterSet(name, targetUrls))
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private void showNoChangeFilterSetDialog(
            @NonNull String name, @NonNull Set<String> targetUrls, boolean active) {
        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle(R.string.filter_set_apply_no_change_title)
                        .setMessage(active
                                ? getString(R.string.filter_set_apply_no_change_active_message)
                                : getString(R.string.filter_set_apply_set_active_message, name));
        if (active) {
            builder.setPositiveButton(R.string.button_close, null);
        } else {
            builder.setPositiveButton(R.string.filter_set_apply_set_active_button,
                    (dialog, which) -> applyFilterSet(name, targetUrls))
                    .setNegativeButton(R.string.button_cancel, null);
        }
        builder.show();
    }

    private void showApplyFilterSetConfirmation(
            @NonNull String name, @NonNull Set<String> targetUrls,
            @NonNull FilterProfileDiff diff) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.filter_set_apply_preview_title, name))
                .setMessage(buildApplyFilterSetPreviewMessage(diff))
                .setPositiveButton(R.string.checkbox_list_context_apply,
                        (dialog, which) -> applyFilterSet(name, targetUrls))
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    @NonNull
    private String buildApplyFilterSetPreviewMessage(@NonNull FilterProfileDiff diff) {
        StringBuilder message = new StringBuilder(diff.hasChanges()
                ? getString(R.string.filter_set_apply_preview_message,
                        diff.getEnableCount(), diff.getDisableCount(),
                        diff.getKeepEnabledCount())
                : getString(R.string.filter_set_apply_preview_no_changes));
        if (diff.weakensProtection()) {
            message.append('\n').append(getString(
                    R.string.filter_set_apply_preview_disable_warning));
        }
        if (diff.getMissingCount() > 0) {
            message.append('\n').append(getString(
                    R.string.filter_set_apply_preview_missing, diff.getMissingCount()));
        }
        return message.toString();
    }

    @NonNull
    private Set<String> getEnabledSourceUrls() {
        Set<String> enabledUrls = new HashSet<>();
        for (HostsSource source : lastSources) {
            if (source.getId() != HostsSource.USER_SOURCE_ID && source.isEnabled() &&
                    source.getUrl() != null) {
                enabledUrls.add(source.getUrl());
            }
        }
        return enabledUrls;
    }

    @NonNull
    private Set<String> getAvailableSourceUrls() {
        Set<String> availableUrls = new HashSet<>();
        for (HostsSource source : lastSources) {
            if (source.getId() != HostsSource.USER_SOURCE_ID && source.getUrl() != null) {
                availableUrls.add(source.getUrl());
            }
        }
        return availableUrls;
    }

    private void applyFilterSet(@NonNull String name, @NonNull Set<String> enabledUrls) {
        // QA-27: capture Context and DAO on the main thread to avoid calling requireContext()
        // from a background thread if the fragment detaches while the lambda is queued.
        android.content.Context appCtx = requireContext().getApplicationContext();
        HostsSourceDao dao = AppDatabase.getInstance(appCtx).hostsSourceDao();

        Snackbar waitSnackbar = Snackbar.make(coordinatorLayout, R.string.notification_configuration_installing, Snackbar.LENGTH_INDEFINITE);
        waitSnackbar.show();

        AppExecutors.getInstance().diskIO().execute(() -> {
            dao.applySourceSelections(enabledUrls);
            FilterSetStore.setActiveProfile(appCtx, name);
            FilterSetUpdateService.enable(appCtx);
            AppExecutors.getInstance().mainThread().execute(() -> {
                waitSnackbar.dismiss();
                if (isAdded()) {
                    Snackbar.make(coordinatorLayout, R.string.filter_set_applied, Snackbar.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void promptScheduleFilterSet() {
        // Let user schedule current selection or any saved set.
        Set<String> names = new HashSet<>(FilterSetStore.getSetNames(requireContext()));
        List<String> options = new ArrayList<>();
        options.add(getString(R.string.filter_set_schedule_current_selection));
        options.addAll(names);
        String[] arr = options.toArray(new String[0]);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.menu_schedule_filter_set)
                .setItems(arr, (d, which) -> {
                    String choice = arr[which];
                    if (choice.equals(getString(R.string.filter_set_schedule_current_selection))) {
                        // Save current selection as a real set so it can be scheduled.
                        String name = createScheduledSelectionName();
                        saveFilterSet(name);
                        promptScheduleForOne(name);
                    } else {
                        promptScheduleForOne(choice);
                    }
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    @NonNull
    private String createScheduledSelectionName() {
        Set<String> names = FilterSetStore.getSetNames(requireContext());
        String baseName = getString(R.string.filter_set_schedule_current_selection_saved_name);
        String candidate = baseName;
        int suffix = 2;
        while (FilterSetStore.validateSetName(candidate, names)
                != FilterSetStore.SetNameValidation.OK) {
            candidate = baseName + " " + suffix;
            suffix++;
        }
        return candidate;
    }

    private void promptScheduleForOne(String name) {
        CharSequence[] scheduleOptions = new CharSequence[]{
                getString(R.string.filter_set_schedule_off),
                getString(R.string.filter_set_schedule_daily),
                getString(R.string.filter_set_schedule_weekly)
        };
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(name)
                .setItems(scheduleOptions, (d, which) -> {
                    if (which == 0) {
                        FilterSetStore.setSchedule(requireContext(), name, FilterSetStore.SCHEDULE_OFF);
                        FilterSetUpdateService.enable(requireContext());
                        showScheduleApplied(scheduleOptions[which]);
                        return;
                    }
                    if (which == 1) {
                        // Daily -> pick time
                        pickTime((hour, minute) -> {
                            FilterSetStore.setSchedule(requireContext(), name, FilterSetStore.SCHEDULE_DAILY, 1, hour, minute);
                            FilterSetUpdateService.enable(requireContext());
                            showScheduleApplied(scheduleOptions[which]);
                        });
                        return;
                    }
                    // Weekly -> pick day then time
                    pickDayOfWeek(dowIso -> pickTime((hour, minute) -> {
                        FilterSetStore.setSchedule(requireContext(), name, FilterSetStore.SCHEDULE_WEEKLY, dowIso, hour, minute);
                        FilterSetUpdateService.enable(requireContext());
                        showScheduleApplied(scheduleOptions[which]);
                    }));
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private void showScheduleApplied(@NonNull CharSequence scheduleLabel) {
        Snackbar.make(coordinatorLayout,
                getString(R.string.filter_set_schedule_applied, scheduleLabel),
                Snackbar.LENGTH_SHORT).show();
    }

    private interface TimePicked {
        void onPicked(int hour24, int minute);
    }

    private void pickTime(@NonNull TimePicked picked) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        int hour = c.get(java.util.Calendar.HOUR_OF_DAY);
        int minute = c.get(java.util.Calendar.MINUTE);
        new android.app.TimePickerDialog(
                requireContext(),
                (view, hourOfDay, minuteOfHour) -> picked.onPicked(hourOfDay, minuteOfHour),
                hour,
                minute,
                DateFormat.is24HourFormat(requireContext())
        ).show();
    }

    private interface DayPicked {
        void onPicked(int isoDay);
    }

    private void pickDayOfWeek(@NonNull DayPicked picked) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.filter_set_schedule_pick_day)
                .setItems(getWeekdayLabels(), (d, which) -> picked.onPicked(which + 1))
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    @NonNull
    private String[] getWeekdayLabels() {
        String[] labels = new String[DayOfWeek.values().length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = DayOfWeek.of(i + 1).getDisplayName(TextStyle.FULL, Locale.getDefault());
        }
        return labels;
    }

    /**
     * Shows a bottom sheet with source-creation choices.
     * Scheduling stays in the Sources toolbar and Manage schedules screen.
     */
    private void showAddSourceOptions() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View sheetView = getLayoutInflater().inflate(
                R.layout.hosts_add_options_sheet,
                (ViewGroup) requireView(),
                false
        );
        
        // Browse catalog option — opens Discover tab in HomeActivity (catalog is embedded there)
        View catalogOption = sheetView.findViewById(R.id.browseCatalogOption);
        catalogOption.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(requireContext(), HomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra(HomeActivity.EXTRA_NAV_DISCOVER, true);
            startActivity(intent);
        });
        
        // Add custom source option
        View customOption = sheetView.findViewById(R.id.addCustomOption);
        customOption.setOnClickListener(v -> {
            dialog.dismiss();
            startSourceEdition(null);
        });

        dialog.setContentView(sheetView);
        dialog.show();
    }

    @Override
    public void setEnabled(HostsSource source, boolean enabled) {
        FilterSetStore.markCustomProfile(requireContext());
        this.mViewModel.setSourceEnabled(source, enabled);
    }

    @Override
    public void edit(HostsSource source) {
        startSourceEdition(source);
    }

    @Override
    public void updateSource(HostsSource source) {
        runUpdateSources(source);
    }

    @Override
    public void updateAllSources() {
        runUpdateSources(null);
    }

    private void startSourceEdition(@Nullable HostsSource source) {
        Intent intent = new Intent(requireContext(), SourceEditActivity.class);
        if (source != null) {
            intent.putExtra(SOURCE_ID, source.getId());
        }
        startActivity(intent);
    }

    private void runCheckUpdates() {
        Snackbar info = Snackbar.make(coordinatorLayout, R.string.status_check, Snackbar.LENGTH_LONG);
        info.show();
        // QA-05: capture Context before background thread to avoid IllegalStateException on navigation.
        AdAwayApplication application = (AdAwayApplication) requireContext().getApplicationContext();
        AppExecutors.getInstance().networkIO().execute(() -> {
            SourceModel sourceModel = application.getSourceModel();
            try {
                sourceModel.checkForUpdate();
            } catch (HostErrorException ignored) {
                // Keep it simple: failures are handled elsewhere in the app
            }
        });
    }

    /**
     * Update sources (download + parse) then apply the adblocking config.
     * If {@code source} is null, updates all enabled sources.
     */
    private void runUpdateSources(@Nullable HostsSource source) {
        this.mViewModel.updateSources(source == null ? null : source.getId());
    }
}
