package org.adaway.ui.welcome;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import org.adaway.R;
import org.adaway.model.error.HostError;
import org.adaway.ui.home.HomeViewModel;

import static org.adaway.ui.welcome.WelcomeActivity.hideView;
import static org.adaway.ui.welcome.WelcomeActivity.showView;

/**
 * This class is a fragment to first sync the main hosts source.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class WelcomeSyncFragment extends WelcomeFragment {
    private TextView headerTextView;
    private ProgressBar progressBar;
    private ImageView syncedImageView;
    private ImageView errorImageView;
    private ImageView retryImageView;
    private TextView errorTextView;
    private HomeViewModel homeViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.welcome_sync_layout, container, false);

        this.headerTextView = view.findViewById(R.id.headerTextView);
        this.progressBar = view.findViewById(R.id.progressBar);
        this.syncedImageView = view.findViewById(R.id.syncedImageView);
        this.errorImageView = view.findViewById(R.id.errorImageView);

        this.bindRetry(view);

        this.homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        LifecycleOwner lifecycleOwner = getViewLifecycleOwner();
        this.homeViewModel.isAdBlocked().observe(lifecycleOwner, adBlocked -> {
            if (adBlocked) {
                notifySynced();
            }
        });
        this.homeViewModel.getError().observe(lifecycleOwner, this::notifyError);
        this.homeViewModel.sync();

        return view;
    }

    private void bindRetry(View view) {
        this.retryImageView = view.findViewById(R.id.retryImageView);
        this.errorTextView = view.findViewById(R.id.errorTextView);
        this.retryImageView.setOnClickListener(this::retry);
        this.errorTextView.setOnClickListener(this::retry);
    }

    private void notifySynced() {
        this.homeViewModel.enableAllSources();
        this.headerTextView.setText(R.string.welcome_synced_header);
        hideView(this.progressBar);
        showView(this.syncedImageView);
        allowNext();
    }

    private void notifyError(HostError error) {
        String errorMessage = getResources().getText(error.getMessageKey()).toString();
        String syncError = getResources().getText(R.string.welcome_sync_error).toString();
        String retryMessage = String.format(syncError, errorMessage);
        this.errorTextView.setText(retryMessage);
        hideView(this.progressBar);
        showView(this.errorImageView);
        showView(this.retryImageView);
        showView(this.errorTextView);
    }

    private void retry(@SuppressWarnings("unused") View view) {
        hideView(this.errorImageView);
        hideView(this.retryImageView);
        hideView(this.errorTextView);
        showView(this.progressBar);
        this.homeViewModel.sync();
    }
}
