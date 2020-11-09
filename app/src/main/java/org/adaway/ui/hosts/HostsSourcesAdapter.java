package org.adaway.ui.hosts;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.adaway.R;
import org.adaway.db.entity.HostsSource;

import java.time.Duration;
import java.time.ZonedDateTime;

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
    private static final String[] QUANTITY_PREFIXES = new String[]{"k", "M", "G"};

    /**
     * Constructor.
     *
     * @param viewCallback The view callback.
     */
    HostsSourcesAdapter(@NonNull HostsSourcesViewCallback viewCallback) {
        super(DIFF_CALLBACK);
        this.viewCallback = viewCallback;
    }

    /**
     * Get the approximate delay from a date to now.
     *
     * @param context The application context.
     * @param from    The date from which computes the delay.
     * @return The approximate delay.
     */
    private static String getApproximateDelay(Context context, ZonedDateTime from) {
        // Get resource for plurals
        Resources resources = context.getResources();
        // Get current date in UTC timezone
        ZonedDateTime now = ZonedDateTime.now();
        // Get delay between from and now in minutes
        long delay = Duration.between(from, now).toMinutes();
        // Check if delay is lower than an hour
        if (delay < 60) {
            return resources.getString(R.string.hosts_source_few_minutes);
        }
        // Get delay in hours
        delay /= 60;
        // Check if delay is lower than a day
        if (delay < 24) {
            int hours = (int) delay;
            return resources.getQuantityString(R.plurals.hosts_source_hours, hours, hours);
        }
        // Get delay in days
        delay /= 24;
        // Check if delay is lower than a month
        if (delay < 30) {
            int days = (int) delay;
            return resources.getQuantityString(R.plurals.hosts_source_days, days, days);
        }
        // Get delay in months
        int months = (int) delay / 30;
        return resources.getQuantityString(R.plurals.hosts_source_months, months, months);
    }

    @NonNull
    @Override
    public HostsSourcesAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        View view = layoutInflater.inflate(R.layout.hosts_sources_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HostsSource source = this.getItem(position);
        holder.enabledCheckBox.setChecked(source.isEnabled());
        holder.enabledCheckBox.setOnClickListener(view -> viewCallback.toggleEnabled(source));
        holder.labelTextView.setText(source.getLabel());
        holder.urlTextView.setText(source.getUrl());
        holder.updateTextView.setText(getUpdateText(source));
        holder.sizeTextView.setText(getHostCount(source));
        holder.itemView.setOnClickListener(view -> viewCallback.edit(source));
    }

    private String getUpdateText(HostsSource source) {
        // Get context
        Context context = this.viewCallback.getContext();
        // Check modification dates
        boolean lastOnlineModificationDefined = source.getOnlineModificationDate() != null;
        boolean lastLocalModificationDefined = source.getLocalModificationDate() != null;
        // Declare update text
        String updateText;
        // Check if online modification date is unknown
        if (!lastOnlineModificationDefined) {
            if (lastLocalModificationDefined) {
                String approximateDelay = getApproximateDelay(context, source.getLocalModificationDate());
                updateText = context.getString(R.string.hosts_source_installed, approximateDelay);
            } else {
                updateText = context.getString(R.string.hosts_source_unknown_status);
            }
            return updateText;
        }
        // Get last online modification delay
        String approximateDelay = getApproximateDelay(context, source.getOnlineModificationDate());
        if (!source.isEnabled() || !lastLocalModificationDefined) {
            updateText = context.getString(R.string.hosts_source_last_update, approximateDelay);
        } else if (source.getOnlineModificationDate().isAfter(source.getLocalModificationDate())) {
            updateText = context.getString(R.string.hosts_source_need_update, approximateDelay);
        } else {
            updateText = context.getString(R.string.hosts_source_up_to_date, approximateDelay);
        }
        return updateText;
    }

    private String getHostCount(HostsSource source) {
        // Note: NumberFormat.getCompactNumberInstance is Java 12 only
        // Check empty source
        int size = source.getSize();
        if (size <= 0) {
            return "";
        }
        // Compute size decimal length
        int length = 1;
        while (size > 10) {
            size /= 10;
            length++;
        }
        // Compute prefix to use
        int prefixIndex = (length - 1) / 3 - 1;
        // Return formatted count
        Context context = this.viewCallback.getContext();
        size = source.getSize();
        if (prefixIndex < 0) {
            return context.getString(R.string.hosts_count, Integer.toString(size));
        } else if (prefixIndex >= QUANTITY_PREFIXES.length) {
            prefixIndex = QUANTITY_PREFIXES.length - 1;
            size = 13;
        }
        size = Math.toIntExact(Math.round(size / Math.pow(10, (prefixIndex + 1) * 3D)));
        return context.getString(R.string.hosts_count, size + QUANTITY_PREFIXES[prefixIndex]);
    }

    /**
     * This class is a the {@link RecyclerView.ViewHolder} for the hosts sources view.
     *
     * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        final CheckBox enabledCheckBox;
        final TextView labelTextView;
        final TextView urlTextView;
        final TextView updateTextView;
        final TextView sizeTextView;

        /**
         * Constructor.
         *
         * @param itemView The hosts sources view.
         */
        ViewHolder(View itemView) {
            super(itemView);
            this.enabledCheckBox = itemView.findViewById(R.id.sourceEnabledCheckBox);
            this.labelTextView = itemView.findViewById(R.id.sourceLabelTextView);
            this.urlTextView = itemView.findViewById(R.id.sourceUrlTextView);
            this.updateTextView = itemView.findViewById(R.id.sourceUpdateTextView);
            this.sizeTextView = itemView.findViewById(R.id.sourceSizeTextView);
        }
    }
}
