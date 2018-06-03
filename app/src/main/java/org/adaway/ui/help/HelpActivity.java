/*
 * Copyright (C) 2011-2012 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This file is part of AdAway.
 * 
 * AdAway is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AdAway is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AdAway.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.adaway.ui.help;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

import org.adaway.R;
import org.adaway.helper.ThemeHelper;
import org.adaway.ui.MainActivity;
import org.adaway.util.Constants;
import org.sufficientlysecure.donations.DonationsFragment;

import java.util.ArrayList;

public class HelpActivity extends AppCompatActivity {
    /**
     * PayPal
     */
    private static final String PAYPAL_USER = "android@schuermann.eu";
    private static final String PAYPAL_CURRENCY_CODE = "EUR";
    /**
     * Flattr
     */
    private static final String FLATTR_PROJECT_URL = "http://code.google.com/p/ad-away/";
    // without http:// !
    private static final String FLATTR_URL = "flattr.com/thing/369138/AdAway-Ad-blocker-for-Android";
    /**
     * Bitcoin
     */
    private static final String BITCOIN = "173kZxbkKuvnF5fa5b7t21kqU5XfEvvwTs";
    ViewPager mViewPager;
    TabsAdapter mTabsAdapter;

    /**
     * Menu Items
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // app icon in Action Bar clicked; go home
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);

        mViewPager = new ViewPager(this);
        mViewPager.setId(R.id.all); // TODO Set proper id

        setContentView(mViewPager);
        ActionBar bar = getSupportActionBar();
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        bar.setDisplayShowTitleEnabled(true);
        bar.setDisplayHomeAsUpEnabled(true);

        mTabsAdapter = new TabsAdapter(this, mViewPager);

        Bundle faqBundle = new Bundle();
        faqBundle.putInt(HelpFragmentHtml.ARG_HTML_FILE, R.raw.help_faq);
        mTabsAdapter.addTab(bar.newTab().setText(getString(R.string.help_tab_faq)),
                HelpFragmentHtml.class, faqBundle);

        Bundle problemsBundle = new Bundle();
        problemsBundle.putInt(HelpFragmentHtml.ARG_HTML_FILE, R.raw.help_problems);
        mTabsAdapter.addTab(bar.newTab().setText(getString(R.string.help_tab_problems)),
                HelpFragmentHtml.class, problemsBundle);

        Bundle sOnSOffBundle = new Bundle();
        sOnSOffBundle.putInt(HelpFragmentHtml.ARG_HTML_FILE, R.raw.help_s_on_s_off);
        mTabsAdapter.addTab(bar.newTab().setText(getString(R.string.help_tab_s_on_s_off)),
                HelpFragmentHtml.class, sOnSOffBundle);

        Bundle donationsArgs = new Bundle();
        donationsArgs.putBoolean(DonationsFragment.ARG_DEBUG, Constants.isDebugEnabled());
        donationsArgs.putBoolean(DonationsFragment.ARG_FLATTR_ENABLED, true);
        donationsArgs.putString(DonationsFragment.ARG_FLATTR_PROJECT_URL, FLATTR_PROJECT_URL);
        donationsArgs.putString(DonationsFragment.ARG_FLATTR_URL, FLATTR_URL);

        donationsArgs.putBoolean(DonationsFragment.ARG_PAYPAL_ENABLED, true);
        donationsArgs.putString(DonationsFragment.ARG_PAYPAL_CURRENCY_CODE, PAYPAL_CURRENCY_CODE);
        donationsArgs.putString(DonationsFragment.ARG_PAYPAL_USER, PAYPAL_USER);
        donationsArgs.putString(DonationsFragment.ARG_PAYPAL_ITEM_NAME, getString(R.string.help_donation_paypal_item));

        donationsArgs.putBoolean(DonationsFragment.ARG_BITCOIN_ENABLED, true);
        donationsArgs.putString(DonationsFragment.ARG_BITCOIN_ADDRESS, BITCOIN);

        mTabsAdapter.addTab(bar.newTab().setText(getString(R.string.help_tab_donate)),
                DonationsFragment.class, donationsArgs);

        Bundle changelogArgs = new Bundle();
        changelogArgs.putInt(HelpFragmentHtml.ARG_HTML_FILE, R.raw.help_changelog);
        mTabsAdapter.addTab(bar.newTab().setText(getString(R.string.help_tab_changelog)),
                HelpFragmentHtml.class, changelogArgs);

        mTabsAdapter.addTab(bar.newTab().setText(getString(R.string.help_tab_about)),
                HelpFragmentAbout.class, null);
    }

    public static class TabsAdapter extends FragmentPagerAdapter implements ActionBar.TabListener,
            ViewPager.OnPageChangeListener {
        private final Context mContext;
        private final ActionBar mActionBar;
        private final ViewPager mViewPager;
        private final ArrayList<TabInfo> mTabs = new ArrayList<>();

        public TabsAdapter(AppCompatActivity activity, ViewPager pager) {
            super(activity.getSupportFragmentManager());
            mContext = activity;
            mActionBar = activity.getSupportActionBar();
            mViewPager = pager;
            mViewPager.setAdapter(this);
            mViewPager.setOnPageChangeListener(this);
        }

        public void addTab(ActionBar.Tab tab, Class<?> clss, Bundle args) {
            TabInfo info = new TabInfo(clss, args);
            tab.setTag(info);
            tab.setTabListener(this);
            mTabs.add(info);
            mActionBar.addTab(tab);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mTabs.size();
        }

        @Override
        public Fragment getItem(int position) {
            TabInfo info = mTabs.get(position);
            return Fragment.instantiate(mContext, info.clss.getName(), info.args);
        }

        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        public void onPageSelected(int position) {
            mActionBar.setSelectedNavigationItem(position);
        }

        public void onPageScrollStateChanged(int state) {
        }

        public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft) {
            Object tag = tab.getTag();
            for (int i = 0; i < mTabs.size(); i++) {
                if (mTabs.get(i) == tag) {
                    mViewPager.setCurrentItem(i);
                }
            }
        }

        public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft) {
        }

        public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft) {
        }

        static final class TabInfo {
            private final Class<?> clss;
            private final Bundle args;

            TabInfo(Class<?> _class, Bundle _args) {
                clss = _class;
                args = _args;
            }
        }
    }
}
