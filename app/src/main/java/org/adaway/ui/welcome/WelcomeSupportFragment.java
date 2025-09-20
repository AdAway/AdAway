package org.adaway.ui.welcome;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static org.adaway.ui.support.SupportActivity.SPONSORSHIP_LINK;
import static org.adaway.ui.support.SupportActivity.SUPPORT_LINK;
import static org.adaway.ui.support.SupportActivity.animateHeart;
import static org.adaway.ui.support.SupportActivity.bindLink;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.adaway.databinding.WelcomeSupportLayoutBinding;
import org.adaway.helper.PreferenceHelper;
import org.adaway.util.log.SentryLog;

/**
 * This class is a fragment to inform user how to support the application development.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class WelcomeSupportFragment extends WelcomeFragment {
    private WelcomeSupportLayoutBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        this.binding = WelcomeSupportLayoutBinding.inflate(inflater, container, false);

        animateHeart(this.binding.headerImageView);
        bindSupport();
        customizeSecondOption();

        return this.binding.getRoot();
    }

    @Override
    protected boolean canGoNext() {
        return true;
    }

    private void customizeSecondOption() {
        if (SentryLog.isStub()) {
            showAndBindSponsorship();
        } else {
            bindTelemetry();
        }
    }

    private void bindSupport() {
        Context context = requireContext();
        bindLink(context, this.binding.headerImageView, SUPPORT_LINK);
        bindLink(context, this.binding.headerTextView, SUPPORT_LINK);
        bindLink(context, this.binding.paypalCardView, SUPPORT_LINK);
    }

    private void bindTelemetry() {
        this.binding.telemetryCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PreferenceHelper.setTelemetryEnabled(requireContext(), isChecked);
            SentryLog.setEnabled(requireActivity().getApplication(), isChecked);
        });
    }

    private void showAndBindSponsorship() {
        this.binding.telemetryTextView.setVisibility(INVISIBLE);
        this.binding.telemetryCheckBox.setVisibility(INVISIBLE);
        this.binding.sponsorshipCardView.setVisibility(VISIBLE);
        bindLink(requireContext(), this.binding.sponsorshipCardView, SPONSORSHIP_LINK);
    }
}
