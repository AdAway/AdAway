package org.adaway.ui.welcome;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import org.adaway.R;
import org.adaway.databinding.WelcomeSyncLayoutBinding;
import org.adaway.model.error.HostError;
import org.adaway.ui.home.HomeViewModel;

import static org.adaway.ui.Animations.hideView;
import static org.adaway.ui.Animations.showView;

/**
 * This class is a fragment to first sync the main hosts source.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class WelcomeSyncFragment extends WelcomeFragment {
    private WelcomeSyncLayoutBinding binding;
    private HomeViewModel homeViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        this.binding = WelcomeSyncLayoutBinding.inflate(inflater, container, false);
        bindRetry();

        this.homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        LifecycleOwner lifecycleOwner = getViewLifecycleOwner();
        this.homeViewModel.isAdBlocked().observe(lifecycleOwner, adBlocked -> {
            if (adBlocked) {
                notifySynced();
            }
        });
        this.homeViewModel.getError().observe(lifecycleOwner, this::notifyError);
        this.homeViewModel.sync();

        return this.binding.getRoot();
    }

    private void bindRetry() {
        this.binding.retryImageView.setOnClickListener(this::retry);
        this.binding.errorTextView.setOnClickListener(this::retry);
    }

    private void notifySynced() {
        this.homeViewModel.enableAllSources();
        this.binding.headerTextView.setText(R.string.welcome_synced_header);
        hideView(this.binding.progressBar);
        showView(this.binding.syncedImageView);
        allowNext();
    }

    private void notifyError(HostError error) {
        String errorMessage = getResources().getText(error.getMessageKey()).toString();
        String syncError = getResources().getText(R.string.welcome_sync_error).toString();
        String retryMessage = String.format(syncError, errorMessage);
        this.binding.errorTextView.setText(retryMessage);
        hideView(this.binding.progressBar);
        showView(this.binding.errorImageView);
        showView(this.binding.retryImageView);
        showView(this.binding.errorTextView);
    }

    private void retry(@SuppressWarnings("unused") View view) {
        hideView(this.binding.errorImageView);
        hideView(this.binding.retryImageView);
        hideView(this.binding.errorTextView);
        showView(this.binding.progressBar);
        this.homeViewModel.sync();
    }
}
