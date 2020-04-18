package org.adaway.ui.tcpdump;

import android.graphics.PorterDuff;

import androidx.annotation.NonNull;

import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.adaway.R;
import org.adaway.db.entity.ListType;

import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;

/**
 * This class is a the {@link RecyclerView.Adapter} for the tcpdump log view.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
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
     * The activity view callback.
     */
    private final TcpdumpLogViewCallback callback;

    TcpdumpLogAdapter(TcpdumpLogViewCallback callback) {
        super(DIFF_CALLBACK);
        this.callback = callback;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        View view = layoutInflater.inflate(R.layout.tcpdump_log_entry, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // Get log entry
        LogEntry entry = getItem(position);
        // Set host name
        holder.hostnameTextView.setText(entry.getHost());
        holder.hostnameTextView.setOnClickListener(v -> this.callback.openHostInBrowser(entry.getHost()));
        // Set type status
        bindImageView(holder.blackImageView, BLOCKED, entry);
        bindImageView(holder.whiteImageView, ALLOWED, entry);
        bindImageView(holder.redirectionImageView, REDIRECTED, entry);
    }

    private void bindImageView(ImageView imageView, ListType type, LogEntry entry) {
        if (type == entry.getType()) {
            int primaryColor = this.callback.getColor(R.color.primary);
            imageView.setColorFilter(primaryColor, PorterDuff.Mode.MULTIPLY);
            imageView.setOnClickListener(v -> this.callback.removeListItem(entry.getHost()));
        } else {
            imageView.clearColorFilter();
            imageView.setOnClickListener(v -> this.callback.addListItem(entry.getHost(), type));
        }
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
