package org.adaway.ui.discover;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.databinding.FragmentDiscoverFilterlistsBinding;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.model.source.FilterListCompatibility;
import org.adaway.model.source.FilterListsDirectoryApi;
import org.adaway.model.source.FilterListsSourceMetadata;
import org.adaway.model.source.SourceUpdateService;
import org.adaway.ui.adblocking.ApplyConfigurationSnackbar;
import org.adaway.ui.source.SourceEditActivity;
import org.adaway.util.AppExecutors;
import org.adaway.ui.hosts.FilterListsSubscribeAllWorker;

import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Fragment for browsing and subscribing to FilterLists.com directory.
 *
 * Ported from FilterListsImportActivity. Phase 2 adds tag chip + language spinner filtering.
 */
public class DiscoverFilterListsFragment extends Fragment {

    private FragmentDiscoverFilterlistsBinding binding;

    private static final String PREFS = "filterlists_cache";
    private static final String KEY_LISTS_JSON = "listsJson";
    private static final String KEY_SYNTAXES_JSON = "syntaxesJson";
    private static final String KEY_CACHED_AT = "cachedAt";
    private static final String KEY_TAGS_JSON = "tagsJson";
    private static final String KEY_LANGUAGES_JSON = "languagesJson";
    private static final String KEY_TAGS_CACHED_AT = "tagsCachedAt";
    private static final String KEY_URL_PREFIX = "listUrl_";
    private static final String KEY_SUBSCRIBE_ALL_STOPPING = "subscribeAllStopping";
    private static final long CACHE_TTL_MS = 24L * 60L * 60L * 1000L; // 24h
    private static final int MAX_LAST_RUN_DIALOG_ROWS = 80;

    private final List<FilterListsDirectoryApi.ListSummary> all = new ArrayList<>();
    private final List<FilterListsDirectoryApi.ListSummary> filtered = new ArrayList<>();
    private Map<Integer, String> syntaxNames;
    private final Map<Integer, String> resolvedUrlCache = new HashMap<>();
    private final Map<Integer, String> existingFilterListUrlsById = new HashMap<>();
    private final Set<String> existingUrls = new HashSet<>();
    private final Set<Integer> selectedListIds = new HashSet<>();
    private HostsSourceDao hostsSourceDao;

    // Tag/language/compat filter state
    private int selectedTagId = 0;
    private int selectedLanguageId = 0;
    private boolean mCompatibleOnly = false;
    private boolean mSubscribedOnly = false;
    private List<FilterListsDirectoryApi.Language> loadedLanguages = new ArrayList<>();

    private Adapter adapter;

    private Integer currentWorkingId = null;
    private String currentWorkingName = null;
    private int currentDone = 0;
    private int currentTotal = 0;

    private int monotonicLastDone = -1;
    private int monotonicLastTotal = -1;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean sourcesUiRefreshScheduled = false;
    private final Runnable sourcesUiRefresh = () -> {
        sourcesUiRefreshScheduled = false;
        if (this.binding == null) return;
        notifyFilterListRowsChanged();
    };

    private long lastAdapterRefreshMs = 0L;
    private boolean directoryLoading = false;
    private boolean directoryLoadFailed = false;
    private boolean bulkOperationRunning = false;

    private Snackbar activeSnackbar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        this.binding = FragmentDiscoverFilterlistsBinding.inflate(inflater, container, false);
        return this.binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        hostsSourceDao = AppDatabase.getInstance(requireContext()).hostsSourceDao();

        binding.filterlistsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new Adapter();
        binding.filterlistsRecyclerView.setAdapter(adapter);

        binding.filterlistsSearchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        binding.filterlistsSubscribeVisibleButton.setOnClickListener(
                v -> confirmSubscribeAll(false));
        binding.filterlistsRemoveVisibleButton.setOnClickListener(
                v -> confirmUnsubscribeAll(false));
        binding.filterlistsSubscribeAllButton.setOnClickListener(
                v -> confirmSubscribeAll(true));
        binding.filterlistsRemoveAllButton.setOnClickListener(
                v -> confirmUnsubscribeAll(true));
        binding.filterlistsCancelBulkButton.setOnClickListener(v -> cancelSubscribeAll());
        binding.filterlistsStateRetryButton.setOnClickListener(v -> load());
        binding.filterlistsReviewLastRunButton.setOnClickListener(v -> showLastRunReviewDialog());
        binding.filterlistsRetryLastRunButton.setOnClickListener(v -> retryLastRunNoUrlLists());
        binding.filterlistsReviewUnsupportedButton.setOnClickListener(
                v -> reviewLastRunUnsupportedLists());
        refreshLastRunReviewAction(true);

        // Observe live DB changes to update subscribed state
        hostsSourceDao.loadAll().observe(getViewLifecycleOwner(), sources -> {
            if (this.binding == null) return;
            final List<HostsSource> snapshot = sources != null ? new ArrayList<>(sources) : null;
            AppExecutors.getInstance().diskIO().execute(() -> {
                Set<String> urls = new HashSet<>();
                Map<Integer, String> filterListUrls = new HashMap<>();
                if (snapshot != null) {
                    for (HostsSource s : snapshot) {
                        urls.add(s.getUrl());
                        indexExistingSource(s, filterListUrls);
                    }
                }
                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (this.binding == null) return;
                    existingUrls.clear();
                    existingUrls.addAll(urls);
                    synchronized (existingFilterListUrlsById) {
                        existingFilterListUrlsById.clear();
                        existingFilterListUrlsById.putAll(filterListUrls);
                    }
                    if (mSubscribedOnly) {
                        filter();
                    } else {
                        refreshBulkActionsState();
                        requestAdapterRefreshThrottled(1000);
                    }
                });
            });
        });

        // Observe background subscribe-all job
        WorkManager.getInstance(requireContext())
                .getWorkInfosForUniqueWorkLiveData(FilterListsSubscribeAllWorker.UNIQUE_WORK_NAME)
                .observe(getViewLifecycleOwner(), infos -> {
                    if (this.binding == null) return;
                    WorkInfo info = pickBestWorkInfo(infos);
                    boolean running = info != null && (info.getState() == WorkInfo.State.RUNNING
                            || info.getState() == WorkInfo.State.ENQUEUED);
                    boolean stopping = running && isSubscribeAllStopping();
                    bulkOperationRunning = running;

                    if (running && info != null) {
                        refreshLastRunReviewAction(false);
                        int done = info.getProgress().getInt(FilterListsSubscribeAllWorker.PROGRESS_DONE, 0);
                        int total = info.getProgress().getInt(FilterListsSubscribeAllWorker.PROGRESS_TOTAL, 0);
                        int currentId = info.getProgress()
                                .getInt(FilterListsSubscribeAllWorker.PROGRESS_CURRENT_ID, -1);
                        String currentName = info.getProgress()
                                .getString(FilterListsSubscribeAllWorker.PROGRESS_CURRENT_NAME);

                        if (total != monotonicLastTotal) {
                            monotonicLastTotal = total;
                            monotonicLastDone = -1;
                        }
                        if (done < monotonicLastDone) {
                            return;
                        }
                        monotonicLastDone = done;
                        currentDone = done;
                        currentTotal = total;
                        currentWorkingId = currentId >= 0 ? currentId : null;
                        currentWorkingName = currentName;

                        binding.filterlistsSubscribeAllStatus.setVisibility(View.VISIBLE);
                        String line;
                        if (stopping) {
                            line = getString(R.string.filterlists_subscribe_all_stopping);
                        } else if (total <= 0) {
                            line = getString(R.string.filterlists_status_preparing);
                        } else {
                            int percent = (int) Math.floor(done * 100.0 / total);
                            line = getString(R.string.filterlists_progress_count_percent,
                                    done, total, percent);
                        }
                        if (currentName != null && !currentName.isEmpty()) {
                            line = getString(R.string.filterlists_progress_with_source,
                                    line, currentName);
                        }
                        binding.filterlistsSubscribeAllStatus.setText(line);
                    } else {
                        setSubscribeAllStopping(false);
                        monotonicLastDone = -1;
                        monotonicLastTotal = -1;
                        currentDone = 0;
                        currentTotal = 0;
                        currentWorkingId = null;
                        currentWorkingName = null;
                        String finalSummary = formatSubscribeAllResult(info);
                        if (finalSummary != null) {
                            binding.filterlistsSubscribeAllStatus.setVisibility(View.VISIBLE);
                            binding.filterlistsSubscribeAllStatus.setText(finalSummary);
                        } else {
                            binding.filterlistsSubscribeAllStatus.setVisibility(View.GONE);
                        }
                        refreshBulkActionsState();
                        refreshLastRunReviewAction(true);
                    }

                    binding.filterlistsCancelBulkButton.setVisibility(running ? View.VISIBLE : View.GONE);
                    binding.filterlistsCancelBulkButton.setEnabled(!stopping);
                    refreshBulkActionsState();
                    requestAdapterRefreshThrottled(running ? 1000 : 0);
                });

        binding.filterlistsCompatibleOnlySwitch.setOnCheckedChangeListener((btn, checked) -> {
            mCompatibleOnly = checked;
            filter();
        });
        binding.filterlistsShowSubscribedSwitch.setOnCheckedChangeListener((btn, checked) -> {
            mSubscribedOnly = checked;
            filter();
        });

        load();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (activeSnackbar != null) {
            activeSnackbar.dismiss();
            activeSnackbar = null;
        }
        this.binding = null;
    }

    private void requestAdapterRefreshThrottled(long minIntervalMs) {
        long now = android.os.SystemClock.elapsedRealtime();
        long waitMs = Math.max(0L, (lastAdapterRefreshMs + minIntervalMs) - now);
        mainHandler.removeCallbacks(sourcesUiRefresh);
        sourcesUiRefreshScheduled = true;
        mainHandler.postDelayed(() -> {
            lastAdapterRefreshMs = android.os.SystemClock.elapsedRealtime();
            sourcesUiRefresh.run();
        }, waitMs);
    }

    private void load() {
        if (binding == null) return;
        directoryLoading = true;
        directoryLoadFailed = false;
        binding.filterlistsProgress.setVisibility(View.VISIBLE);
        refreshBulkActionsState();
        updateFilterListsState();

        // Capture application context on main thread — safe to use from any thread thereafter.
        final Context appContext = requireContext().getApplicationContext();

        AppExecutors.getInstance().networkIO().execute(() -> {
            try {
                AdAwayApplication app = (AdAwayApplication) appContext;
                FilterListsDirectoryApi api = new FilterListsDirectoryApi(app.getSourceModel().getHttpClientForUi());

                // Load existing sources
                List<HostsSource> existing = hostsSourceDao.getAll();
                Set<String> existingUrlsLocal = new HashSet<>();
                Map<Integer, String> existingFilterListUrlsLocal = new HashMap<>();
                for (HostsSource s : existing) {
                    existingUrlsLocal.add(s.getUrl());
                    indexExistingSource(s, existingFilterListUrlsLocal);
                }

                SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                String cachedListsJson = prefs.getString(KEY_LISTS_JSON, null);
                String cachedSyntaxesJson = prefs.getString(KEY_SYNTAXES_JSON, null);
                String cachedTagsJson = prefs.getString(KEY_TAGS_JSON, null);
                String cachedLanguagesJson = prefs.getString(KEY_LANGUAGES_JSON, null);
                long cachedAt = prefs.getLong(KEY_CACHED_AT, 0L);
                long tagsCachedAt = prefs.getLong(KEY_TAGS_CACHED_AT, 0L);
                long now = System.currentTimeMillis();
                boolean listsCacheFresh = cachedListsJson != null && cachedSyntaxesJson != null
                        && (now - cachedAt) < CACHE_TTL_MS;
                boolean tagsCacheFresh = cachedTagsJson != null && cachedLanguagesJson != null
                        && (now - tagsCachedAt) < CACHE_TTL_MS;

                boolean cachedDirectoryLoaded = false;
                // Show cached lists immediately if available
                if (cachedListsJson != null && cachedSyntaxesJson != null) {
                    try {
                        Map<Integer, String> cachedSyntaxNames = FilterListsDirectoryApi
                                .parseSyntaxNamesJson(cachedSyntaxesJson);
                        List<FilterListsDirectoryApi.ListSummary> cachedLists = FilterListsDirectoryApi
                                .parseListsJson(cachedListsJson);
                        List<FilterListsDirectoryApi.Tag> cachedTags = cachedTagsJson != null
                                ? FilterListsDirectoryApi.parseTagsJson(cachedTagsJson)
                                : new ArrayList<>();
                        List<FilterListsDirectoryApi.Language> cachedLanguages = cachedLanguagesJson != null
                                ? FilterListsDirectoryApi.parseLanguagesJson(cachedLanguagesJson)
                                : new ArrayList<>();
                        cachedDirectoryLoaded = true;
                        AppExecutors.getInstance().mainThread().execute(() -> {
                            if (this.binding == null) return;
                            this.syntaxNames = cachedSyntaxNames;
                            this.existingUrls.clear();
                            this.existingUrls.addAll(existingUrlsLocal);
                            synchronized (existingFilterListUrlsById) {
                                this.existingFilterListUrlsById.clear();
                                this.existingFilterListUrlsById.putAll(existingFilterListUrlsLocal);
                            }
                            all.clear();
                            all.addAll(cachedLists);
                            populateTagChips(cachedTags);
                            populateLanguageSpinner(cachedLanguages);
                            filter();
                            binding.filterlistsProgress.setVisibility(View.GONE);
                            refreshBulkActionsState();
                            updateFilterListsState();
                        });
                    } catch (IOException ignored) {
                        // fall through to network
                    }
                }

                if (cachedDirectoryLoaded && listsCacheFresh && tagsCacheFresh) {
                    AppExecutors.getInstance().mainThread().execute(() -> {
                        if (this.binding == null) return;
                        directoryLoading = false;
                        directoryLoadFailed = false;
                        binding.filterlistsProgress.setVisibility(View.GONE);
                        refreshBulkActionsState();
                        updateFilterListsState();
                    });
                    return;
                }

                // Refresh from network
                String listsJson = api.getListsJson();
                String syntaxesJson = api.getSyntaxesJson();
                String tagsJson = api.getTagsJson();
                String languagesJson = api.getLanguagesJson();

                Map<Integer, String> syntaxNamesNet = FilterListsDirectoryApi.parseSyntaxNamesJson(syntaxesJson);
                List<FilterListsDirectoryApi.ListSummary> lists = FilterListsDirectoryApi.parseListsJson(listsJson);
                List<FilterListsDirectoryApi.Tag> tags = FilterListsDirectoryApi.parseTagsJson(tagsJson);
                List<FilterListsDirectoryApi.Language> languages = FilterListsDirectoryApi.parseLanguagesJson(languagesJson);

                prefs.edit()
                        .putString(KEY_LISTS_JSON, listsJson)
                        .putString(KEY_SYNTAXES_JSON, syntaxesJson)
                        .putLong(KEY_CACHED_AT, now)
                        .putString(KEY_TAGS_JSON, tagsJson)
                        .putString(KEY_LANGUAGES_JSON, languagesJson)
                        .putLong(KEY_TAGS_CACHED_AT, now)
                        .apply();

                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (this.binding == null) return;
                    this.syntaxNames = syntaxNamesNet;
                    this.existingUrls.clear();
                    this.existingUrls.addAll(existingUrlsLocal);
                    synchronized (existingFilterListUrlsById) {
                        this.existingFilterListUrlsById.clear();
                        this.existingFilterListUrlsById.putAll(existingFilterListUrlsLocal);
                    }
                    all.clear();
                    all.addAll(lists);
                    populateTagChips(tags);
                    populateLanguageSpinner(languages);
                    filter();
                    directoryLoading = false;
                    directoryLoadFailed = false;
                    binding.filterlistsProgress.setVisibility(View.GONE);
                    refreshBulkActionsState();
                    updateFilterListsState();
                });
            } catch (IOException e) {
                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (this.binding == null) return;
                    directoryLoading = false;
                    directoryLoadFailed = true;
                    binding.filterlistsProgress.setVisibility(View.GONE);
                    refreshBulkActionsState();
                    updateFilterListsState();
                    showSnackbar(getString(R.string.filterlists_load_failed));
                });
            }
        });
    }

    private void populateTagChips(@NonNull List<FilterListsDirectoryApi.Tag> tags) {
        if (binding == null) return;
        ChipGroup chipGroup = binding.tagChipGroup;
        chipGroup.removeAllViews();

        // "All" chip
        Chip allChip = new Chip(requireContext());
        allChip.setText(R.string.filterlists_tag_all);
        allChip.setCheckable(true);
        allChip.setChecked(selectedTagId == 0);
        allChip.setTag(0);
        allChip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                selectedTagId = 0;
                filter();
            }
        });
        chipGroup.addView(allChip);

        for (FilterListsDirectoryApi.Tag tag : tags) {
            Chip chip = new Chip(requireContext());
            chip.setText(tag.name);
            chip.setCheckable(true);
            chip.setChecked(selectedTagId == tag.id);
            chip.setTag(tag.id);
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedTagId = tag.id;
                    filter();
                }
            });
            chipGroup.addView(chip);
        }
    }

    private void populateLanguageSpinner(@NonNull List<FilterListsDirectoryApi.Language> languages) {
        if (binding == null) return;
        loadedLanguages = new ArrayList<>(languages);

        List<String> items = new ArrayList<>();
        items.add(getString(R.string.filterlists_language_all));
        for (FilterListsDirectoryApi.Language lang : languages) {
            items.add(lang.name);
        }

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, items);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.languageSpinner.setAdapter(spinnerAdapter);

        binding.languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    selectedLanguageId = 0;
                } else {
                    selectedLanguageId = loadedLanguages.get(position - 1).id;
                }
                filter();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedLanguageId = 0;
            }
        });
    }

    private void filter() {
        if (binding == null) return;
        String q = getCurrentSearchQuery();
        List<FilterListsDirectoryApi.ListSummary> nextFiltered = new ArrayList<>();
        for (FilterListsDirectoryApi.ListSummary s : all) {
            if (!q.isEmpty()) {
                String name = s.name != null ? s.name.toLowerCase(Locale.ROOT) : "";
                String desc = s.description != null ? s.description.toLowerCase(Locale.ROOT) : "";
                if (!name.contains(q) && !desc.contains(q)) continue;
            }
            if (selectedTagId != 0 && !hasId(s.tagIds, selectedTagId)) continue;
            if (selectedLanguageId != 0 && !hasId(s.languageIds, selectedLanguageId)) continue;
            if (mCompatibleOnly && !isAdAwayCompatible(s.syntaxIds)) continue;
            if (mSubscribedOnly && !isSummarySubscribed(s)) continue;
            nextFiltered.add(s);
        }
        pruneSelectedListIdsToVisible(nextFiltered);
        updateFilteredRows(nextFiltered);
        refreshBulkActionsState();
        updateFilterListsState();
    }

    @NonNull
    private String getCurrentSearchQuery() {
        if (binding == null || binding.filterlistsSearchEditText.getText() == null) {
            return "";
        }
        return binding.filterlistsSearchEditText.getText().toString()
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private static boolean hasId(@Nullable int[] ids, int want) {
        if (ids == null) return false;
        for (int id : ids) if (id == want) return true;
        return false;
    }

    private void updateFilteredRows(@NonNull List<FilterListsDirectoryApi.ListSummary> nextFiltered) {
        List<FilterListsDirectoryApi.ListSummary> previousFiltered = new ArrayList<>(filtered);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return previousFiltered.size();
            }

            @Override
            public int getNewListSize() {
                return nextFiltered.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return previousFiltered.get(oldItemPosition).id
                        == nextFiltered.get(newItemPosition).id;
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                FilterListsDirectoryApi.ListSummary oldItem = previousFiltered.get(oldItemPosition);
                FilterListsDirectoryApi.ListSummary newItem = nextFiltered.get(newItemPosition);
                return Objects.equals(oldItem.name, newItem.name)
                        && Objects.equals(oldItem.description, newItem.description)
                        && Arrays.equals(oldItem.syntaxIds, newItem.syntaxIds)
                        && Arrays.equals(oldItem.tagIds, newItem.tagIds)
                        && Arrays.equals(oldItem.languageIds, newItem.languageIds);
            }
        });

        filtered.clear();
        filtered.addAll(nextFiltered);
        if (adapter != null) {
            diff.dispatchUpdatesTo(adapter);
        }
    }

    private void pruneSelectedListIdsToVisible(
            @NonNull List<FilterListsDirectoryApi.ListSummary> visibleRows) {
        Set<Integer> visibleIds = new HashSet<>();
        for (FilterListsDirectoryApi.ListSummary summary : visibleRows) {
            visibleIds.add(summary.id);
        }
        selectedListIds.retainAll(visibleIds);
    }

    private void setFilterListSelected(
            @NonNull FilterListsDirectoryApi.ListSummary summary,
            boolean selected) {
        if (selected) {
            selectedListIds.add(summary.id);
        } else {
            selectedListIds.remove(summary.id);
        }
        refreshBulkActionsState();
        notifyFilterListRowChanged(summary.id);
    }

    private void notifyFilterListRowsChanged() {
        if (adapter == null) return;
        int count = adapter.getItemCount();
        if (count > 0) {
            adapter.notifyItemRangeChanged(0, count);
        }
    }

    private void notifyFilterListRowChanged(int listId) {
        if (adapter == null) return;
        for (int i = 0; i < filtered.size(); i++) {
            if (filtered.get(i).id == listId) {
                adapter.notifyItemChanged(i);
                return;
            }
        }
    }

    private void updateFilterListsState() {
        if (binding == null) return;

        int state = FilterListsUiState.resolve(
                directoryLoading,
                directoryLoadFailed,
                all.size(),
                filtered.size(),
                hasActiveFilters());
        boolean showState = state != FilterListsUiState.EMPTY_HIDDEN;

        binding.filterlistsStateContainer.setVisibility(showState ? View.VISIBLE : View.GONE);
        binding.filterlistsRecyclerView.setVisibility(showState ? View.GONE : View.VISIBLE);
        binding.filterlistsStateRetryButton.setVisibility(
                state == FilterListsUiState.LOAD_FAILED ? View.VISIBLE : View.GONE);

        switch (state) {
            case FilterListsUiState.LOADING:
                binding.filterlistsStateTitle.setText(R.string.filterlists_state_loading_title);
                binding.filterlistsStateMessage.setText(R.string.filterlists_state_loading_message);
                break;
            case FilterListsUiState.LOAD_FAILED:
                binding.filterlistsStateTitle.setText(R.string.filterlists_state_load_failed_title);
                binding.filterlistsStateMessage.setText(R.string.filterlists_state_load_failed_message);
                break;
            case FilterListsUiState.NO_LISTS:
                binding.filterlistsStateTitle.setText(R.string.filterlists_state_no_lists_title);
                binding.filterlistsStateMessage.setText(R.string.filterlists_state_no_lists_message);
                break;
            case FilterListsUiState.NO_MATCHES:
                binding.filterlistsStateTitle.setText(R.string.filterlists_state_no_matches_title);
                binding.filterlistsStateMessage.setText(R.string.filterlists_state_no_matches_message);
                break;
            default:
                break;
        }
    }

    private boolean hasActiveFilters() {
        if (binding == null) return false;
        CharSequence query = binding.filterlistsSearchEditText.getText();
        boolean hasQuery = query != null && query.toString().trim().length() > 0;
        return hasQuery || selectedTagId != 0 || selectedLanguageId != 0
                || mCompatibleOnly || mSubscribedOnly;
    }

    private int countCompatible(@NonNull List<FilterListsDirectoryApi.ListSummary> summaries) {
        int count = 0;
        for (FilterListsDirectoryApi.ListSummary summary : summaries) {
            if (isAdAwayCompatible(summary.syntaxIds)) {
                count++;
            }
        }
        return count;
    }

    /** Returns true if the list uses a syntax that maps directly to DNS/root-hosts blocking. */
    private static boolean isAdAwayCompatible(@Nullable int[] syntaxIds) {
        return FilterListsSubscriptionState.isCompatible(syntaxIds);
    }

    private boolean isSummarySubscribed(@NonNull FilterListsDirectoryApi.ListSummary summary) {
        String url = getCachedUrlForId(summary.id);
        return url != null && existingUrls.contains(url);
    }

    @Nullable
    private String getCachedUrlForId(int id) {
        return getCachedUrlForId(requireContext(), id);
    }

    @Nullable
    private String getCachedUrlForId(@NonNull Context context, int id) {
        synchronized (resolvedUrlCache) {
            String url = resolvedUrlCache.get(id);
            if (url != null) return normalizeCachedUrl(url);
        }
        String sourceUrl = getSourceUrlForFilterListId(id);
        if (sourceUrl != null) {
            return sourceUrl;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String url;
        url = prefs.getString(KEY_URL_PREFIX + id, null);
        if (url != null) {
            url = normalizeCachedUrl(url);
            synchronized (resolvedUrlCache) {
                if (url != null) {
                    resolvedUrlCache.put(id, url);
                } else {
                    resolvedUrlCache.remove(id);
                }
            }
        }
        return url;
    }

    @Nullable
    private static String normalizeCachedUrl(@Nullable String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Nullable
    private String getSourceUrlForFilterListId(int id) {
        synchronized (existingFilterListUrlsById) {
            return existingFilterListUrlsById.get(id);
        }
    }

    private static void indexExistingSource(@NonNull HostsSource source,
            @NonNull Map<Integer, String> filterListUrls) {
        Integer filterListId = source.getFilterListId();
        if (filterListId == null) {
            return;
        }
        String selectedUrl = normalizeCachedUrl(source.getFilterListSelectedUrl());
        String sourceUrl = normalizeCachedUrl(source.getUrl());
        String url = selectedUrl != null ? selectedUrl : sourceUrl;
        if (url != null) {
            filterListUrls.put(filterListId, url);
        }
    }

    private void cacheResolvedUrl(@NonNull Context context, int id, @Nullable String url) {
        if (url == null) {
            return;
        }
        synchronized (resolvedUrlCache) {
            resolvedUrlCache.put(id, url);
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_URL_PREFIX + id, url)
                .apply();
    }

    private void onPick(FilterListsDirectoryApi.ListSummary summary) {
        if (binding == null) return;
        if (!isAdAwayCompatible(summary.syntaxIds)) {
            showUnsupportedReviewDialog(summary);
            return;
        }
        binding.filterlistsProgress.setVisibility(View.VISIBLE);
        final Context appContext = requireContext().getApplicationContext();
        AppExecutors.getInstance().networkIO().execute(() -> {
            try {
                AdAwayApplication app = (AdAwayApplication) appContext;
                FilterListsDirectoryApi api = new FilterListsDirectoryApi(app.getSourceModel().getHttpClientForUi());
                FilterListsDirectoryApi.ListDetails details = api.getListDetails(summary.id);
                String url = details.pickBestDownloadUrl();
                if (url != null) {
                    cacheResolvedUrl(appContext, summary.id, url);
                }
                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (this.binding == null) return;
                    binding.filterlistsProgress.setVisibility(View.GONE);
                    if (url == null) {
                        showSnackbar(getString(R.string.filterlists_no_direct_hosts_url));
                        return;
                    }
                    openSourceEditForFilterList(summary, details.name, url);
                });
            } catch (IOException e) {
                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (this.binding == null) return;
                    binding.filterlistsProgress.setVisibility(View.GONE);
                    showSnackbar(getString(R.string.filterlists_resolve_url_failed));
                });
            }
        });
    }

    private void setSubscribed(FilterListsDirectoryApi.ListSummary summary, boolean subscribed) {
        if (!subscribed) {
            confirmUnsubscribe(summary);
            return;
        }
        updateSubscription(summary, true);
    }

    private void confirmUnsubscribe(FilterListsDirectoryApi.ListSummary summary) {
        if (binding == null) return;
        notifyFilterListRowChanged(summary.id);
        String name = summary.name != null ? summary.name : getString(R.string.filter_sources_title);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.filterlists_confirm_unsubscribe_title)
                .setMessage(getString(R.string.filterlists_confirm_unsubscribe_message, name))
                .setNegativeButton(R.string.button_cancel, null)
                .setPositiveButton(R.string.filterlists_confirm_unsubscribe_action,
                        (dialog, which) -> updateSubscription(summary, false))
                .show();
    }

    private void updateSubscription(FilterListsDirectoryApi.ListSummary summary, boolean subscribed) {
        if (binding == null) return;
        binding.filterlistsProgress.setVisibility(View.VISIBLE);
        final Context appContext = requireContext().getApplicationContext();
        AppExecutors.getInstance().networkIO().execute(() -> {
            try {
                String url = getCachedUrlForId(appContext, summary.id);
                if (url == null) {
                    AdAwayApplication app = (AdAwayApplication) appContext;
                    FilterListsDirectoryApi api = new FilterListsDirectoryApi(
                            app.getSourceModel().getHttpClientForUi());
                    FilterListsDirectoryApi.ListDetails details = api.getListDetails(summary.id);
                    url = details.pickBestDownloadUrl();
                    if (url != null) {
                        cacheResolvedUrl(appContext, summary.id, url);
                    }
                }
                if (url == null) {
                    String msg = appContext.getString(R.string.filterlists_no_direct_download_url);
                    AppExecutors.getInstance().mainThread().execute(() -> {
                        if (this.binding == null) return;
                        binding.filterlistsProgress.setVisibility(View.GONE);
                        showSnackbar(msg);
                        notifyFilterListRowChanged(summary.id);
                    });
                    return;
                }

                if (subscribed) {
                    HostsSource src = new HostsSource();
                    src.setLabel(summary.name != null ? summary.name : url);
                    src.setUrl(url);
                    src.setEnabled(true);
                    src.setAllowEnabled(false);
                    src.setRedirectEnabled(false);
                    FilterListsSourceMetadata.apply(src, summary.id, summary.name,
                            summary.syntaxIds, summary.tagIds, summary.languageIds, url);
                    hostsSourceDao.insert(src);
                    SourceUpdateService.enqueueUpdateNow(appContext);
                } else {
                    HostsSource existing = hostsSourceDao.getByUrl(url).orElse(null);
                    if (existing != null) {
                        hostsSourceDao.delete(existing);
                        ((AdAwayApplication) appContext).getSourceModel().syncHostEntries();
                    }
                }

                final String finalUrl = url;
                runOnMainThreadIfAdded(() -> {
                    // Mutations to existingUrls must happen on the main thread so the
                    // adapter (which reads existingUrls on main thread) sees a consistent view.
                    if (subscribed) {
                        existingUrls.add(finalUrl);
                        synchronized (existingFilterListUrlsById) {
                            existingFilterListUrlsById.put(summary.id, finalUrl);
                        }
                    } else {
                        existingUrls.remove(finalUrl);
                        synchronized (existingFilterListUrlsById) {
                            existingFilterListUrlsById.remove(summary.id);
                        }
                    }
                    binding.filterlistsProgress.setVisibility(View.GONE);
                    if (mSubscribedOnly) {
                        filter();
                    } else {
                        notifyFilterListRowChanged(summary.id);
                    }
                    if (subscribed) {
                        showSnackbar(getString(R.string.filterlists_subscribe_done));
                    } else {
                        if (activeSnackbar != null) activeSnackbar.dismiss();
                        new ApplyConfigurationSnackbar(binding.getRoot(), false, false)
                                .notifyUpdateAvailable();
                    }
                });
            } catch (IOException e) {
                runOnMainThreadIfAdded(() -> {
                    binding.filterlistsProgress.setVisibility(View.GONE);
                    showSnackbar(getString(R.string.filterlists_update_subscription_failed));
                    notifyFilterListRowChanged(summary.id);
                });
            }
        });
    }

    private void confirmSubscribeAll(boolean allDirectory) {
        if (binding == null) return;
        List<FilterListsDirectoryApi.ListSummary> scope =
                allDirectory ? all : getSelectedSummariesForBulkScope();
        if (!allDirectory && scope.isEmpty()) {
            showSnackbar(getString(R.string.filterlists_no_selected_lists));
            return;
        }
        int safeCount = countCompatible(scope);
        if (safeCount == 0) {
            if (!allDirectory && showUnsupportedSelectionReview(scope)) {
                return;
            }
            showSnackbar(getString(allDirectory
                    ? R.string.filterlists_no_dns_safe_lists_in_scope
                    : R.string.filterlists_no_dns_safe_selected_lists));
            return;
        }
        int messageId = allDirectory
                ? R.string.filterlists_confirm_subscribe_all_message
                : R.string.filterlists_confirm_subscribe_filtered_message;

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.filterlists_confirm_subscribe_all_title)
                .setMessage(getString(messageId, safeCount))
                .setNegativeButton(R.string.button_cancel, null)
                .setPositiveButton(R.string.filterlists_confirm_subscribe_all_action,
                        (dialog, which) -> subscribeAll(allDirectory))
                .show();
    }

    private void subscribeAll(boolean allDirectory) {
        if (binding == null) return;
        int[] selectedIds = allDirectory ? null : getSelectedIdsForBulkScope();
        if (!allDirectory && selectedIds.length == 0) {
            showSnackbar(getString(R.string.filterlists_no_selected_lists));
            return;
        }
        Context appContext = requireContext().getApplicationContext();
        FilterListsSubscribeAllWorker.prepareForNewRun(appContext);
        binding.filterlistsSubscribeAllStatus.setVisibility(View.VISIBLE);
        binding.filterlistsSubscribeAllStatus.setText(R.string.filterlists_status_preparing);
        setSubscribeAllStopping(false);
        bulkOperationRunning = true;
        refreshBulkActionsState();
        currentDone = 0;
        currentTotal = 0;
        currentWorkingId = null;
        currentWorkingName = getString(R.string.filterlists_status_preparing);
        notifyFilterListRowsChanged();

        try {
            binding.filterlistsSearchEditText.clearFocus();
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(binding.filterlistsSearchEditText.getWindowToken(), 0);
            }
        } catch (Exception ignored) {
        }

        Data inputData = FilterListsSubscribeAllWorker.buildScopeInput(
                allDirectory ? null : getCurrentSearchQuery(),
                allDirectory ? 0 : selectedTagId,
                allDirectory ? 0 : selectedLanguageId,
                !allDirectory && mCompatibleOnly,
                selectedIds);
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(FilterListsSubscribeAllWorker.class)
                .setInputData(inputData)
                .build();
        WorkManager wm = WorkManager.getInstance(appContext);
        wm.enqueueUniqueWork(FilterListsSubscribeAllWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request);

        showSnackbar(getString(R.string.filterlists_running_in_background));
    }

    private void confirmUnsubscribeAll(boolean allDirectory) {
        if (binding == null) return;
        List<FilterListsDirectoryApi.ListSummary> selectedScope =
                allDirectory ? new ArrayList<>() : getSelectedSummariesForBulkScope();
        if (!allDirectory && selectedScope.isEmpty()) {
            showSnackbar(getString(R.string.filterlists_no_selected_lists));
            return;
        }
        if (!allDirectory && countSubscribed(selectedScope) == 0) {
            showSnackbar(getString(R.string.filterlists_no_subscribed_selected_lists));
            return;
        }
        int titleId = !allDirectory
                ? R.string.filterlists_confirm_unsubscribe_filtered_title
                : R.string.filterlists_confirm_unsubscribe_all_title;
        int messageId = !allDirectory
                ? R.string.filterlists_confirm_unsubscribe_filtered_message
                : R.string.filterlists_confirm_unsubscribe_all_message;
        int actionId = !allDirectory
                ? R.string.filterlists_confirm_unsubscribe_filtered_action
                : R.string.filterlists_confirm_unsubscribe_all_action;
        new AlertDialog.Builder(requireContext())
                .setTitle(titleId)
                .setMessage(messageId)
                .setNegativeButton(R.string.button_cancel,
                        (dialog, which) -> refreshBulkActionsState())
                .setPositiveButton(actionId,
                        (dialog, which) -> unsubscribeAll(allDirectory))
                .setOnCancelListener(dialog -> refreshBulkActionsState())
                .show();
    }

    private void openExistingSource(
            @NonNull FilterListsDirectoryApi.ListSummary summary,
            @NonNull String url) {
        if (binding == null) return;
        AppExecutors.getInstance().diskIO().execute(() -> {
            HostsSource existing = hostsSourceDao.getByUrl(url).orElse(null);
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (this.binding == null) return;
                if (existing == null) {
                    existingUrls.remove(url);
                    refreshBulkActionsState();
                    notifyFilterListRowChanged(summary.id);
                    showSnackbar(getString(R.string.filterlists_not_subscribed));
                    return;
                }
                Intent intent = new Intent(requireContext(), SourceEditActivity.class);
                intent.putExtra(SourceEditActivity.SOURCE_ID, existing.getId());
                startActivity(intent);
            });
        });
    }

    private void openSourceEditForFilterList(FilterListsDirectoryApi.ListSummary summary,
            String label, String url) {
        Intent intent = new Intent(requireContext(), SourceEditActivity.class);
        intent.putExtra(SourceEditActivity.EXTRA_INITIAL_LABEL, label);
        intent.putExtra(SourceEditActivity.EXTRA_INITIAL_URL, url);
        intent.putExtra(SourceEditActivity.EXTRA_INITIAL_ALLOW, false);
        intent.putExtra(SourceEditActivity.EXTRA_INITIAL_REDIRECT, false);
        intent.putExtra(SourceEditActivity.EXTRA_FILTER_LIST_ID, summary.id);
        intent.putExtra(SourceEditActivity.EXTRA_FILTER_LIST_NAME, summary.name);
        intent.putExtra(SourceEditActivity.EXTRA_FILTER_LIST_SYNTAX_IDS, summary.syntaxIds);
        intent.putExtra(SourceEditActivity.EXTRA_FILTER_LIST_TAG_IDS, summary.tagIds);
        intent.putExtra(SourceEditActivity.EXTRA_FILTER_LIST_LANGUAGE_IDS, summary.languageIds);
        intent.putExtra(SourceEditActivity.EXTRA_FILTER_LIST_SELECTED_URL, url);
        startActivity(intent);
    }

    private void unsubscribeAll(boolean allDirectory) {
        if (binding == null) return;
        bulkOperationRunning = true;
        refreshBulkActionsState();
        final Context appContext = requireContext().getApplicationContext();
        final List<FilterListsDirectoryApi.ListSummary> listSnapshot =
                allDirectory ? new ArrayList<>() : getSelectedSummariesForBulkScope();
        if (!allDirectory && listSnapshot.isEmpty()) {
            bulkOperationRunning = false;
            refreshBulkActionsState();
            showSnackbar(getString(R.string.filterlists_no_selected_lists));
            return;
        }
        final Map<Integer, String> urlSnapshot;
        synchronized (resolvedUrlCache) {
            urlSnapshot = new HashMap<>(resolvedUrlCache);
        }
        AppExecutors.getInstance().diskIO().execute(() -> {
            SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            Set<String> filterListUrls = new HashSet<>();
            if (allDirectory) {
                for (HostsSource source : hostsSourceDao.getAll()) {
                    if (isFilterListsSource(source)) {
                        String url = normalizeCachedUrl(source.getUrl());
                        if (url != null) {
                            filterListUrls.add(url);
                        }
                    }
                }
            } else {
                for (FilterListsDirectoryApi.ListSummary summary : listSnapshot) {
                    String url = getSourceUrlForFilterListId(summary.id);
                    if (url == null) {
                        url = urlSnapshot.get(summary.id);
                    }
                    if (url == null) {
                        url = prefs.getString(KEY_URL_PREFIX + summary.id, null);
                    }
                    url = normalizeCachedUrl(url);
                    if (url != null) {
                        filterListUrls.add(url);
                    }
                }
            }

            int removed = 0;
            for (String url : filterListUrls) {
                HostsSource existing = hostsSourceDao.getByUrl(url).orElse(null);
                if (existing != null) {
                    hostsSourceDao.delete(existing);
                    removed++;
                }
            }
            if (removed > 0) {
                ((AdAwayApplication) appContext).getSourceModel().syncHostEntries();
            }

            int removedCount = removed;
            runOnMainThreadIfAdded(() -> {
                existingUrls.removeAll(filterListUrls);
                bulkOperationRunning = false;
                refreshBulkActionsState();
                if (mSubscribedOnly) {
                    filter();
                } else {
                    notifyFilterListRowsChanged();
                }
                if (removedCount > 0) {
                    if (activeSnackbar != null) activeSnackbar.dismiss();
                    new ApplyConfigurationSnackbar(binding.getRoot(), false, false)
                            .notifyUpdateAvailable();
                } else {
                    showSnackbar(getString(R.string.filterlists_unsubscribe_all_done, removedCount));
                }
            });
        });
    }

    private static boolean isFilterListsSource(@NonNull HostsSource source) {
        return source.getFilterListId() != null
                || normalizeCachedUrl(source.getFilterListSelectedUrl()) != null;
    }

    private void cancelSubscribeAll() {
        if (binding == null) return;
        Context appContext = requireContext().getApplicationContext();
        FilterListsSubscribeAllWorker.requestCancel(appContext);
        WorkManager.getInstance(appContext)
                .cancelUniqueWork(FilterListsSubscribeAllWorker.UNIQUE_WORK_NAME);
        setSubscribeAllStopping(true);
        bulkOperationRunning = true;
        refreshBulkActionsState();
        binding.filterlistsCancelBulkButton.setEnabled(false);
        binding.filterlistsSubscribeAllStatus.setVisibility(View.VISIBLE);
        binding.filterlistsSubscribeAllStatus.setText(R.string.filterlists_subscribe_all_stopping);
        showSnackbar(getString(R.string.filterlists_subscribe_all_stopping));
    }

    @NonNull
    private List<FilterListsDirectoryApi.ListSummary> getSelectedSummariesForBulkScope() {
        List<FilterListsDirectoryApi.ListSummary> selected = new ArrayList<>();
        for (FilterListsDirectoryApi.ListSummary summary : filtered) {
            if (selectedListIds.contains(summary.id)) {
                selected.add(summary);
            }
        }
        return selected;
    }

    private int countSubscribed(@NonNull List<FilterListsDirectoryApi.ListSummary> summaries) {
        int count = 0;
        for (FilterListsDirectoryApi.ListSummary summary : summaries) {
            if (isSummarySubscribed(summary)) {
                count++;
            }
        }
        return count;
    }

    @NonNull
    private int[] getSelectedIdsForBulkScope() {
        List<FilterListsDirectoryApi.ListSummary> selected = getSelectedSummariesForBulkScope();
        int[] ids = new int[selected.size()];
        for (int i = 0; i < selected.size(); i++) {
            ids[i] = selected.get(i).id;
        }
        return ids;
    }

    private void refreshBulkActionsState() {
        if (binding == null) return;
        List<FilterListsDirectoryApi.ListSummary> selected = getSelectedSummariesForBulkScope();
        int allState = FilterListsSubscriptionState.resolve(
                all, this::getCachedUrlForId, existingUrls);
        int compatibleAll = countCompatible(all);
        boolean directoryBlocking = directoryLoading && all.isEmpty();
        boolean busy = directoryBlocking || bulkOperationRunning;
        binding.filterlistsBulkActionsRow.setVisibility(
                all.isEmpty() && filtered.isEmpty() && !bulkOperationRunning
                        ? View.GONE : View.VISIBLE);
        binding.filterlistsSelectedActionsRow.setVisibility(
                filtered.isEmpty() && !bulkOperationRunning ? View.GONE : View.VISIBLE);
        binding.filterlistsSubscribeVisibleButton.setEnabled(
                !busy && !selected.isEmpty());
        binding.filterlistsRemoveVisibleButton.setEnabled(
                !busy && !selected.isEmpty());
        binding.filterlistsSubscribeAllButton.setEnabled(
                !busy && compatibleAll > 0 && allState != FilterListsSubscriptionState.ALL);
        binding.filterlistsRemoveAllButton.setEnabled(
                !busy && hasExistingFilterListSubscriptions());
    }

    private boolean hasExistingFilterListSubscriptions() {
        synchronized (existingFilterListUrlsById) {
            return !existingFilterListUrlsById.isEmpty();
        }
    }

    private void showSnackbar(String message) {
        if (binding == null) return;
        activeSnackbar = Snackbar.make(binding.filterlistsRecyclerView, message, Snackbar.LENGTH_LONG);
        activeSnackbar.show();
    }

    private void refreshLastRunReviewAction(boolean allowed) {
        if (binding == null) return;
        SharedPreferences prefs = requireContext().getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String outcomes = prefs.getString(FilterListsSubscribeAllWorker.KEY_LAST_RUN_OUTCOMES, "");
        boolean hasReview = prefs.getInt(
                FilterListsSubscribeAllWorker.KEY_LAST_RUN_REVIEW_COUNT, 0) > 0
                && outcomes != null && !outcomes.isEmpty();
        boolean hasRetry = outcomes != null && parseRetryableNoUrlIds(outcomes).length > 0;
        boolean hasUnsupported = outcomes != null && parseUnsupportedIds(outcomes).length > 0;
        boolean showActions = allowed && (hasReview || hasRetry || hasUnsupported);
        binding.filterlistsLastRunActions.setVisibility(showActions ? View.VISIBLE : View.GONE);
        binding.filterlistsReviewLastRunButton.setVisibility(
                hasReview ? View.VISIBLE : View.GONE);
        binding.filterlistsRetryLastRunButton.setVisibility(
                hasRetry ? View.VISIBLE : View.GONE);
        binding.filterlistsReviewUnsupportedButton.setVisibility(
                hasUnsupported ? View.VISIBLE : View.GONE);
    }

    private void showLastRunReviewDialog() {
        if (binding == null) return;
        SharedPreferences prefs = requireContext().getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String outcomes = prefs.getString(FilterListsSubscribeAllWorker.KEY_LAST_RUN_OUTCOMES, "");
        if (outcomes == null || outcomes.isEmpty()) {
            showSnackbar(getString(R.string.filterlists_last_run_review_empty));
            refreshLastRunReviewAction(true);
            return;
        }

        String message = formatLastRunReviewMessage(
                outcomes,
                prefs.getInt(FilterListsSubscribeAllWorker.KEY_LAST_RUN_OUTCOME_COUNT, 0),
                prefs.getBoolean(FilterListsSubscribeAllWorker.KEY_LAST_RUN_CANCELLED, false));
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.filterlists_last_run_review_title)
                .setMessage(message)
                .setPositiveButton(R.string.button_close, null)
                .show();
    }

    private void retryLastRunNoUrlLists() {
        if (binding == null) return;
        Context appContext = requireContext().getApplicationContext();
        FilterListsSubscribeAllWorker.prepareForNewRun(appContext);
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String outcomes = prefs.getString(FilterListsSubscribeAllWorker.KEY_LAST_RUN_OUTCOMES, "");
        int[] retryIds = parseRetryableNoUrlIds(outcomes != null ? outcomes : "");
        if (retryIds.length == 0) {
            showSnackbar(getString(R.string.filterlists_retry_last_run_empty));
            refreshLastRunReviewAction(true);
            return;
        }

        SharedPreferences.Editor editor = prefs.edit();
        synchronized (resolvedUrlCache) {
            for (int id : retryIds) {
                editor.remove(KEY_URL_PREFIX + id);
                resolvedUrlCache.remove(id);
            }
        }
        editor.apply();

        binding.filterlistsSubscribeAllStatus.setVisibility(View.VISIBLE);
        binding.filterlistsSubscribeAllStatus.setText(R.string.filterlists_status_preparing);
        setSubscribeAllStopping(false);
        bulkOperationRunning = true;
        refreshBulkActionsState();
        refreshLastRunReviewAction(false);

        Data inputData = FilterListsSubscribeAllWorker.buildScopeInput(null, 0, 0, false,
                retryIds);
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(FilterListsSubscribeAllWorker.class)
                .setInputData(inputData)
                .build();
        WorkManager.getInstance(requireContext()).enqueueUniqueWork(
                FilterListsSubscribeAllWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE,
                request);
        showSnackbar(getString(R.string.filterlists_retry_last_run_started, retryIds.length));
    }

    private void reviewLastRunUnsupportedLists() {
        if (binding == null) return;
        Context appContext = requireContext().getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String outcomes = prefs.getString(FilterListsSubscribeAllWorker.KEY_LAST_RUN_OUTCOMES, "");
        int[] unsupportedIds = parseUnsupportedIds(outcomes != null ? outcomes : "");
        if (unsupportedIds.length == 0) {
            showSnackbar(getString(R.string.filterlists_review_unsupported_empty));
            refreshLastRunReviewAction(true);
            return;
        }

        List<FilterListsDirectoryApi.ListSummary> summaries = findSummariesByIds(unsupportedIds);
        if (summaries.isEmpty()) {
            showSnackbar(getString(R.string.filterlists_review_unsupported_not_loaded));
            return;
        }

        showUnsupportedChoicesDialog(summaries);
    }

    private boolean showUnsupportedSelectionReview(
            @NonNull List<FilterListsDirectoryApi.ListSummary> summaries) {
        List<FilterListsDirectoryApi.ListSummary> unsupported = new ArrayList<>();
        for (FilterListsDirectoryApi.ListSummary summary : summaries) {
            if (!isAdAwayCompatible(summary.syntaxIds)) {
                unsupported.add(summary);
            }
        }
        if (unsupported.isEmpty()) {
            return false;
        }
        showUnsupportedChoicesDialog(unsupported);
        return true;
    }

    private void showUnsupportedChoicesDialog(
            @NonNull List<FilterListsDirectoryApi.ListSummary> summaries) {
        if (summaries.size() == 1) {
            showUnsupportedReviewDialog(summaries.get(0));
            return;
        }

        int shown = Math.min(summaries.size(), MAX_LAST_RUN_DIALOG_ROWS);
        CharSequence[] labels = new CharSequence[shown];
        for (int i = 0; i < shown; i++) {
            FilterListsDirectoryApi.ListSummary summary = summaries.get(i);
            labels[i] = summary.name != null && !summary.name.isEmpty()
                    ? summary.name : "List " + summary.id;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.filterlists_review_unsupported_choices_title)
                .setItems(labels, (dialog, which) -> showUnsupportedReviewDialog(summaries.get(which)))
                .setNegativeButton(R.string.button_close, null)
                .show();
    }

    @NonNull
    private List<FilterListsDirectoryApi.ListSummary> findSummariesByIds(@NonNull int[] ids) {
        List<FilterListsDirectoryApi.ListSummary> summaries = new ArrayList<>();
        for (int id : ids) {
            for (FilterListsDirectoryApi.ListSummary summary : all) {
                if (summary.id == id) {
                    summaries.add(summary);
                    break;
                }
            }
        }
        return summaries;
    }

    private void showUnsupportedReviewDialog(FilterListsDirectoryApi.ListSummary summary) {
        if (binding == null) return;
        binding.filterlistsProgress.setVisibility(View.VISIBLE);
        final Context appContext = requireContext().getApplicationContext();
        AppExecutors.getInstance().networkIO().execute(() -> {
            try {
                AdAwayApplication app = (AdAwayApplication) appContext;
                FilterListsDirectoryApi api = new FilterListsDirectoryApi(
                        app.getSourceModel().getHttpClientForUi());
                FilterListsDirectoryApi.ListDetails details = api.getListDetails(summary.id);
                String url = details.pickBestDownloadUrl();
                if (url != null) {
                    cacheResolvedUrl(appContext, summary.id, url);
                }
                runOnMainThreadIfAdded(() -> {
                    binding.filterlistsProgress.setVisibility(View.GONE);
                    String urlMessage = url == null
                            ? getString(R.string.filterlists_review_unsupported_no_url)
                            : getString(R.string.filterlists_review_unsupported_url, url);
                    String message = getString(
                            R.string.filterlists_review_unsupported_message,
                            FilterListCompatibility.describe(summary.syntaxIds),
                            FilterListCompatibility.capabilitySummary(summary.syntaxIds),
                            urlMessage);
                    String label = details.name != null && !details.name.isEmpty()
                            ? details.name : summary.name;
                    AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                            .setTitle(R.string.filterlists_review_unsupported_title)
                            .setMessage(message)
                            .setNegativeButton(R.string.button_close, null);
                    if (url != null) {
                        builder.setPositiveButton(R.string.filterlists_add_manually,
                                (dialog, which) -> openSourceEditForFilterList(summary, label, url));
                    }
                    builder.show();
                });
            } catch (IOException e) {
                runOnMainThreadIfAdded(() -> {
                    binding.filterlistsProgress.setVisibility(View.GONE);
                    showSnackbar(getString(R.string.filterlists_resolve_url_failed));
                });
            }
        });
    }

    static int[] parseRetryableNoUrlIds(@NonNull String outcomes) {
        return parseOutcomeIds(outcomes, "SKIPPED_NO_URL");
    }

    static int[] parseUnsupportedIds(@NonNull String outcomes) {
        return parseOutcomeIds(outcomes, "SKIPPED_UNSUPPORTED");
    }

    @NonNull
    private static int[] parseOutcomeIds(@NonNull String outcomes, @NonNull String expectedOutcome) {
        List<Integer> ids = new ArrayList<>();
        String[] lines = outcomes.split("\\n");
        for (String line : lines) {
            String[] parts = line.split("\\t", -1);
            if (parts.length < 2 || !expectedOutcome.equals(parts[0])) {
                continue;
            }
            try {
                int id = Integer.parseInt(parts[1]);
                if (id <= 0 || ids.contains(id)) {
                    continue;
                }
                ids.add(id);
            } catch (NumberFormatException ignored) {
            }
        }
        int[] result = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            result[i] = ids.get(i);
        }
        return result;
    }

    @NonNull
    static String formatLastRunReviewMessage(@NonNull String outcomes, int outcomeCount,
            boolean cancelled) {
        String[] lines = outcomes.split("\\n");
        StringBuilder builder = new StringBuilder();
        if (cancelled) {
            builder.append("Cancelled").append('\n').append('\n');
        }
        int shown = Math.min(lines.length, MAX_LAST_RUN_DIALOG_ROWS);
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(formatOutcomeLine(lines[i]));
        }
        int hidden = Math.max(0, outcomeCount - shown);
        if (hidden > 0) {
            builder.append('\n').append('+').append(hidden).append(" more");
        }
        return builder.toString();
    }

    @NonNull
    private static String formatOutcomeLine(@NonNull String line) {
        String[] parts = line.split("\\t", -1);
        String outcome = parts.length > 0 ? parts[0] : "";
        String id = parts.length > 1 ? parts[1] : "";
        String name = parts.length > 2 ? parts[2] : "";
        String url = parts.length > 3 ? parts[3] : "";

        String label = !name.isEmpty() ? name : (!id.isEmpty() && !"0".equals(id)
                ? "List " + id : "Unknown list");
        String status;
        switch (outcome) {
            case "SUBSCRIBED":
                status = "Added";
                break;
            case "ALREADY":
                status = "Already";
                break;
            case "SKIPPED_NO_URL":
                status = "No URL";
                break;
            case "SKIPPED_UNSUPPORTED":
                status = "Unsupported";
                break;
            default:
                status = outcome.isEmpty() ? "Unknown" : outcome;
                break;
        }

        if (url.isEmpty()) {
            return status + " - " + label;
        }
        return status + " - " + label + "\n" + url;
    }

    private boolean isSubscribeAllStopping() {
        Context appContext = requireContext().getApplicationContext();
        return appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_SUBSCRIBE_ALL_STOPPING, false);
    }

    private void setSubscribeAllStopping(boolean stopping) {
        Context appContext = requireContext().getApplicationContext();
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SUBSCRIBE_ALL_STOPPING, stopping)
                .apply();
    }

    @Nullable
    private static WorkInfo pickBestWorkInfo(@Nullable List<WorkInfo> infos) {
        if (infos == null || infos.isEmpty()) return null;
        for (WorkInfo i : infos) {
            if (i.getState() == WorkInfo.State.RUNNING) return i;
        }
        for (WorkInfo i : infos) {
            if (i.getState() == WorkInfo.State.ENQUEUED) return i;
        }
        return infos.get(infos.size() - 1);
    }

    @Nullable
    private String formatSubscribeAllResult(@Nullable WorkInfo info) {
        if (info == null || !info.getState().isFinished()) {
            return null;
        }

        Data output = info.getOutputData();
        int subscribed = output.getInt(FilterListsSubscribeAllWorker.OUTPUT_SUBSCRIBED, 0);
        int already = output.getInt(FilterListsSubscribeAllWorker.OUTPUT_ALREADY, 0);
        int skippedNoUrl = output.getInt(FilterListsSubscribeAllWorker.OUTPUT_SKIPPED_NO_URL, 0);
        int skippedUnsupported = output.getInt(
                FilterListsSubscribeAllWorker.OUTPUT_SKIPPED_UNSUPPORTED, 0);
        boolean cancelled = info.getState() == WorkInfo.State.CANCELLED
                || output.getBoolean(FilterListsSubscribeAllWorker.OUTPUT_CANCELLED, false);

        if (subscribed == 0 && already == 0 && skippedNoUrl == 0 && skippedUnsupported == 0) {
            return cancelled ? getString(R.string.filterlists_subscribe_all_cancelled) : null;
        }

        int messageId = cancelled
                ? R.string.filterlists_subscribe_all_cancelled_summary
                : R.string.filterlists_subscribe_all_done_summary;
        String summary = getString(messageId, subscribed, already, skippedNoUrl,
                skippedUnsupported);
        String reviewPreview = output.getString(FilterListsSubscribeAllWorker.OUTPUT_REVIEW_PREVIEW);
        int reviewCount = output.getInt(FilterListsSubscribeAllWorker.OUTPUT_REVIEW_COUNT, 0);
        if (reviewCount <= 0 || reviewPreview == null || reviewPreview.isEmpty()) {
            return summary;
        }
        return getString(R.string.filterlists_subscribe_all_review_summary, summary,
                reviewPreview);
    }

    private String formatSyntax(int[] ids) {
        if (ids == null || ids.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) sb.append(", ");
            String name = syntaxNames != null ? syntaxNames.get(ids[i]) : null;
            sb.append(name != null ? name : getString(R.string.filterlists_syntax_fallback, ids[i]));
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------------------------------
    // Adapter
    // -----------------------------------------------------------------------------------------

    private class Adapter extends RecyclerView.Adapter<RowVH> {
        @NonNull
        @Override
        public RowVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.filterlists_import_item, parent, false);
            return new RowVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RowVH holder, int position) {
            FilterListsDirectoryApi.ListSummary s = filtered.get(position);
            String cachedUrl = getCachedUrlForId(s.id);
            boolean isSubscribed = cachedUrl != null && existingUrls.contains(cachedUrl);
            boolean compatible = isAdAwayCompatible(s.syntaxIds);
            String capabilitySummary = FilterListCompatibility.capabilitySummary(s.syntaxIds);
            boolean isSelected = selectedListIds.contains(s.id);

            holder.selectionCheckBox.setOnCheckedChangeListener(null);
            holder.selectionCheckBox.setEnabled(!bulkOperationRunning);
            holder.selectionCheckBox.setChecked(isSelected);
            holder.selectionCheckBox.setContentDescription(
                    getString(R.string.filterlists_source_select_description, s.name));
            holder.selectionCheckBox.setOnCheckedChangeListener(
                    (buttonView, checked) -> setFilterListSelected(s, checked));

            holder.switchView.setOnCheckedChangeListener(null);
            holder.switchView.setEnabled(true);
            holder.switchView.setChecked(isSubscribed);
            holder.switchView.setOnCheckedChangeListener((buttonView, checked) -> setSubscribed(s, checked));

            holder.name.setText(s.name);
            holder.syntax.setText(formatSyntax(s.syntaxIds));

            boolean isCurrent = currentWorkingId != null && currentWorkingId == s.id;
            String rowState;
            if (isCurrent) {
                holder.status.setVisibility(View.VISIBLE);
                holder.status.setText(R.string.filterlists_status_processing);
                rowState = getString(R.string.filterlists_status_processing);
            } else if (isSubscribed) {
                holder.status.setVisibility(View.VISIBLE);
                holder.status.setText(R.string.filterlists_subscribe_done);
                rowState = getString(R.string.filterlists_subscribe_done);
            } else if (!compatible) {
                holder.status.setVisibility(View.VISIBLE);
                rowState = FilterListCompatibility.rowSummary(s.syntaxIds);
                holder.status.setText(rowState);
            } else {
                holder.status.setVisibility(View.GONE);
                rowState = FilterListCompatibility.rowSummary(s.syntaxIds);
            }

            holder.desc.setText(formatDescriptionWithCapabilities(s.description, s.syntaxIds));
            holder.switchView.setContentDescription(
                    getString(R.string.filterlists_source_toggle_description, s.name));
            holder.itemView.setContentDescription(
                    getString(R.string.filterlists_source_row_description, s.name,
                            rowState + ". " + capabilitySummary));
            holder.itemView.setOnClickListener(v -> {
                if (isSubscribed && cachedUrl != null) {
                    openExistingSource(s, cachedUrl);
                } else {
                    onPick(s);
                }
            });
        }

        @Override
        public int getItemCount() {
            return filtered.size();
        }
    }

    static String formatDescriptionWithCapabilities(@Nullable String description,
            @Nullable int[] syntaxIds) {
        String capabilities = FilterListCompatibility.capabilitySummary(syntaxIds);
        if (description == null || description.trim().isEmpty()) {
            return capabilities;
        }
        return description.trim() + "\n" + capabilities;
    }

    static class RowVH extends RecyclerView.ViewHolder {
        final MaterialCheckBox selectionCheckBox;
        final MaterialSwitch switchView;
        final TextView name;
        final TextView syntax;
        final TextView status;
        final TextView desc;

        RowVH(@NonNull View itemView) {
            super(itemView);
            selectionCheckBox = itemView.findViewById(R.id.filterlistsItemSelectionCheckBox);
            switchView = itemView.findViewById(R.id.filterlistsItemSwitch);
            name = itemView.findViewById(R.id.filterlistsItemName);
            syntax = itemView.findViewById(R.id.filterlistsItemSyntax);
            status = itemView.findViewById(R.id.filterlistsItemStatus);
            desc = itemView.findViewById(R.id.filterlistsItemDesc);
        }
    }

    private void runOnMainThreadIfAdded(@NonNull Runnable action) {
        AppExecutors.getInstance().mainThread().execute(() -> {
            if (!isAdded() || this.binding == null) return;
            action.run();
        });
    }
}
