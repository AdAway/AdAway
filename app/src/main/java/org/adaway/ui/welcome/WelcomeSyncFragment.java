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
import androidx.lifecycle.ViewModelProviders;

import org.adaway.R;
import org.adaway.model.error.HostError;
import org.adaway.ui.next.NextViewModel;

import static org.adaway.ui.welcome.WelcomeActivity.hideView;
import static org.adaway.ui.welcome.WelcomeActivity.showView;

public class WelcomeSyncFragment extends WelcomeFragment {

    private TextView headerTextView;
    private ProgressBar progressBar;
    private ImageView syncedImageView;
    private ImageView errorImageView;
    private ImageView retryImageView;
    private TextView errorTextView;
    private NextViewModel nextViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.welcome_sync_layout, container, false);

        this.headerTextView = view.findViewById(R.id.headerTextView);
        this.progressBar = view.findViewById(R.id.progressBar);
        this.syncedImageView = view.findViewById(R.id.syncedImageView);
        this.errorImageView = view.findViewById(R.id.errorImageView);

        this.bindRetry(view);

        this.nextViewModel = ViewModelProviders.of(this).get(NextViewModel.class);
        this.nextViewModel.isAdBlocked().observe(this, adBlocked -> {
            if (adBlocked) {
                notifySynced();
            }
        });
        this.nextViewModel.getError().observe(this, this::notifyError);
        this.nextViewModel.sync();

        return view;
    }

    private void bindRetry(View view) {
        this.retryImageView = view.findViewById(R.id.retryImageView);
        this.errorTextView = view.findViewById(R.id.errorTextView);
        this.retryImageView.setOnClickListener(this::retry);
        this.errorTextView.setOnClickListener(this::retry);
    }

    private void notifySynced() {
        this.nextViewModel.enableAllSources();
        this.headerTextView.setText(R.string.welcome_synced_header);
        hideView(this.progressBar);
        showView(this.syncedImageView);
        allowNext();
    }

    private void notifyError(HostError error) {
        String errorMessage = getResources().getText(error.getMessageKey()).toString();
        String retryMessage = String.format(getResources().getText(R.string.welcome_sync_error).toString(), errorMessage);
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
        this.nextViewModel.sync();
    }
}
