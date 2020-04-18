package org.adaway.ui.welcome;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import org.adaway.R;

import static android.content.Intent.ACTION_VIEW;
import static org.adaway.ui.support.SupportActivity.SUPPORT_LINK;
import static org.adaway.ui.support.SupportActivity.animateHeart;

/**
 * This class is a fragment to inform user how to support the application development.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class WelcomeSupportFragment extends WelcomeFragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.welcome_support_layout, container, false);

        animateHeart(view);
        bindSupport(view);

        return view;
    }

    @Override
    protected boolean canGoNext() {
        return true;
    }

    private void bindSupport(View view) {
        ImageView headerImageView = view.findViewById(R.id.headerImageView);
        TextView headerTextView = view.findViewById(R.id.headerTextView);
        CardView supportCardView = view.findViewById(R.id.paypalCardView);

        View.OnClickListener listener = v -> {
            Intent browserIntent = new Intent(ACTION_VIEW, Uri.parse(SUPPORT_LINK));
            startActivity(browserIntent);
        };

        headerImageView.setOnClickListener(listener);
        headerTextView.setOnClickListener(listener);
        supportCardView.setOnClickListener(listener);
    }
}
