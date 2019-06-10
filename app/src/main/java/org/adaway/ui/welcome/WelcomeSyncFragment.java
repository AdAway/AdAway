package org.adaway.ui.welcome;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProviders;

import org.adaway.R;
import org.adaway.ui.next.NextViewModel;
import org.adaway.util.Log;

public class WelcomeSyncFragment extends WelcomeFragment {

    //    private boolean synced = false;
    private ProgressBar progressBar;
    private ImageView syncedImageView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.welcome_sync_layout, container, false);

        this.progressBar = view.findViewById(R.id.progressBar);
        this.syncedImageView = view.findViewById(R.id.syncedImageView);

        NextViewModel nextViewModel = ViewModelProviders.of(this).get(NextViewModel.class);
        nextViewModel.isAdBlocked().observe(this, adBlocked -> {
            Log.w("SYNC", "AdBlocked status: " + adBlocked);
            if (adBlocked) {
                notifySynced();
            }
        });
        nextViewModel.getError().observe(this, error -> Log.w("SYNC", "Error: " + error));
        nextViewModel.sync();

        return view;
    }

    private void notifySynced() {
        this.progressBar.setVisibility(View.INVISIBLE);
        this.syncedImageView.setVisibility(View.VISIBLE);
        allowNext();
    }
}
