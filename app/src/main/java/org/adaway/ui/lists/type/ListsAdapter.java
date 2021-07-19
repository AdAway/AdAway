package org.adaway.ui.lists.type;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.adaway.R;
import org.adaway.db.entity.HostListItem;
import org.adaway.ui.lists.ListsViewCallback;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;

/**
 * This class is a the {@link RecyclerView.Adapter} for the hosts list view.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class ListsAdapter extends PagingDataAdapter<HostListItem, ListsAdapter.ViewHolder> {
    /**
     * This callback is use to compare hosts sources.
     */
    private static final DiffUtil.ItemCallback<HostListItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<HostListItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull HostListItem oldItem, @NonNull HostListItem newItem) {
                    return (oldItem.getHost().equals(newItem.getHost()));
                }

                @Override
                public boolean areContentsTheSame(@NonNull HostListItem oldItem, @NonNull HostListItem newItem) {
                    // NOTE: if you use equals, your object must properly override Object#equals()
                    // Incorrectly returning false here will result in too many animations.
                    return oldItem.equals(newItem);
                }
            };

    /**
     * This callback is use to call view actions.
     */
    @NonNull
    private final ListsViewCallback viewCallback;
    /**
     * Whether the list item needs two rows or not.
     */
    private final boolean twoRows;

    /**
     * Constructor.
     *
     * @param viewCallback The view callback.
     * @param twoRows      Whether the list items need two rows or not.
     */
    ListsAdapter(@NonNull ListsViewCallback viewCallback, boolean twoRows) {
        super(DIFF_CALLBACK);
        this.viewCallback = viewCallback;
        this.twoRows = twoRows;
    }

    @NonNull
    @Override
    public ListsAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        View view = layoutInflater.inflate(
                this.twoRows ? R.layout.checkbox_list_two_entries : R.layout.checkbox_list_entry,
                parent,
                false
        );
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HostListItem item = getItem(position);
        if (item == null) { // Data might be null if not loaded yet
            holder.clear();
            return;
        }
        boolean editable = item.getSourceId() == USER_SOURCE_ID;
        holder.enabledCheckBox.setEnabled(editable);
        holder.enabledCheckBox.setChecked(item.isEnabled());
        holder.enabledCheckBox.setOnClickListener(editable ? view -> viewCallback.toggleItemEnabled(item) : null);
        holder.hostTextView.setText(item.getHost());
        if (this.twoRows) {
            holder.redirectionTextView.setText(item.getRedirection());
        }
        holder.itemView.setOnLongClickListener(editable ? view -> viewCallback.startAction(item, holder.itemView) : null);
    }

    /**
     * This class is a the {@link RecyclerView.ViewHolder} for the hosts list view.
     *
     * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        final CheckBox enabledCheckBox;
        final TextView hostTextView;
        final TextView redirectionTextView;

        /**
         * Constructor.
         *
         * @param itemView The hosts sources view.
         */
        ViewHolder(View itemView) {
            super(itemView);
            this.enabledCheckBox = itemView.findViewById(R.id.checkbox_list_checkbox);
            this.hostTextView = itemView.findViewById(R.id.checkbox_list_text);
            this.redirectionTextView = itemView.findViewById(R.id.checkbox_list_subtext);
        }

        void clear() {
            this.enabledCheckBox.setChecked(true);
            this.enabledCheckBox.setEnabled(false);
            this.enabledCheckBox.setOnClickListener(null);
            this.hostTextView.setText("");
            if (this.redirectionTextView != null) {
                this.redirectionTextView.setText("");
            }
            this.itemView.setOnLongClickListener(null);
        }
    }
}
