package org.adaway.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.view.View;

import static android.view.View.ALPHA;
import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

/**
 * This class is an utility class to animate views.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public final class Animations {

    private Animations() {

    }

    /**
     * Animate view to be shown.
     *
     * @param view The view to animate.
     */
    public static void showView(View view) {
        ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(view, ALPHA, 1F);
        objectAnimator.setAutoCancel(true);
        objectAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                view.setVisibility(VISIBLE);
            }
        });
        objectAnimator.start();
    }

    /**
     * Animate view to be hidden.
     *
     * @param view The view to animate.
     */
    public static void hideView(View view) {
        ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(view, ALPHA, 0F);
        objectAnimator.setAutoCancel(true);
        objectAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.setVisibility(INVISIBLE);
            }
        });
        objectAnimator.start();
    }

    /**
     * Animate view to be removed.
     *
     * @param view The view to animate.
     */
    public static void removeView(View view) {
        ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(view, ALPHA, 0F);
        objectAnimator.setAutoCancel(true);
        objectAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.setVisibility(GONE);
            }
        });
        objectAnimator.start();
    }

    /**
     * Immediately set view to shown state.
     *
     * @param view The view to set.
     */
    public static void setShown(View view) {
        view.setVisibility(VISIBLE);
        view.setAlpha(1f);
    }

    /**
     * Immediately set view to hidden state.
     *
     * @param view The view to set.
     */
    public static void setHidden(View view) {
        view.setVisibility(INVISIBLE);
        view.setAlpha(0f);
    }

    /**
     * Immediately set view to gone state.
     *
     * @param view The view to set.
     */
    public static void setRemoved(View view) {
        view.setVisibility(GONE);
        view.setAlpha(0f);
    }
}
