package org.adaway.ui.tcpdump;

import android.support.annotation.NonNull;
import android.support.v7.recyclerview.extensions.ListAdapter;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.adaway.R;
import org.adaway.db.entity.ListType;

class TcpdumpLogAdapter extends ListAdapter<LogEntry, TcpdumpLogAdapter.ViewHolder> {
    /**
     * This callback is use to compare hosts sources.
     */
    private static final DiffUtil.ItemCallback<LogEntry> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<LogEntry>() {
                @Override
                public boolean areItemsTheSame(@NonNull LogEntry oldEntry, @NonNull LogEntry newEntry) {
                    return oldEntry.getHost().equals(newEntry.getHost());
                }

                @Override
                public boolean areContentsTheSame(@NonNull LogEntry oldEntry, @NonNull LogEntry newEntry) {
                    return oldEntry.equals(newEntry);
                }
            };

    /**
     * The activity view model.
     */
    private final TcpdumpLogViewModel viewModel;    // TODO Create a proper callback interface

    TcpdumpLogAdapter(TcpdumpLogViewModel viewModel) {
        super(DIFF_CALLBACK);
        this.viewModel = viewModel;
    }

    @NonNull
    @Override
    public TcpdumpLogAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        View view = layoutInflater.inflate(R.layout.tcpdump_log_entry, parent, false);
        return new TcpdumpLogAdapter.ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TcpdumpLogAdapter.ViewHolder holder, int position) {
        // Get log entry
        LogEntry entry = this.getItem(position);
        String hostName = entry.getHost();
        ListType hostType = entry.getType();
        // Set host name
        holder.hostnameTextView.setText(hostName);
        // Set type status
        if (hostType != null) {
            switch (hostType) {
                case BLACK_LIST:
                    holder.blackImageView.setColorFilter(R.color.primary);
                    holder.whiteImageView.clearColorFilter();
                    holder.redirectionImageView.clearColorFilter();
                    break;
                case WHITE_LIST:
                    holder.blackImageView.clearColorFilter();
                    holder.whiteImageView.setColorFilter(R.color.primary);
                    holder.redirectionImageView.clearColorFilter();
                    break;
                case REDIRECTION_LIST:
                    holder.blackImageView.clearColorFilter();
                    holder.whiteImageView.clearColorFilter();
                    holder.redirectionImageView.setColorFilter(R.color.primary);
                    break;
            }
        } else {
            holder.blackImageView.clearColorFilter();
            holder.whiteImageView.clearColorFilter();
            holder.redirectionImageView.clearColorFilter();
        }
        // Bind click listener
        holder.blackImageView.setOnClickListener(v -> this.viewModel.addListItem(ListType.BLACK_LIST, hostName, null));
        holder.whiteImageView.setOnClickListener(v -> this.viewModel.addListItem(ListType.WHITE_LIST, hostName, null));
        // TODO Add dialog here
        holder.redirectionImageView.setOnClickListener(v -> this.viewModel.addListItem(ListType.REDIRECTION_LIST, hostName, "1.1.1.1"));
    }

    /**
     * This class is a the {@link RecyclerView.ViewHolder} for the log entry view.
     *
     * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView blackImageView;
        final ImageView whiteImageView;
        final ImageView redirectionImageView;
        final TextView hostnameTextView;

        /**
         * Constructor.
         *
         * @param itemView The hosts sources view.
         */
        ViewHolder(View itemView) {
            super(itemView);
            this.blackImageView = itemView.findViewById(R.id.blockImageView);
            this.whiteImageView = itemView.findViewById(R.id.whiteImageView);
            this.redirectionImageView = itemView.findViewById(R.id.redirectionImageView);
            this.hostnameTextView = itemView.findViewById(R.id.hostnameTextView);
        }
    }
}
