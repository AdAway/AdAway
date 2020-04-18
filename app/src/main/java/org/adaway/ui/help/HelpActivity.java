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

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.adaway.R;
import org.adaway.helper.ThemeHelper;

public class HelpActivity extends AppCompatActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.help_activity);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        ViewPager2 viewPager = findViewById(R.id.pager);
        viewPager.setAdapter(new TabsAdapter(this));

        TabLayout tabLayout = findViewById(R.id.tabLayout);

        new TabLayoutMediator(
                tabLayout,
                viewPager,
                (tab, position) -> tab.setText(getTabName(position))
        ).attach();
    }

    private @StringRes
    int getTabName(int position) {
        switch (position) {
            case 0:
                return R.string.help_tab_faq;
            case 1:
                return R.string.help_tab_problems;
            case 2:
                return R.string.help_tab_s_on_s_off;
            default:
                throw new IllegalStateException("Position " + position + " is not supported.");
        }
    }

    private static class TabsAdapter extends FragmentStateAdapter {
        private final Fragment faqFragment = HelpFragmentHtml.newInstance(R.raw.help_faq);
        private final Fragment problemsFragment = HelpFragmentHtml.newInstance(R.raw.help_problems);
        private final Fragment sonSofFragment = HelpFragmentHtml.newInstance(R.raw.help_s_on_s_off);

        TabsAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return this.faqFragment;
                case 1:
                    return this.problemsFragment;
                case 2:
                    return this.sonSofFragment;
                default:
                    throw new IllegalStateException("Position " + position + " is not supported.");
            }
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }
}
