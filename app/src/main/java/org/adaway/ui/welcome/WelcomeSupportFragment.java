package org.adaway.ui.welcome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
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

import static org.adaway.ui.next.NextActivity.SUPPORT_LINK;

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
        CardView supportCardView = view.findViewById(R.id.support_card_view);

        View.OnClickListener listener = v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_LINK));
            startActivity(browserIntent);
        };

        headerImageView.setOnClickListener(listener);
        headerTextView.setOnClickListener(listener);
        supportCardView.setOnClickListener(listener);
    }

    private void animateHeart(View view) {
        ImageView headerImageView = view.findViewById(R.id.headerImageView);

        PropertyValuesHolder growScaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1F, 1.2F);
        PropertyValuesHolder growScaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1F, 1.2F);
        Animator growAnimator = ObjectAnimator.ofPropertyValuesHolder(headerImageView, growScaleX, growScaleY);
        growAnimator.setDuration(200);
        growAnimator.setStartDelay(2000);

        PropertyValuesHolder shrinkScaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.2F, 1F);
        PropertyValuesHolder shrinkScaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.2F, 1F);
        Animator shrinkAnimator = ObjectAnimator.ofPropertyValuesHolder(headerImageView, shrinkScaleX, shrinkScaleY);
        growAnimator.setDuration(400);

        AnimatorSet animationSet = new AnimatorSet();
        animationSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animationSet.start();
            }
        });
        animationSet.playSequentially(growAnimator, shrinkAnimator);
        animationSet.start();
    }
}
