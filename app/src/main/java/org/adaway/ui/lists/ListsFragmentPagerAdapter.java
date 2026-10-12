package org.adaway.ui.lists;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import org.adaway.ui.lists.type.AbstractListFragment;
import org.adaway.ui.lists.type.AllowedHostsFragment;
import org.adaway.ui.lists.type.BlockedHostsFragment;
import org.adaway.ui.lists.type.RedirectedHostsFragment;

import static org.adaway.ui.lists.ListsActivity.BLOCKED_HOSTS_TAB;
import static org.adaway.ui.lists.ListsActivity.REDIRECTED_HOSTS_TAB;
import static org.adaway.ui.lists.ListsActivity.ALLOWED_HOSTS_TAB;

/**
 * This class is a {@link FragmentStateAdapter} to store lists tab fragments.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class ListsFragmentPagerAdapter extends FragmentStateAdapter {
    /**
     * The number of fragment.
     */
    private static final int FRAGMENT_COUNT = 3;
    private final androidx.fragment.app.FragmentManager fragmentManager;

    /**
     * Constructor.
     *
     */
    ListsFragmentPagerAdapter(FragmentActivity fragmentActivity) {
        super(fragmentActivity);
        this.fragmentManager = fragmentActivity.getSupportFragmentManager();
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case BLOCKED_HOSTS_TAB:
                return new BlockedHostsFragment();
            case ALLOWED_HOSTS_TAB:
                return new AllowedHostsFragment();
            case REDIRECTED_HOSTS_TAB:
                return new RedirectedHostsFragment();
            default:
                throw new IllegalStateException("Position " + position + " is not supported.");
        }
    }

    @Override
    public int getItemCount() {
        return FRAGMENT_COUNT;
    }

    /**
     * Ensure action mode is cancelled.
     */
    void ensureActionModeCanceled() {
        for (Fragment fragment : this.fragmentManager.getFragments()) {
            if (fragment instanceof AbstractListFragment) {
                ((AbstractListFragment) fragment).ensureActionModeCanceled();
            }
        }
    }

    /**
     * Add an item into the requested fragment.
     *
     * @param position The fragment position.
     */
    void addItem(int position) {
        // FragmentStateAdapter may restore instances without calling createFragment().
        for (Fragment fragment : this.fragmentManager.getFragments()) {
            boolean matches = position == BLOCKED_HOSTS_TAB && fragment instanceof BlockedHostsFragment
                    || position == ALLOWED_HOSTS_TAB && fragment instanceof AllowedHostsFragment
                    || position == REDIRECTED_HOSTS_TAB && fragment instanceof RedirectedHostsFragment;
            if (matches && fragment.isAdded() && fragment.getView() != null) {
                ((AbstractListFragment) fragment).addItem();
                return;
            }
        }
    }
}
