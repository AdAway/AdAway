package org.adaway.ui.update;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static org.adaway.ui.support.SupportActivity.SPONSORSHIP_LINK;
import static org.adaway.ui.support.SupportActivity.SUPPORT_LINK;
import static org.adaway.ui.support.SupportActivity.bindLink;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import org.adaway.R;
import org.adaway.databinding.UpdateActityBinding;
import org.adaway.helper.ThemeHelper;
import org.adaway.model.update.Manifest;

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
        bindListeners();
        bindManifest();
        bindProgress();
    }

    private void bindListeners() {
        this.binding.updateButton.setOnClickListener(this::startUpdate);
        bindLink(this, this.binding.updateDonateButton, SUPPORT_LINK);
        bindLink(this, this.binding.updateSponsorButton, SPONSORSHIP_LINK);
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
        this.binding.updateButton.setVisibility(GONE);
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
