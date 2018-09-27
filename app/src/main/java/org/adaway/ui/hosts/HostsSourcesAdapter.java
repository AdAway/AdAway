package org.adaway.ui.hosts;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.recyclerview.extensions.ListAdapter;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import org.adaway.R;
import org.adaway.db.entity.HostsSource;
import org.adaway.util.DateUtils;

/**
 * This class is a the {@link RecyclerView.Adapter} for the hosts sources view.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class HostsSourcesAdapter extends ListAdapter<HostsSource, HostsSourcesAdapter.ViewHolder> {
    /**
     * This callback is use to compare hosts sources.
     */
    private static final DiffUtil.ItemCallback<HostsSource> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<HostsSource>() {
                @Override
                public boolean areItemsTheSame(@NonNull HostsSource oldSource, @NonNull HostsSource newSource) {
                    return oldSource.getUrl().equals(newSource.getUrl());
                }

                @Override
                public boolean areContentsTheSame(@NonNull HostsSource oldSource, @NonNull HostsSource newSource) {
                    // NOTE: if you use equals, your object must properly override Object#equals()
                    // Incorrectly returning false here will result in too many animations.
                    return oldSource.equals(newSource);
                }
            };

    /**
     * This callback is use to call view actions.
     */
    @NonNull
    private final HostsSourcesViewCallback viewCallback;

    /**
     * Constructor.
     *
     * @param viewCallback The view callback.
     */
    HostsSourcesAdapter(@NonNull HostsSourcesViewCallback viewCallback) {
        super(DIFF_CALLBACK);
        this.viewCallback = viewCallback;
    }

    @NonNull
    @Override
    public HostsSourcesAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        View view = layoutInflater.inflate(R.layout.checkbox_list_two_entries, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HostsSource source = this.getItem(position);
        holder.enabledCheckBox.setChecked(source.isEnabled());
        holder.enabledCheckBox.setOnClickListener(view -> viewCallback.toggleEnabled(source));
        holder.hostnameTextView.setText(source.getUrl());
        holder.updateTextView.setText(getUpdateText(source));
        holder.itemView.setOnLongClickListener(view -> viewCallback.startAction(source, holder.itemView));
    }

    private String getUpdateText(HostsSource source) {
        // Get context
        Context context = this.viewCallback.getContext();
        // Check modification dates
        boolean lastOnlineModificationDefined = source.getLastOnlineModification() != null &&
                source.getLastOnlineModification().getTime() != 0;
        boolean lastLocalModificationDefined = source.getLastLocalModification() != null &&
                source.getLastLocalModification().getTime() != 0;
        // Get last online modification delay
        String approximateDelay = lastOnlineModificationDefined ? DateUtils.getApproximateDelay(context, source.getLastOnlineModification()) : "";
        // Declare update text
        String updateText;
        if (!lastOnlineModificationDefined) {
            updateText = context.getString(R.string.hosts_source_unknown_status);
        } else if (!source.isEnabled() || !lastLocalModificationDefined) {
            updateText = context.getString(R.string.hosts_source_last_update, approximateDelay);
        } else if (source.getLastOnlineModification().after(source.getLastLocalModification())) {
            updateText = context.getString(R.string.hosts_source_need_update, approximateDelay);
        } else {
            updateText = context.getString(R.string.hosts_source_up_to_date, approximateDelay);
        }
        return updateText;
    }

    /**
     * This class is a the {@link RecyclerView.ViewHolder} for the hosts sources view.
     *
     * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        final CheckBox enabledCheckBox;
        final TextView hostnameTextView;
        final TextView updateTextView;

        /**
         * Constructor.
         *
         * @param itemView The hosts sources view.
         */
        ViewHolder(View itemView) {
            super(itemView);
            this.enabledCheckBox = itemView.findViewById(R.id.checkbox_list_checkbox);
            this.hostnameTextView = itemView.findViewById(R.id.checkbox_list_text);
            this.updateTextView = itemView.findViewById(R.id.checkbox_list_subtext);
        }
    }
}
