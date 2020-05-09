package org.adaway.ui.welcome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;

import org.adaway.R;
import org.adaway.ui.home.HomeActivity;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

/**
 * This class is a welcome activity to run first time setup on the user device.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class WelcomeActivity extends AppCompatActivity implements WelcomeNavigable {
    private WelcomePagerAdapter pagerAdapter;
    private ViewPager2 viewPager;
    private MaterialButton backButton;
    private MaterialButton nextButton;
    private ImageView[] dotImageViews;

    public static void showView(View view) {
        ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(view, View.ALPHA, 1F);
        objectAnimator.setAutoCancel(true);
        objectAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                view.setVisibility(VISIBLE);
            }
        });
        objectAnimator.start();
    }

    public static void hideView(View view) {
        ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(view, View.ALPHA, 0F);
        objectAnimator.setAutoCancel(true);
        objectAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.setVisibility(INVISIBLE);
            }
        });
        objectAnimator.start();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.welcome_activity);
        buildPager();
        bindBackButton();
        bindNextButton();
        bindDots();
    }

    private void buildPager() {
        this.pagerAdapter = new WelcomePagerAdapter(this);
        this.viewPager = findViewById(R.id.view_pager);
        this.viewPager.setAdapter(this.pagerAdapter);
        this.viewPager.setUserInputEnabled(false);
    }

    private void bindNextButton() {
        this.nextButton = findViewById(R.id.next_button);
        this.nextButton.setOnClickListener(view -> goNext());
    }

    private void bindBackButton() {
        this.backButton = findViewById(R.id.back_button);
        this.backButton.setOnClickListener(view -> goBack());
    }

    private void bindDots() {
        this.dotImageViews = new ImageView[]{
                findViewById(R.id.dot1ImageView),
                findViewById(R.id.dot2ImageView),
                findViewById(R.id.dot3ImageView)
        };
        highlightDot(this.viewPager.getCurrentItem());
        this.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                highlightDot(position);
            }
        });
    }

    private void highlightDot(int position) {
        for (int index = 0; index < dotImageViews.length; index++) {
            if (index == position) {
                dotImageViews[index].setImageResource(R.drawable.dot);
                dotImageViews[index].animate().alpha(0.7F).scaleX(1.2F).scaleY(1.2F);
            } else {
                dotImageViews[index].setImageResource(R.drawable.dot_outline);
                dotImageViews[index].animate().alpha(0.5F).scaleX(1F).scaleY(1F);
            }
        }
    }

    @Override
    public void onBackPressed() {
        int currentItem = this.viewPager.getCurrentItem();
        if (currentItem == 0) {
            super.onBackPressed();
        } else {
            goBack();
        }
    }

    @Override
    public void allowNext() {
        if (this.viewPager.getCurrentItem() == this.pagerAdapter.getItemCount() - 1) {
            this.nextButton.setText(R.string.welcome_finish_button);
        } else {
            this.nextButton.setText(R.string.welcome_next_button);
        }
        showView(this.nextButton);
    }

    @Override
    public void blockNext() {
        hideView(this.nextButton);
    }

    private void allowBack() {
        showView(this.backButton);
    }

    private void blockBack() {
        hideView(this.backButton);
    }

    private void goNext() {
        int currentItem = this.viewPager.getCurrentItem();
        int count = this.pagerAdapter.getItemCount();
        if (currentItem >= count - 1) {
            startHomeActivity();
            return;
        }
        currentItem++;
        this.viewPager.setCurrentItem(currentItem);
        allowBack();
        if (this.pagerAdapter.createFragment(currentItem).canGoNext()) {
            allowNext();
        } else {
            blockNext();
        }
    }

    private void goBack() {
        int currentItem = this.viewPager.getCurrentItem();
        if (currentItem == 0) {
            return;
        }
        this.viewPager.setCurrentItem(currentItem - 1);
        if (currentItem <= 1) {
            blockBack();
        }
        allowNext();
    }

    private void startHomeActivity() {
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }
}
