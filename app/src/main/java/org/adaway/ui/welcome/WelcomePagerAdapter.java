package org.adaway.ui.welcome;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

/**
 * This class is a pager adapter to create setup step fragments.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class WelcomePagerAdapter extends FragmentStateAdapter {
    private final WelcomeMethodFragment welcomeMethodFragment;
    private final WelcomeSyncFragment welcomeSyncFragment;
    private final WelcomeSupportFragment welcomeSupportFragment;

    WelcomePagerAdapter(@NonNull FragmentActivity fragmentManager) {
        super(fragmentManager);
        this.welcomeMethodFragment = new WelcomeMethodFragment();
        this.welcomeSyncFragment = new WelcomeSyncFragment();
        this.welcomeSupportFragment = new WelcomeSupportFragment();
    }

    @NonNull
    @Override
    public WelcomeFragment createFragment(int position) {
        switch (position) {
            case 0:
                return this.welcomeMethodFragment;
            case 1:
                return this.welcomeSyncFragment;
            case 2:
                return this.welcomeSupportFragment;
            default:
                throw new IllegalStateException("Position " + position + " is not supported.");
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
