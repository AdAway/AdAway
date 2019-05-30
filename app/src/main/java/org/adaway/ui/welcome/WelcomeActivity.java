package org.adaway.ui.welcome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;

import org.adaway.R;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

/**
 * This class is a welcome activity to run first time setup on the user device..
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class WelcomeActivity extends AppCompatActivity implements WelcomeNavigable {
    private WelcomePagerAdapter pagerAdapter;
    private ViewPager2 viewPager;
    private MaterialButton backButton;
    private MaterialButton nextButton;
    private ImageView[] dotImageViews;

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
        this.pagerAdapter = new WelcomePagerAdapter(this.getSupportFragmentManager());
        this.viewPager = findViewById(R.id.view_pager);
        this.viewPager.setAdapter(this.pagerAdapter);
        this.viewPager.setUserInputEnabled(false);
    }

    private void bindNextButton() {
        this.nextButton = findViewById(R.id.next_button);
        this.nextButton.setOnClickListener(v -> {
            int currentItem = this.viewPager.getCurrentItem();
            int count = this.pagerAdapter.getItemCount();
            if (currentItem < count - 1) {
                this.viewPager.setCurrentItem(currentItem + 1);
                allowBack();
                blockNext();
            }
        });
    }

    private void bindBackButton() {
        this.backButton = findViewById(R.id.back_button);
        this.backButton.setOnClickListener(v -> {
            int currentItem = this.viewPager.getCurrentItem();
            if (currentItem > 0) {
                this.viewPager.setCurrentItem(currentItem - 1);
                if (currentItem <= 1) {
                    blockBack();
                }
                allowNext();
            }
        });
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
            this.viewPager.setCurrentItem(currentItem - 1);
        }
    }

    @Override
    public void allowBack() {
        showButton(this.backButton);
    }

    @Override
    public void blockBack() {
        hideButton(this.backButton);
    }

    @Override
    public void allowNext() {
        showButton(this.nextButton);
        this.nextButton.setText(R.string.welcome_next_button);
    }

    @Override
    public void blockNext() {
        hideButton(this.nextButton);
    }

    @Override
    public void allowFinish() {
        showButton(this.nextButton);
        this.nextButton.setText(R.string.welcome_finish_button);
    }

    private void showButton(MaterialButton button) {
        if (button.getVisibility() == VISIBLE) {
            return;
        }
        button.setAlpha(0F);
        button.setVisibility(VISIBLE);
        button.animate()
                .alpha(1F)
                .setListener(null);
    }

    private void hideButton(MaterialButton button) {
        if (button.getVisibility() != VISIBLE) {
            return;
        }
        button.animate()
                .alpha(0F)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        button.setVisibility(INVISIBLE);
                    }
                });
    }
}
