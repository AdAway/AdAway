package org.adaway.ui.hosts;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import org.adaway.R;
import org.adaway.db.entity.HostsSource;

import java.util.List;

/**
 * This class is an {@link ArrayAdapter} to bind hosts sources to list item view.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class HostsSourcesAdapter extends ArrayAdapter<HostsSource> {
    private final LayoutInflater mLayoutInflater;

    /**
     * Constructor.
     *
     * @param context The application context.
     * @param sources The sources to display into the list.
     */
    HostsSourcesAdapter(@NonNull Context context, @NonNull List<HostsSource> sources) {
        super(context, R.layout.checkbox_list_two_entries, sources);
        this.mLayoutInflater = LayoutInflater.from(context);
    }


    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        // Try to recycle view
        View view = convertView;
        if (view == null) {
            view = this.mLayoutInflater.inflate(R.layout.checkbox_list_two_entries, parent, false);
        }
        // Bind source to view
        HostsSource source = this.getItem(position);
        if (source != null) {
            bindView(view, source);
        }
        // Return view
        return view;
    }

    private void bindView(View view, HostsSource source) {
        CheckBox enabledCheckBox = view.findViewById(R.id.checkbox_list_checkbox);
        TextView hostnameTextView = view.findViewById(R.id.checkbox_list_text);
        TextView lastModifiedTextView = view.findViewById(R.id.checkbox_list_subtext);

        enabledCheckBox.setChecked(source.isEnabled());
        hostnameTextView.setText(source.getUrl());
        lastModifiedTextView.setText(getModifiedText(source));
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
}
