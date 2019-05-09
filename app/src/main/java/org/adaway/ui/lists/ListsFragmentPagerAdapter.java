package org.adaway.ui.lists;

import android.app.Activity;

import androidx.annotation.NonNull;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.PagerAdapter;

import android.view.ViewGroup;

import org.adaway.R;

import static org.adaway.ui.lists.ListsFragment.BLACKLIST_TAB;
import static org.adaway.ui.lists.ListsFragment.REDIRECTION_TAB;
import static org.adaway.ui.lists.ListsFragment.WHITELIST_TAB;

/**
 * This class is a {@link PagerAdapter} to store lists tab fragments.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class ListsFragmentPagerAdapter extends FragmentStatePagerAdapter {
    /**
     * The number of fragment.
     */
    private static final int FRAGMENT_COUNT = 3;
    /**
     * The context activity.
     */
    private final Activity activity;
    /**
     * The blacklist fragment (<code>null</code> until first retrieval).
     */
    private AbstractListFragment blacklistFragment;
    /**
     * The whitelist fragment (<code>null</code> until first retrieval).
     */
    private AbstractListFragment whitelistFragment;
    /**
     * The redirection list fragment (<code>null</code> until first retrieval).
     */
    private AbstractListFragment redirectionListFragment;

    /**
     * Constructor.
     *
     * @param activity        The context activity.
     * @param fragmentManager The fragment manager.
     */
    ListsFragmentPagerAdapter(Activity activity, FragmentManager fragmentManager) {
        super(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        this.activity = activity;
    }

    /**
     * Ensure action mode is cancelled.
     */
    void ensureActionModeCanceled() {
        if (this.blacklistFragment != null) {
            this.blacklistFragment.ensureActionModeCanceled();
        }
        if (this.whitelistFragment != null) {
            this.whitelistFragment.ensureActionModeCanceled();
        }
        if (this.redirectionListFragment != null) {
            this.redirectionListFragment.ensureActionModeCanceled();
        }
    }

    /**
     * Add an item into the requested fragment.
     *
     * @param position The fragment position.
     */
    void addItem(int position) {
        switch (position) {
            case BLACKLIST_TAB:
                if (this.blacklistFragment != null) {
                    this.blacklistFragment.addItem();
                }
                break;
            case WHITELIST_TAB:
                if (this.whitelistFragment != null) {
                    this.whitelistFragment.addItem();
                }
                break;
            case REDIRECTION_TAB:
                if (this.redirectionListFragment != null) {
                    this.redirectionListFragment.addItem();
                }
                break;
        }
    }

    @NonNull
    @Override
    public Fragment getItem(int position) {
        // Check fragment position
        switch (position) {
            case WHITELIST_TAB:
                return new WhiteListFragment();
            case REDIRECTION_TAB:
                return new RedirectionListFragment();
            default:
            case BLACKLIST_TAB:
                return new BlackListFragment();
        }
    }

    @Override
    public CharSequence getPageTitle(int position) {
        // Check fragment position
        switch (position) {
            case BLACKLIST_TAB:
                return this.activity.getString(R.string.lists_tab_blocked);
            case WHITELIST_TAB:
                return this.activity.getString(R.string.lists_tab_allowed);
            case REDIRECTION_TAB:
                return this.activity.getString(R.string.lists_tab_redirected);
            default:
                return null;
        }
    }

    // More explanation here: https://stackoverflow.com/a/29288093/1538096
    @Override
    @NonNull
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        AbstractListFragment fragment = (AbstractListFragment) super.instantiateItem(container, position);
        switch (position) {
            case BLACKLIST_TAB:
                this.blacklistFragment = fragment;
                break;
            case WHITELIST_TAB:
                this.whitelistFragment = fragment;
                break;
            case REDIRECTION_TAB:
                this.redirectionListFragment = fragment;
                break;
        }
        return fragment;
    }

    @Override
    public int getCount() {
        return FRAGMENT_COUNT;
    }
}
