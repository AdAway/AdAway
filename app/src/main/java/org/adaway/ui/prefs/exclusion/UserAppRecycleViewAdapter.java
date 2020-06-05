package org.adaway.ui.prefs.exclusion;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.adaway.R;

/**
 * This class is the {@link RecyclerView.Adapter} for the {@link PrefsVpnExcludedAppsActivity}.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class UserAppRecycleViewAdapter extends RecyclerView.Adapter<UserAppRecycleViewAdapter.ViewHolder> {
    private final ExcludedAppController controller;

    /**
     * Constructor.
     *
     * @param controller The user applications.
     */
    UserAppRecycleViewAdapter(ExcludedAppController controller) {
        this.controller = controller;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        View view = layoutInflater.inflate(R.layout.vpn_excluded_app_entry, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UserApp[] applications = this.controller.getUserApplications();
        if (position < 0 || position >= applications.length) {
            return;
        }
        UserApp application = applications[position];
        holder.iconImageView.setImageDrawable(application.icon);
        holder.nameTextView.setText(application.name);
        holder.descriptionTextView.setText(application.packageName);
        holder.excludedSwitch.setOnCheckedChangeListener(null);
        holder.excludedSwitch.setChecked(application.excluded);
        holder.excludedSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                this.controller.excludeApplications(application);
            } else {
                this.controller.includeApplications(application);
            }
        });
    }

    @Override
    public int getItemCount() {
        return this.controller.getUserApplications().length;
    }

    /**
     * This class is a the {@link RecyclerView.ViewHolder} for the app list view.
     *
     * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView iconImageView;
        final TextView nameTextView;
        final TextView descriptionTextView;
        final SwitchCompat excludedSwitch;

        /**
         * Constructor.
         *
         * @param itemView The hosts sources view.
         */
        ViewHolder(View itemView) {
            super(itemView);
            this.iconImageView = itemView.findViewById(R.id.iconImageView);
            this.nameTextView = itemView.findViewById(R.id.nameTextView);
            this.descriptionTextView = itemView.findViewById(R.id.packageTextView);
            this.excludedSwitch = itemView.findViewById(R.id.excludedSwitch);
        }
    }
}
