package org.adaway.ui.update;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.content.Intent;
import android.net.Uri;
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
    /** PayPal support link (inlined from deleted SupportActivity). */
    private static final Uri SUPPORT_LINK = Uri.parse("https://paypal.me/BruceBUJON");
    /** GitHub sponsors link (inlined from deleted SupportActivity). */
    private static final Uri SPONSORSHIP_LINK = Uri.parse("https://github.com/sponsors/PerfectSlayer");

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
        this.binding.updateDonateButton.setOnClickListener(
                v -> startActivity(new Intent(Intent.ACTION_VIEW, SUPPORT_LINK)));
        this.binding.updateSponsorButton.setOnClickListener(
                v -> startActivity(new Intent(Intent.ACTION_VIEW, SPONSORSHIP_LINK)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        this.updateViewModel.resumePendingInstall();
    }

    private void bindManifest() {
        this.updateViewModel.getAppManifest().observe(this, manifest -> {
            if (manifest == null) {
                markUnavailable();
            } else if (manifest.updateAvailable) {
                showUpdate(manifest);
            } else {
                markUpToDate(manifest);
            }
        });
    }

    private void bindProgress() {
        this.updateViewModel.getDownloadProgress().observe(this, progress -> {
            if (progress == null) {
                Manifest manifest = this.updateViewModel.getAppManifest().getValue();
                this.binding.updateButton.setVisibility(
                        manifest != null && manifest.updateAvailable && !manifest.isExpired()
                                ? VISIBLE : GONE);
                this.binding.downloadProgressBar.setVisibility(GONE);
                this.binding.downloadProgressBar.setProgress(0);
                this.binding.progressTextView.setText(null);
                return;
            }
            this.binding.updateButton.setVisibility(INVISIBLE);
            this.binding.downloadProgressBar.setVisibility(VISIBLE);
            this.binding.downloadProgressBar.setProgress(progress.getProgress(), true);
            this.binding.progressTextView.setText(progress.format(this));
        });
    }

    private void markUpToDate(Manifest manifest) {
        this.binding.headerTextView.setText(R.string.update_up_to_date_header);
        this.binding.updateButton.setVisibility(GONE);
        this.binding.changelogTitleTextView.setVisibility(VISIBLE);
        this.binding.changelogTextView.setText(manifest.changelog);
    }

    private void showUpdate(Manifest manifest) {
        this.binding.headerTextView.setText(R.string.update_update_available_header);
        this.binding.updateButton.setVisibility(VISIBLE);
        this.binding.changelogTitleTextView.setVisibility(VISIBLE);
        this.binding.changelogTextView.setText(manifest.changelog);
    }

    private void markUnavailable() {
        this.binding.headerTextView.setText(R.string.update_unavailable_header);
        this.binding.updateButton.setVisibility(GONE);
        this.binding.changelogTitleTextView.setVisibility(GONE);
        this.binding.changelogTextView.setText(null);
    }

    private void startUpdate(View view) {
        this.binding.updateButton.setVisibility(INVISIBLE);
        this.binding.downloadProgressBar.setVisibility(VISIBLE);
        this.binding.downloadProgressBar.setProgress(0);
        this.binding.progressTextView.setText(null);
        this.updateViewModel.update();
    }
}
