package org.adaway.ui.lists;

import android.app.Activity;

import androidx.annotation.NonNull;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.PagerAdapter;

import android.view.ViewGroup;

import org.adaway.R;

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
     * The blacklist fragment index.
     */
    private static final int BLACKLIST_FRAGMENT_INDEX = 0;
    /**
     * The whitelist fragment index.
     */
    private static final int WHITELIST_FRAGMENT_INDEX = 1;
    /**
     * The redirection fragment index.
     */
    private static final int REDIRECTION_FRAGMENT_INDEX = 2;
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
        super(fragmentManager);
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
            case BLACKLIST_FRAGMENT_INDEX:
                if (this.blacklistFragment != null) {
                    this.blacklistFragment.addItem();
                }
                break;
            case WHITELIST_FRAGMENT_INDEX:
                if (this.whitelistFragment != null) {
                    this.whitelistFragment.addItem();
                }
                break;
            case REDIRECTION_FRAGMENT_INDEX:
                if (this.redirectionListFragment != null) {
                    this.redirectionListFragment.addItem();
                }
                break;
        }
    }

    @Override
    public Fragment getItem(int position) {
        // Check fragment position
        switch (position) {
            case BLACKLIST_FRAGMENT_INDEX:
                return new BlackListFragment();
            case WHITELIST_FRAGMENT_INDEX:
                return new WhiteListFragment();
            case REDIRECTION_FRAGMENT_INDEX:
                return new RedirectionListFragment();
            default:
                return null;
        }
    }

    @Override
    public CharSequence getPageTitle(int position) {
        // Check fragment position
        switch (position) {
            case BLACKLIST_FRAGMENT_INDEX:
                return this.activity.getString(R.string.lists_tab_blacklist);
            case WHITELIST_FRAGMENT_INDEX:
                return this.activity.getString(R.string.lists_tab_whitelist);
            case REDIRECTION_FRAGMENT_INDEX:
                return this.activity.getString(R.string.lists_tab_redirection_list);
            default:
                return null;
        }
    }

    // More explanation here: https://stackoverflow.com/a/29288093/1538096
    @Override
    @NonNull
    public Object instantiateItem(ViewGroup container, int position) {
        AbstractListFragment fragment = (AbstractListFragment) super.instantiateItem(container, position);
        switch (position) {
            case BLACKLIST_FRAGMENT_INDEX:
                this.blacklistFragment = fragment;
                break;
            case WHITELIST_FRAGMENT_INDEX:
                this.whitelistFragment = fragment;
                break;
            case REDIRECTION_FRAGMENT_INDEX:
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
