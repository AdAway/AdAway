package org.adaway.ui.welcome;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class WelcomePagerAdapter extends FragmentStateAdapter {

    WelcomePagerAdapter(@NonNull FragmentActivity fragmentManager) {
        super(fragmentManager);
    }

    @NonNull
    @Override
    public Fragment getItem(int position) {
        switch (position) {
            case 0:
                return new WelcomeMethodFragment();
        }
        return new WelcomeMethodFragment();
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
