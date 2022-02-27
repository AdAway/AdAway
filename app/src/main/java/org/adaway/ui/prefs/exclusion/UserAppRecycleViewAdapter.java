package org.adaway.ui.prefs.exclusion;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.adaway.databinding.VpnExcludedAppEntryBinding;

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
        VpnExcludedAppEntryBinding binding = VpnExcludedAppEntryBinding.inflate(layoutInflater, parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UserApp[] applications = this.controller.getUserApplications();
        if (position < 0 || position >= applications.length) {
            return;
        }
        UserApp application = applications[position];
        holder.binding.rowLayout.setOnClickListener(
                v -> holder.binding.excludedSwitch.setChecked(!holder.binding.excludedSwitch.isChecked())
        );
        holder.binding.iconImageView.setImageDrawable(application.icon);
        holder.binding.nameTextView.setText(application.name);
        holder.binding.packageTextView.setText(application.packageName);
        holder.binding.excludedSwitch.setOnCheckedChangeListener(null);
        holder.binding.excludedSwitch.setChecked(application.excluded);
        holder.binding.excludedSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
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
        final VpnExcludedAppEntryBinding binding;

        /**
         * Constructor.
         *
         * @param binding The hosts sources view binding.
         */
        ViewHolder(VpnExcludedAppEntryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
