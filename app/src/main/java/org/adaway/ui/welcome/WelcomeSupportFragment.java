package org.adaway.ui.welcome;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.adaway.databinding.WelcomeSupportLayoutBinding;

import static android.content.Intent.ACTION_VIEW;
import static org.adaway.ui.support.SupportActivity.SUPPORT_LINK;
import static org.adaway.ui.support.SupportActivity.animateHeart;

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

        return this.binding.getRoot();
    }

    @Override
    protected boolean canGoNext() {
        return true;
    }

    private void bindSupport() {
        View.OnClickListener listener = v -> {
            Intent browserIntent = new Intent(ACTION_VIEW, Uri.parse(SUPPORT_LINK));
            startActivity(browserIntent);
        };
        this.binding.headerImageView.setOnClickListener(listener);
        this.binding.headerTextView.setOnClickListener(listener);
        this.binding.paypalCardView.setOnClickListener(listener);
    }
}
