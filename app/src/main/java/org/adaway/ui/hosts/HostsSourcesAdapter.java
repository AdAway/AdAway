package org.adaway.ui.hosts;

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
        holder.lastModifiedTextView.setText(getModifiedText(source));
        holder.itemView.setOnLongClickListener(view -> viewCallback.startAction(source, holder.itemView));
    }

    private int getModifiedText(HostsSource source) {
        int modifiedText;
        if (source.getLastOnlineModification() == null || source.getLastLocalModification() == null) {
            modifiedText = R.string.hosts_source_unknown_status;
        } else if (source.getLastOnlineModification().after(source.getLastLocalModification())) {
            modifiedText = R.string.hosts_source_up_to_date;
        } else {
            modifiedText = R.string.hosts_source_up_to_date;
        }
        return modifiedText;
    }

    /**
     * This class is a the {@link RecyclerView.ViewHolder} for the hosts sources view.
     *
     * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        final CheckBox enabledCheckBox;
        final TextView hostnameTextView;
        final TextView lastModifiedTextView;

        /**
         * Constructor.
         *
         * @param itemView The hosts sources view.
         */
        ViewHolder(View itemView) {
            super(itemView);
            this.enabledCheckBox = itemView.findViewById(R.id.checkbox_list_checkbox);
            this.hostnameTextView = itemView.findViewById(R.id.checkbox_list_text);
            this.lastModifiedTextView = itemView.findViewById(R.id.checkbox_list_subtext);
        }
    }
}
