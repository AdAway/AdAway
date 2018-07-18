package org.adaway.ui.lists;

import android.app.Activity;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.ViewModelProviders;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.recyclerview.extensions.ListAdapter;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.adaway.R;
import org.adaway.db.entity.HostListItem;

import java.util.List;

/**
 * This class is a {@link Fragment} to display and manage lists of {@link ListsFragment}.
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
    protected Activity mActivity;
    /**
     * The current action mode when item is selection (<code>null</code> if no action started).
     */
    private ActionMode mActionMode;
    /**
     * The action mode callback (<code>null</code> if view is not created).
     */
    private ActionMode.Callback mActionCallback;
    /**
     * The hosts list related to the current action (<code>null</code> if view is not created).
     */
    private HostListItem mActionItem;
    /**
     * The view related hosts source of the current action (<code>null</code> if view is not created).
     */
    private View mActionSourceView;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Store activity
        this.mActivity = this.getActivity();
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
        ListAdapter adapter = new ListsAdapter(this, this.isTwoRowsItem());
        recyclerView.setAdapter(adapter);
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
                // Check action item
                if (AbstractListFragment.this.mActionItem == null) {
                    return false;
                }
                // Check item identifier
                switch (item.getItemId()) {
                    case R.id.checkbox_list_context_edit:
                        // Edit action item
                        AbstractListFragment.this.editItem(AbstractListFragment.this.mActionItem);
                        // Finish action mode
                        AbstractListFragment.this.mActionMode.finish();
                        return true;
                    case R.id.checkbox_list_context_delete:
                        // Delete action item
                        AbstractListFragment.this.deleteItem(AbstractListFragment.this.mActionItem);
                        // Finish action mode
                        AbstractListFragment.this.mActionMode.finish();
                        return true;
                    default:
                        return false;
                }
            }

            @Override
            public void onDestroyActionMode(ActionMode actionMode) {
                // Clear view background color
                if (AbstractListFragment.this.mActionSourceView != null) {
                    AbstractListFragment.this.mActionSourceView.setBackgroundColor(Color.TRANSPARENT);
                }
                // Clear current source and its view
                AbstractListFragment.this.mActionItem = null;
                AbstractListFragment.this.mActionSourceView = null;
                // Clear action mode
                AbstractListFragment.this.mActionMode = null;
            }
        };
        /*
         * Load data.
         */
        // Get view model and bind it to the list view
        this.mViewModel = ViewModelProviders.of(this).get(ListsViewModel.class);
        this.getData().observe(this, adapter::submitList);
        // Return created view
        return view;
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
        int currentItemBackgroundColor = this.getResources().getColor(R.color.selected_background);
        // Apply background color to view
        this.mActionSourceView.setBackgroundColor(currentItemBackgroundColor);
        // Start action mode and store it
        this.mActionMode = this.mActivity.startActionMode(this.mActionCallback);
        // Return event consumed
        return true;
    }

    /**
     * Ensure action mode is cancelled.
     */
    void ensureActionModeCanceled() {
        if (this.mActionMode != null) {
            this.mActionMode.finish();
        }
    }

    protected abstract LiveData<List<HostListItem>> getData();

    protected boolean isTwoRowsItem() {
        return false;
    }

    protected abstract void addItem();

    protected abstract void editItem(HostListItem item);

    protected void deleteItem(HostListItem item) {
        this.mViewModel.removeListItem(item);
    }

    @Override
    public void toggleItemEnabled(HostListItem list) {
        this.mViewModel.toggleItemEnabled(list);
    }
}
