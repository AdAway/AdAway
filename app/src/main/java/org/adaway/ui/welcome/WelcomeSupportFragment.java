package org.adaway.ui.welcome;

import static android.view.View.INVISIBLE;
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
        bindTelemetry();

        return this.binding.getRoot();
    }

    @Override
    protected boolean canGoNext() {
        return true;
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
            SentryLog.setEnabled(getContext(), isChecked);
        });
        if (SentryLog.isStub()) {
            this.binding.telemetryTextView.setVisibility(INVISIBLE);
            this.binding.telemetryCheckBox.setVisibility(INVISIBLE);
        }
    }
}
