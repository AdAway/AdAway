package org.adaway.ui.welcome;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class WelcomePagerAdapter extends FragmentStateAdapter {
    private final WelcomeNavigable navigable;

    WelcomePagerAdapter(WelcomeActivity activity) {
        super(activity.getSupportFragmentManager());
        this.navigable = activity;
    }

    @NonNull
    @Override
    public Fragment getItem(int position) {
        switch (position) {
            case 0:
                WelcomeMethodFragment fragment = new WelcomeMethodFragment();
                fragment.setNavigable(this.navigable);
                return fragment;
        }
        return new WelcomeMethodFragment();
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
