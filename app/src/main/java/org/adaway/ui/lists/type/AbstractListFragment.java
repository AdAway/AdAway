package org.adaway.ui.lists.type;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputEditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.LoadState;
import androidx.paging.PagingData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.adaway.R;
import org.adaway.db.entity.HostListItem;
import org.adaway.ui.lists.ListsViewCallback;
import org.adaway.ui.lists.ListsViewModel;
import org.adaway.util.Clipboard;

import kotlin.Unit;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;

/**
 * This class is a {@link Fragment} to display and manage lists of
 * {@link org.adaway.ui.lists.ListsActivity}.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public abstract class AbstractListFragment extends Fragment implements ListsViewCallback {
    /**
     * The view model (<code>null</code> if view is not created).
     */
    protected ListsViewModel mViewModel;
    /**
     * The current activity (<code>null</code> if view is not created).
     */
    protected FragmentActivity mActivity;
    /**
     * The current action mode when item is selection (<code>null</code> if no
     * action started).
     */
    private ActionMode mActionMode;
    /**
     * The action mode callback (<code>null</code> if view is not created).
     */
    private ActionMode.Callback mActionCallback;
    /**
     * The hosts list related to the current action (<code>null</code> if view is
     * not created).
     */
    private HostListItem mActionItem;
    /**
     * The view related hosts source of the current action (<code>null</code> if
     * view is not created).
     */
    private View mActionSourceView;
    /**
     * Whether the list is currently waiting on a refresh load.
     */
    private boolean mListLoading = true;
    /**
     * Whether the last refresh load failed.
     */
    private boolean mListLoadFailed;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        // Store activity
        this.mActivity = requireActivity();
        // Create fragment view
        View view = inflater.inflate(R.layout.hosts_lists_fragment, container, false);
        /*
         * Configure recycler view.
         */
        // Store recycler view
        RecyclerView recyclerView = view.findViewById(R.id.hosts_lists_list);
        recyclerView.setHasFixedSize(true);
        // Defile recycler layout
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this.mActivity);
        recyclerView.setLayoutManager(linearLayoutManager);
        // Create recycler adapter
        ListsAdapter adapter = new ListsAdapter(this, isTwoRowsItem());
        recyclerView.setAdapter(adapter);
        view.findViewById(R.id.hostsListsStateRetryButton)
                .setOnClickListener(retryView -> adapter.retry());
        adapter.addLoadStateListener(loadStates -> {
            LoadState refresh = loadStates.getRefresh();
            this.mListLoading = refresh instanceof LoadState.Loading;
            this.mListLoadFailed = refresh instanceof LoadState.Error;
            updateListState(view, recyclerView, adapter);
            return Unit.INSTANCE;
        });
        adapter.addOnPagesUpdatedListener(() -> {
            updateListState(view, recyclerView, adapter);
            return Unit.INSTANCE;
        });
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

            public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
                boolean editable = mActionItem.getSourceId() == USER_SOURCE_ID;
                menu.findItem(R.id.edit_action).setVisible(editable);
                menu.findItem(R.id.delete_action).setVisible(editable);
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode actionMode, MenuItem item) {
                // Check action item
                if (mActionItem == null) {
                    return false;
                }
                // Check item identifier
                if (item.getItemId() == R.id.edit_action) {
                    // Edit action item
                    editItem(mActionItem);
                    // Finish action mode
                    mActionMode.finish();
                    return true;
                } else if (item.getItemId() == R.id.move_action) {
                    // Move action item
                    mViewModel.moveListItem(mActionItem);
                    // Finish action mode
                    mActionMode.finish();
                    return true;
                } else if (item.getItemId() == R.id.delete_action) {
                    // Delete action item
                    deleteItem(mActionItem);
                    // Finish action mode
                    mActionMode.finish();
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public void onDestroyActionMode(ActionMode actionMode) {
                // Clear view background color
                if (mActionSourceView != null) {
                    mActionSourceView.setBackgroundColor(Color.TRANSPARENT);
                }
                // Clear current source and its view
                mActionItem = null;
                mActionSourceView = null;
                // Clear action mode
                mActionMode = null;
            }
        };
        /*
         * Load data.
         */
        // Get view model and bind it to the list view
        this.mViewModel = new ViewModelProvider(this.mActivity).get(ListsViewModel.class);
        getData().observe(getViewLifecycleOwner(),
                data -> adapter.submitData(getViewLifecycleOwner().getLifecycle(), data));
        /*
         * Wire search bar.
         */
        TextInputEditText searchEditText = view.findViewById(R.id.hostsSearchEditText);
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mViewModel.search(s.toString());
                updateListState(view, recyclerView, adapter);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
        // Restore search query if one is active (e.g. after config change)
        String currentQuery = this.mViewModel.getSearchQuery();
        if (!currentQuery.isEmpty()) {
            searchEditText.setText(currentQuery);
        }
        // Return created view
        return view;
    }

    @Override
    public void onDestroyView() {
        ensureActionModeCanceled();
        this.mActionCallback = null;
        this.mActivity = null;
        super.onDestroyView();
    }

    private void updateListState(@NonNull View root, @NonNull RecyclerView recyclerView,
            @NonNull ListsAdapter adapter) {
        int state = ListsUiState.resolve(this.mListLoading, this.mListLoadFailed,
                adapter.getItemCount(), this.mViewModel != null && this.mViewModel.isSearching());
        View stateContainer = root.findViewById(R.id.hostsListsStateContainer);
        TextView stateTitle = root.findViewById(R.id.hostsListsStateTitle);
        TextView stateMessage = root.findViewById(R.id.hostsListsStateMessage);
        ProgressBar stateProgress = root.findViewById(R.id.hostsListsStateProgress);
        View retryButton = root.findViewById(R.id.hostsListsStateRetryButton);
        boolean visible = state != ListsUiState.HIDDEN;
        stateContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(visible ? View.GONE : View.VISIBLE);
        stateProgress.setVisibility(state == ListsUiState.LOADING ? View.VISIBLE : View.GONE);
        retryButton.setVisibility(state == ListsUiState.LOAD_FAILED ? View.VISIBLE : View.GONE);

        if (state == ListsUiState.LOADING) {
            stateTitle.setText(R.string.lists_state_loading_title);
            stateMessage.setText(R.string.lists_state_loading_message);
        } else if (state == ListsUiState.LOAD_FAILED) {
            stateTitle.setText(R.string.lists_state_load_failed_title);
            stateMessage.setText(R.string.lists_state_load_failed_message);
        } else if (state == ListsUiState.NO_RULES) {
            stateTitle.setText(R.string.lists_state_no_rules_title);
            stateMessage.setText(R.string.lists_state_no_rules_message);
        } else if (state == ListsUiState.NO_MATCHES) {
            stateTitle.setText(R.string.lists_state_no_matches_title);
            stateMessage.setText(R.string.lists_state_no_matches_message);
        }
    }

    @Override
    public boolean startAction(HostListItem item, View sourceView) {
        // Check if there is already a current action
        if (this.mActionMode != null) {
            return false;
        }
        // Store current source and its view
        this.mActionItem = item;
        this.mActionSourceView = sourceView;
        // Get current item background color
        int currentItemBackgroundColor = getResources().getColor(R.color.selected_background, null);
        // Apply background color to view
        this.mActionSourceView.setBackgroundColor(currentItemBackgroundColor);
        // Start action mode and store it
        this.mActionMode = this.mActivity.startActionMode(this.mActionCallback);
        // Return event consumed
        return true;
    }

    @Override
    public boolean copyHostToClipboard(HostListItem item) {
        // Copy host to clipboard
        Clipboard.copyHostToClipboard(this.mActivity, item.getHost());
        return true;
    }

    @Override
    public void onToggleListItem(HostListItem item, boolean isChecked) {
        if (item.getSourceId() == USER_SOURCE_ID) {
            mViewModel.toggleItemEnabled(item);
        } else {
            if (!isChecked) {
                // User turned it OFF -> Prompt to override (whitelist)
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle(R.string.checkbox_list_context_move)
                        .setMessage(R.string.checkbox_list_override_downloaded_message)
                        .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                            mViewModel.moveListItem(item);
                        })
                        .setNegativeButton(android.R.string.no, (dialog, which) -> {
                            // Refresh list to revert checkbox state
                            mViewModel.search(mViewModel.isSearching() ? mViewModel.getSearchQuery() : "");
                        })
                        .show();
            } else {
                // User turned it ON -> Just re-enable the block
                mViewModel.toggleItemEnabled(item);
            }
        }
    }

    /**
     * Ensure action mode is cancelled.
     */
    public void ensureActionModeCanceled() {
        if (this.mActionMode != null) {
            this.mActionMode.finish();
        }
    }

    protected abstract LiveData<PagingData<HostListItem>> getData();

    protected boolean isTwoRowsItem() {
        return false;
    }

    /**
     * Display a UI to add an item to the list.
     */
    public abstract void addItem();

    protected abstract void editItem(HostListItem item);

    protected void deleteItem(HostListItem item) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.list_delete_confirm_title)
                .setMessage(getString(R.string.list_delete_confirm_message, item.getHost()))
                .setNegativeButton(R.string.button_cancel, null)
                .setPositiveButton(R.string.list_delete_confirm_action,
                        (dialog, which) -> this.mViewModel.removeListItem(item))
                .show();
    }

    @Override
    public void toggleItemEnabled(HostListItem item) {
        this.mViewModel.toggleItemEnabled(item);
    }
}
