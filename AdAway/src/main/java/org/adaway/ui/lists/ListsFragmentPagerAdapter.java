package org.adaway.ui.lists;

import android.app.Activity;
import android.content.res.Configuration;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.PagerAdapter;
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
     * The blacklist fragment.
     */
    private AddItemActionListener blacklistFragment;
    /**
     * The whitelist fragment.
     */
    private AddItemActionListener whitelistFragment;
    /**
     * The redirection list fragment.
     */
    private AddItemActionListener redirectionListFragment;

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
     * Add an item into the requested fragment.
     *
     * @param position The fragment position.
     */
    void addItem(int position) {
        switch (position) {
            case BLACKLIST_FRAGMENT_INDEX:
                this.blacklistFragment.addItem();
                break;
            case WHITELIST_FRAGMENT_INDEX:
                this.whitelistFragment.addItem();
                break;
            case REDIRECTION_FRAGMENT_INDEX:
                this.redirectionListFragment.addItem();
                break;
        }
    }

    @Override
    public Fragment getItem(int position) {
        // Check fragment position
        switch (position) {
            case BLACKLIST_FRAGMENT_INDEX:
                return new BlacklistFragment();
            case WHITELIST_FRAGMENT_INDEX:
                return new WhitelistFragment();
            case REDIRECTION_FRAGMENT_INDEX:
                return new RedirectionListFragment();
            default:
                return null;
        }
    }

    @Override
    public CharSequence getPageTitle(int position) {
        // Get configuration
        Configuration configuration = this.activity.getResources().getConfiguration();
        // Check screen size
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                || configuration.screenLayout == Configuration.SCREENLAYOUT_SIZE_XLARGE) {
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
        } else {
            // Check fragment position
            switch (position) {
                case BLACKLIST_FRAGMENT_INDEX:
                    return this.activity.getString(R.string.lists_tab_blacklist_short);
                case WHITELIST_FRAGMENT_INDEX:
                    return this.activity.getString(R.string.lists_tab_whitelist_short);
                case REDIRECTION_FRAGMENT_INDEX:
                    return this.activity.getString(R.string.lists_tab_redirection_list_short);
                default:
                    return null;
            }
        }
    }

    // More explanation here: https://stackoverflow.com/a/29288093/1538096
    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        AddItemActionListener fragment = (AddItemActionListener) super.instantiateItem(container, position);
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

    /**
     * This interface describe the listener of the add item floating action button.
     *
     * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
     */
    interface AddItemActionListener {
        /**
         * Notify the add item action.
         */
        void addItem();
    }
}
