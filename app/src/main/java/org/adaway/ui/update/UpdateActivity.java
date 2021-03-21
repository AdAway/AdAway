package org.adaway.ui.update;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import org.adaway.R;
import org.adaway.databinding.UpdateActityBinding;
import org.adaway.helper.ThemeHelper;
import org.adaway.model.update.Manifest;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

/**
 * This class is the application main activity.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class UpdateActivity extends AppCompatActivity {
    private UpdateActityBinding binding;
    private UpdateViewModel updateViewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        this.binding = UpdateActityBinding.inflate(getLayoutInflater());
        setContentView(this.binding.getRoot());

        this.updateViewModel = new ViewModelProvider(this).get(UpdateViewModel.class);
        bindListener();
        bindManifest();
        bindProgress();
    }

    private void bindListener() {
        this.binding.updateButton.setOnClickListener(this::startUpdate);
    }

    private void bindManifest() {
        this.updateViewModel.getAppManifest().observe(this, manifest -> {
            if (manifest.updateAvailable) {
                showUpdate(manifest);
            } else {
                markUpToDate(manifest);
            }
        });
    }

    private void bindProgress() {
        this.updateViewModel.getDownloadProgress().observe(this, progress -> {
            this.binding.updateButton.setVisibility(INVISIBLE);
            this.binding.downloadProgressBar.setVisibility(VISIBLE);
            this.binding.downloadProgressBar.setProgress(progress.progress, true);
            this.binding.progressTextView.setText(progress.format(this));
        });
    }

    private void markUpToDate(Manifest manifest) {
        this.binding.headerTextView.setText(R.string.update_up_to_date_header);
        this.binding.updateButton.setVisibility(INVISIBLE);
        this.binding.changelogTextView.setText(manifest.changelog);
    }

    private void showUpdate(Manifest manifest) {
        this.binding.headerTextView.setText(R.string.update_update_available_header);
        this.binding.updateButton.setVisibility(VISIBLE);
        this.binding.changelogTextView.setText(manifest.changelog);
    }

    private void startUpdate(View view) {
        this.binding.updateButton.setVisibility(INVISIBLE);
        this.binding.downloadProgressBar.setVisibility(VISIBLE);
        this.updateViewModel.update();
    }
}
