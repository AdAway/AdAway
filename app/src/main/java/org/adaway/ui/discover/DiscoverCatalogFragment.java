package org.adaway.ui.discover;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.databinding.FragmentDiscoverCatalogBinding;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.model.source.FilterListCatalog;
import org.adaway.model.source.SourceUpdateService;
import org.adaway.ui.adblocking.ApplyConfigurationSnackbar;
import org.adaway.ui.hosts.FilterSetStore;
import org.adaway.ui.source.SourceEditActivity;
import org.adaway.util.AppExecutors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Fragment for browsing and adding filter lists from the curated catalog.
 *
 * Ported from FilterCatalogActivity. Embedded into DiscoverFragment as the first sub-tab.
 */
public class DiscoverCatalogFragment extends Fragment {

    private FragmentDiscoverCatalogBinding binding;
    private HostsSourceDao hostsSourceDao;

    private CatalogAdapter adapter;
    private Set<String> existingUrls = new HashSet<>();
    private Set<FilterListCatalog.CatalogEntry> selectedEntries = new HashSet<>();

    private List<FilterListCatalog.CatalogEntry> allEntries;
    private List<FilterListCatalog.CatalogEntry> filteredEntries;
    private String currentSearchQuery = "";
    @Nullable
    private String selectedPresetProfile;
    private Set<String> selectedProfileUrls = new HashSet<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        this.binding = FragmentDiscoverCatalogBinding.inflate(inflater, container, false);
        return this.binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        hostsSourceDao = AppDatabase.getInstance(requireContext()).hostsSourceDao();

        binding.catalogRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        binding.addSelectedButton.setOnClickListener(v -> addSelectedSources());

        binding.catalogSelectAllSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (this.binding == null) return;
            if (filteredEntries == null) return;
            markCustomSelection();
            selectedEntries.clear();
            if (isChecked) {
                for (FilterListCatalog.CatalogEntry entry : filteredEntries) {
                    if (!existingUrls.contains(entry.url)) {
                        selectedEntries.add(entry);
                    }
                }
            }
            notifyCatalogRowsChanged();
            updateAddButton();
            binding.chipCustom.setChecked(true);
        });

        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString();
                filterEntries();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        binding.chipSafe.setOnClickListener(v -> applyPreset("safe"));
        binding.chipBalanced.setOnClickListener(v -> applyPreset("balanced"));
        binding.chipAggressive.setOnClickListener(v -> applyPreset("aggressive"));
        binding.chipCustom.setOnClickListener(v -> binding.chipCustom.setChecked(true));

        loadData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        this.binding = null;
    }

    private void loadData() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            List<HostsSource> existing = hostsSourceDao.getAll();
            Set<String> fetchedUrls = new HashSet<>();
            for (HostsSource source : existing) {
                fetchedUrls.add(source.getUrl());
            }

            allEntries = FilterListCatalog.getAll();
            filteredEntries = new ArrayList<>(allEntries);

            runOnMainThreadIfAdded(() -> {
                // existingUrls mutations must happen on main thread — adapter reads it there.
                existingUrls.clear();
                existingUrls.addAll(fetchedUrls);
                adapter = new CatalogAdapter();
                binding.catalogRecyclerView.setAdapter(adapter);
                updateSubtitle();
            });
        });
    }

    private void filterEntries() {
        if (allEntries == null) return;

        List<FilterListCatalog.CatalogEntry> nextFilteredEntries = new ArrayList<>();
        String query = currentSearchQuery.toLowerCase(Locale.ROOT).trim();

        for (FilterListCatalog.CatalogEntry entry : allEntries) {
            if (query.isEmpty()) {
                nextFilteredEntries.add(entry);
            } else {
                boolean matchesLabel = entry.label.toLowerCase(Locale.ROOT).contains(query);
                boolean matchesUrl = entry.url.toLowerCase(Locale.ROOT).contains(query);
                boolean matchesCategory = entry.category.name().toLowerCase(Locale.ROOT).contains(query);
                if (matchesLabel || matchesUrl || matchesCategory) {
                    nextFilteredEntries.add(entry);
                }
            }
        }

        updateFilteredEntries(nextFilteredEntries);
        updateSubtitle();

        if (binding != null && binding.catalogSelectAllSwitch.isChecked()) {
            binding.catalogSelectAllSwitch.setChecked(false);
        }
    }

    private void applyPreset(String preset) {
        selectedEntries.clear();
        selectedPresetProfile = FilterSetStore.normalizePresetProfile(preset);
        selectedProfileUrls.clear();

        List<FilterListCatalog.CatalogEntry> presetEntries;
        switch (preset) {
            case "safe":
                presetEntries = FilterListCatalog.getDefaults();
                break;
            case "balanced":
                presetEntries = FilterListCatalog.getBalancedPreset();
                break;
            case "aggressive":
                presetEntries = FilterListCatalog.getAggressivePreset();
                break;
            default:
                presetEntries = new ArrayList<>();
        }

        for (FilterListCatalog.CatalogEntry entry : presetEntries) {
            selectedProfileUrls.add(entry.url);
            if (!existingUrls.contains(entry.url)) {
                selectedEntries.add(entry);
            }
        }

        notifyCatalogRowsChanged();
        updateAddButton();

        String message;
        switch (preset) {
            case "safe":
                message = getString(R.string.filter_preset_safe_desc);
                break;
            case "balanced":
                message = getString(R.string.filter_preset_balanced_desc);
                break;
            case "aggressive":
                message = getString(R.string.filter_preset_aggressive_desc);
                break;
            default:
                message = "";
        }

        if (!message.isEmpty() && binding != null) {
            Snackbar.make(binding.catalogRecyclerView, message, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void clearSelection() {
        selectedEntries.clear();
        markCustomSelection();
        notifyCatalogRowsChanged();
        updateAddButton();
    }

    private void markCustomSelection() {
        selectedPresetProfile = null;
        selectedProfileUrls.clear();
        if (binding != null) {
            binding.chipCustom.setChecked(true);
        }
    }

    private void updateFilteredEntries(@NonNull List<FilterListCatalog.CatalogEntry> nextEntries) {
        if (filteredEntries == null) {
            filteredEntries = new ArrayList<>(nextEntries);
            if (adapter != null && !nextEntries.isEmpty()) {
                adapter.notifyItemRangeInserted(0, nextEntries.size());
            }
            return;
        }

        List<FilterListCatalog.CatalogEntry> previousEntries = new ArrayList<>(filteredEntries);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return previousEntries.size();
            }

            @Override
            public int getNewListSize() {
                return nextEntries.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return Objects.equals(previousEntries.get(oldItemPosition).url,
                        nextEntries.get(newItemPosition).url);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                FilterListCatalog.CatalogEntry oldEntry = previousEntries.get(oldItemPosition);
                FilterListCatalog.CatalogEntry newEntry = nextEntries.get(newItemPosition);
                return Objects.equals(oldEntry.label, newEntry.label)
                        && Objects.equals(oldEntry.url, newEntry.url)
                        && oldEntry.category == newEntry.category;
            }
        });

        filteredEntries = new ArrayList<>(nextEntries);
        if (adapter != null) {
            diff.dispatchUpdatesTo(adapter);
        }
    }

    private void notifyCatalogRowsChanged() {
        if (adapter == null) return;
        int count = adapter.getItemCount();
        if (count > 0) {
            adapter.notifyItemRangeChanged(0, count);
        }
    }

    private void updateSubtitle() {
        if (binding == null) return;
        int total = allEntries != null ? allEntries.size() : 0;
        int showing = filteredEntries != null ? filteredEntries.size() : 0;

        if (currentSearchQuery.isEmpty()) {
            binding.catalogSubtitle.setText(
                    getResources().getQuantityString(R.plurals.filter_catalog_count, total, total));
        } else {
            binding.catalogSubtitle.setText(
                    getString(R.string.filter_catalog_showing_count, showing, total));
        }
    }

    private void updateAddButton() {
        if (binding == null) return;
        int count = selectedEntries.size();
        if (count > 0) {
            binding.addSelectedButton.setText(
                    getString(R.string.filter_catalog_add_selected_count, count));
            binding.addSelectedButton.setVisibility(View.VISIBLE);
        } else {
            binding.addSelectedButton.setVisibility(View.GONE);
        }
    }

    private void addSelectedSources() {
        if (selectedEntries.isEmpty() || binding == null) return;

        binding.addSelectedButton.setVisibility(View.GONE);

        // Capture URLs before clearing selectedEntries (called on main thread)
        final Set<String> selectedUrls = new HashSet<>();
        for (FilterListCatalog.CatalogEntry e : selectedEntries) {
            selectedUrls.add(e.url);
        }
        final String profile = selectedPresetProfile;
        final Set<String> profileUrls = profile != null
                ? new HashSet<>(selectedProfileUrls)
                : new HashSet<>(selectedUrls);
        final android.content.Context appContext = requireContext().getApplicationContext();

        AppExecutors.getInstance().diskIO().execute(() -> {
            int added = 0;
            List<String> addedUrls = new ArrayList<>();
            for (FilterListCatalog.CatalogEntry entry : FilterListCatalog.getAll()) {
                if (!selectedUrls.contains(entry.url)) continue;
                if (!existingUrls.contains(entry.url)) {
                    HostsSource source = entry.toHostsSource();
                    source.setEnabled(true);
                    hostsSourceDao.insert(source);
                    addedUrls.add(entry.url);
                    added++;
                }
            }
            // Persist applied preset to FilterSetStore so scheduled updates can track it.
            if (!profileUrls.isEmpty()) {
                if (profile != null) {
                    FilterSetStore.savePresetProfile(appContext, profile, profileUrls);
                } else {
                    FilterSetStore.saveCustomProfile(appContext, profileUrls);
                }
            }
            // Kick off an immediate download of the newly added sources.
            if (added > 0) {
                SourceUpdateService.enqueueUpdateNow(appContext);
            }

            final int finalAdded = added;
            final List<String> finalAddedUrls = addedUrls;
            runOnMainThreadIfAdded(() -> {
                // existingUrls mutations must happen on main thread — adapter reads it there.
                existingUrls.addAll(finalAddedUrls);
                selectedEntries.clear();
                selectedPresetProfile = null;
                selectedProfileUrls.clear();
                notifyCatalogRowsChanged();
                updateAddButton();
                String message = finalAdded > 0
                        ? getResources().getQuantityString(
                                R.plurals.filter_added_success_count, finalAdded, finalAdded)
                        : getString(R.string.filter_preset_already_subscribed);
                Snackbar.make(
                        binding.catalogRecyclerView,
                        message,
                        Snackbar.LENGTH_SHORT).show();
            });
        });
    }

    private void removeExistingSourceByUrl(String url) {
        if (binding == null || !isAdded()) return;
        final android.content.Context appContext = requireContext().getApplicationContext();
        AppExecutors.getInstance().diskIO().execute(() -> {
            HostsSource existing = hostsSourceDao.getByUrl(url).orElse(null);
            if (existing != null) {
                hostsSourceDao.delete(existing);
                ((AdAwayApplication) appContext).getSourceModel().syncHostEntries();
            }
            runOnMainThreadIfAdded(() -> {
                // existingUrls mutation must happen on main thread — adapter reads it there.
                existingUrls.remove(url);
                notifyCatalogRowsChanged();
                new ApplyConfigurationSnackbar(binding.getRoot(), false, false)
                        .notifyUpdateAvailable();
                updateSubtitle();
            });
        });
    }

    // -----------------------------------------------------------------------------------------
    // Adapter
    // -----------------------------------------------------------------------------------------

    private class CatalogAdapter extends RecyclerView.Adapter<CatalogAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.filter_catalog_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FilterListCatalog.CatalogEntry entry = filteredEntries.get(position);
            boolean alreadyAdded = existingUrls.contains(entry.url);
            boolean isSelected = selectedEntries.contains(entry);

            holder.name.setText(entry.label);
            holder.badge.setText(entry.category.getLabelResId());

            if (entry.category.mayBreakServices()) {
                holder.badge.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(
                                requireContext().getResources().getColor(R.color.blocked,
                                        requireContext().getTheme())));
                holder.badge.setTextColor(
                        requireContext().getResources().getColor(android.R.color.white,
                                requireContext().getTheme()));
            } else {
                holder.badge.setBackgroundTintList(null);
            }

            String urlPreview = entry.url;
            if (urlPreview.length() > 55) {
                urlPreview = urlPreview.substring(0, 52) + "...";
            }
            holder.description.setText(urlPreview);

            holder.alreadyAdded.setVisibility(alreadyAdded ? View.VISIBLE : View.GONE);
            holder.toggle.setVisibility(alreadyAdded ? View.GONE : View.VISIBLE);
            holder.toggle.setContentDescription(
                    getString(R.string.filter_catalog_toggle_description, entry.label));

            holder.toggle.setOnCheckedChangeListener(null);
            holder.toggle.setChecked(isSelected);

            holder.toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                markCustomSelection();
                if (isChecked) {
                    selectedEntries.add(entry);
                } else {
                    selectedEntries.remove(entry);
                    if (binding != null && binding.catalogSelectAllSwitch.isChecked()) {
                        binding.catalogSelectAllSwitch.setChecked(false);
                    }
                }
                updateAddButton();
                if (binding != null) binding.chipCustom.setChecked(true);
            });

            holder.card.setCheckable(!alreadyAdded);
            holder.card.setChecked(!alreadyAdded && isSelected);
            holder.card.setContentDescription(getString(alreadyAdded
                    ? R.string.filter_catalog_row_already_added
                    : isSelected
                            ? R.string.filter_catalog_row_selected
                            : R.string.filter_catalog_row_not_selected, entry.label));

            if (alreadyAdded) {
                holder.card.setAlpha(0.7f);
                holder.card.setOnClickListener(v -> {
                    AppExecutors.getInstance().diskIO().execute(() -> {
                        HostsSource existing = hostsSourceDao.getByUrl(entry.url).orElse(null);
                        if (existing == null) return;
                        runOnMainThreadIfAdded(() -> {
                            Intent intent = new Intent(requireContext(), SourceEditActivity.class);
                            intent.putExtra(SourceEditActivity.SOURCE_ID, existing.getId());
                            startActivity(intent);
                        });
                    });
                });
                holder.card.setOnLongClickListener(v -> {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.checkbox_list_context_delete)
                            .setMessage(entry.label)
                            .setPositiveButton(R.string.checkbox_list_context_delete,
                                    (d, which) -> removeExistingSourceByUrl(entry.url))
                            .setNegativeButton(R.string.button_cancel, null)
                            .show();
                    return true;
                });
            } else {
                holder.card.setAlpha(1.0f);
                holder.card.setOnClickListener(v -> {
                    markCustomSelection();
                    boolean newState = !selectedEntries.contains(entry);
                    if (newState) {
                        selectedEntries.add(entry);
                    } else {
                        selectedEntries.remove(entry);
                    }
                    holder.toggle.setChecked(newState);
                    holder.card.setChecked(newState);
                    holder.card.setContentDescription(getString(newState
                            ? R.string.filter_catalog_row_selected
                            : R.string.filter_catalog_row_not_selected, entry.label));
                    int adapterPosition = holder.getBindingAdapterPosition();
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        notifyItemChanged(adapterPosition);
                    }
                    updateAddButton();
                });
                holder.card.setOnLongClickListener(null);
            }
        }

        @Override
        public int getItemCount() {
            return filteredEntries != null ? filteredEntries.size() : 0;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final MaterialCardView card;
            final MaterialSwitch toggle;
            final TextView name;
            final TextView badge;
            final TextView description;
            final TextView alreadyAdded;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                card = itemView.findViewById(R.id.catalogItemCard);
                toggle = itemView.findViewById(R.id.catalogItemSwitch);
                name = itemView.findViewById(R.id.catalogItemName);
                badge = itemView.findViewById(R.id.catalogItemBadge);
                description = itemView.findViewById(R.id.catalogItemDescription);
                alreadyAdded = itemView.findViewById(R.id.catalogItemAlreadyAdded);
            }
        }
    }

    private void runOnMainThreadIfAdded(@NonNull Runnable action) {
        AppExecutors.getInstance().mainThread().execute(() -> {
            if (!isAdded() || this.binding == null) return;
            action.run();
        });
    }
}
