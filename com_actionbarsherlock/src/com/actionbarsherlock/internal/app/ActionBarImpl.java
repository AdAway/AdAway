/*
 * Copyright (C) 2011 Jake Wharton <jakewharton@gmail.com>
 * Copyright (C) 2010 Johan Nilsson <http://markupartist.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.actionbarsherlock.internal.app;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.support.v4.app.ActionBar;
import android.support.v4.view.ActionMode;
import android.support.v4.view.MenuItem;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.SpinnerAdapter;

import com.actionbarsherlock.R;
import com.actionbarsherlock.internal.view.menu.ActionMenuItemView;
import com.actionbarsherlock.internal.view.menu.MenuBuilder;
import com.actionbarsherlock.internal.view.menu.MenuItemImpl;
import com.actionbarsherlock.internal.widget.ActionBarContainer;
import com.actionbarsherlock.internal.widget.ActionBarView;

public final class ActionBarImpl extends ActionBar {
    private final Activity mActivity;

    /** Action bar container. */
    private ActionBarContainer mContainerView;

    /** Action bar view. */
    private ActionBarView mActionView;

    /** List of listeners to the menu visibility. */
    private final List<OnMenuVisibilityListener> mMenuListeners = new ArrayList<OnMenuVisibilityListener>();

    private Animation mFadeInAnimation;
    private Animation mFadeOutAnimation;



    public ActionBarImpl(Activity activity) {
        mActivity = activity;
    }


    // ------------------------------------------------------------------------
    // ACTION BAR SHERLOCK SUPPORT
    // ------------------------------------------------------------------------

    @Override
    protected ActionBar getPublicInstance() {
        return this;
    }

    public void init() {
        mActionView = (ActionBarView)mActivity.findViewById(R.id.abs__action_bar);
        mContainerView = (ActionBarContainer)mActivity.findViewById(R.id.abs__action_bar_container);

        if (mActionView == null) {
            throw new IllegalStateException(getClass().getSimpleName() + " can only be used with a screen_*.xml layout");
        }

        mFadeInAnimation = AnimationUtils.loadAnimation(mActivity, android.R.anim.fade_in);
        mFadeOutAnimation = AnimationUtils.loadAnimation(mActivity, android.R.anim.fade_out);

        if (mActionView.getTitle() == null) {
            mActionView.setTitle(mActivity.getTitle());
        }
    }

    public void onMenuInflated(MenuBuilder menu) {
        if (mActionView == null) {
            return;
        }

        final int maxItems = mActivity.getResources().getInteger(R.integer.abs__max_action_buttons);

        //Iterate and grab as many actions as we can up to maxItems honoring
        //their showAsAction values
        int ifItems = 0;
        final int count = menu.size();
        boolean showsActionItemText = menu.getShowsActionItemText();
        List<MenuItemImpl> keep = new ArrayList<MenuItemImpl>();
        for (int i = 0; i < count; i++) {
            MenuItemImpl item = (MenuItemImpl)menu.getItem(i);

            //Items without an icon or custom view are forced into the overflow menu
            if (!showsActionItemText && (item.getIcon() == null) && (item.getActionView() == null)) {
                continue;
            }
            if (showsActionItemText && ((item.getTitle() == null) || "".equals(item.getTitle()))) {
                continue;
            }

            if ((item.getShowAsAction() & MenuItem.SHOW_AS_ACTION_ALWAYS) != 0) {
                //Show always therefore add to keep list
                keep.add(item);

                if ((keep.size() > maxItems) && (ifItems > 0)) {
                    //If we have exceeded the max and there are "ifRoom" items
                    //then iterate backwards to remove one and add it to the
                    //head of the classic items list.
                    for (int j = keep.size() - 1; j >= 0; j--) {
                        if ((keep.get(j).getShowAsAction() & MenuItem.SHOW_AS_ACTION_IF_ROOM) != 0) {
                            keep.remove(j);
                            ifItems -= 1;
                            break;
                        }
                    }
                }
            } else if (((item.getShowAsAction() & MenuItem.SHOW_AS_ACTION_IF_ROOM) != 0)
                    && (keep.size() < maxItems)) {
                //"ifRoom" items are added if we have not exceeded the max.
                keep.add(item);
                ifItems += 1;
            }
        }

        //Mark items that will be shown on the action bar as such so they do
        //not show up on the activity options menu
        mActionView.removeAllItems();
        for (MenuItemImpl item : keep) {
            item.setIsShownOnActionBar(true);

            //Get a new item for this menu item
            ActionMenuItemView actionItem = mActionView.newItem();
            actionItem.initialize(item, MenuBuilder.TYPE_ACTION_BAR);

            //Associate the itemview with the item so changes will be reflected
            item.setItemView(MenuBuilder.TYPE_ACTION_BAR, actionItem);

            //Add to the action bar for display
            mActionView.addItem(actionItem);
        }
    }

    public void onMenuVisibilityChanged(boolean isVisible) {
        //Marshal to all listeners
        for (OnMenuVisibilityListener listener : mMenuListeners) {
            listener.onMenuVisibilityChanged(isVisible);
        }
    }

    public void setProgressBarIndeterminateVisibility(boolean visible) {
        if (mActionView != null) {
            mActionView.setProgressBarIndeterminateVisibility(visible);
        }
    }

    // ------------------------------------------------------------------------
    // ACTION MODE METHODS
    // ------------------------------------------------------------------------

    @Override
    protected ActionMode startActionMode(ActionMode.Callback callback) {
        throw new RuntimeException("Not implemented.");
    }

    // ------------------------------------------------------------------------
    // ACTION BAR METHODS
    // ------------------------------------------------------------------------

    @Override
    public void addOnMenuVisibilityListener(OnMenuVisibilityListener listener) {
        if (!mMenuListeners.contains(listener)) {
            mMenuListeners.add(listener);
        }
    }

    @Override
    public void addTab(Tab tab) {
        mActionView.addTab(tab);
    }

    @Override
    public void addTab(Tab tab, boolean setSelected) {
        mActionView.addTab(tab, setSelected);
    }

    @Override
    public void addTab(Tab tab, int position) {
        mActionView.addTab(tab, position);
    }

    @Override
    public void addTab(ActionBar.Tab tab, int position, boolean setSelected) {
        mActionView.addTab(tab, position, setSelected);
    }

    @Override
    public View getCustomView() {
        return mActionView.getCustomView();
    }

    @Override
    public int getDisplayOptions() {
        return mActionView.getDisplayOptions();
    }

    @Override
    public int getHeight() {
        return mActionView.getHeight();
    }

    @Override
    public int getNavigationItemCount() {
        switch (mActionView.getNavigationMode()) {
            default:
            case ActionBar.NAVIGATION_MODE_STANDARD:
                return 0;

            case ActionBar.NAVIGATION_MODE_LIST:
                SpinnerAdapter dropdownAdapter = mActionView.getDropdownAdapter();
                return (dropdownAdapter != null) ? dropdownAdapter.getCount() : 0;

            case ActionBar.NAVIGATION_MODE_TABS:
                if (mActionView.getSelectedTab() == null) {
                    return -1;
                }
                return mActionView.getTabCount();
        }
    }

    @Override
    public int getNavigationMode() {
        return mActionView.getNavigationMode();
    }

    @Override
    public int getSelectedNavigationIndex() {
        switch (mActionView.getNavigationMode()) {
            default:
            case ActionBar.NAVIGATION_MODE_STANDARD:
                return -1;

            case ActionBar.NAVIGATION_MODE_LIST:
                return mActionView.getDropdownSelectedPosition();

            case ActionBar.NAVIGATION_MODE_TABS:
                return mActionView.getSelectedTab().getPosition();
        }
    }

    @Override
    public ActionBar.Tab getSelectedTab() {
        return mActionView.getSelectedTab();
    }

    @Override
    public CharSequence getSubtitle() {
        return mActionView.getSubtitle();
    }

    @Override
    public ActionBar.Tab getTabAt(int index) {
        return mActionView.getTabAt(index);
    }

    @Override
    public int getTabCount() {
        return mActionView.getTabCount();
    }

    @Override
    public CharSequence getTitle() {
        return mActionView.getTitle();
    }

    @Override
    public void hide() {
        if (mContainerView.getVisibility() != View.GONE) {
            mContainerView.startAnimation(mFadeOutAnimation);
            mContainerView.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean isShowing() {
        return mContainerView.getVisibility() == View.VISIBLE;
    }

    @Override
    public ActionBar.Tab newTab() {
        return mActionView.newTab();
    }

    @Override
    public void removeAllTabs() {
        mActionView.removeAllTabs();
    }

    @Override
    public void removeOnMenuVisibilityListener(OnMenuVisibilityListener listener) {
        mMenuListeners.remove(listener);
    }

    @Override
    public void removeTab(ActionBar.Tab tab) {
        removeTabAt(tab.getPosition());
    }

    @Override
    public void removeTabAt(int position) {
        mActionView.removeTabAt(position);
    }

    @Override
    public void setBackgroundDrawable(Drawable d) {
        mContainerView.setBackgroundDrawable(d);
    }

    @Override
    public void setCustomView(int resId) {
        View view = LayoutInflater.from(mActivity).inflate(resId, mActionView, false);
        setCustomView(view);
    }

    @Override
    public void setCustomView(View view) {
        mActionView.setCustomNavigationView(view);
    }

    @Override
    public void setCustomView(View view, ActionBar.LayoutParams layoutParams) {
        view.setLayoutParams(layoutParams);
        mActionView.setCustomNavigationView(view);
    }

    @Override
    public void setDisplayHomeAsUpEnabled(boolean showHomeAsUp) {
        setDisplayOptions(showHomeAsUp ? ActionBar.DISPLAY_HOME_AS_UP : 0, ActionBar.DISPLAY_HOME_AS_UP);
    }

    @Override
    public void setDisplayOptions(int options) {
        mActionView.setDisplayOptions(options);
    }

    @Override
    public void setDisplayOptions(int newOptions, int mask) {
        mActionView.setDisplayOptions((mActionView.getDisplayOptions() & ~mask) | newOptions);
    }

    @Override
    public void setDisplayShowCustomEnabled(boolean showCustom) {
        setDisplayOptions(showCustom ? ActionBar.DISPLAY_SHOW_CUSTOM : 0, ActionBar.DISPLAY_SHOW_CUSTOM);
    }

    @Override
    public void setDisplayShowHomeEnabled(boolean showHome) {
        setDisplayOptions(showHome ? ActionBar.DISPLAY_SHOW_HOME : 0, ActionBar.DISPLAY_SHOW_HOME);
    }

    @Override
    public void setDisplayShowTitleEnabled(boolean showTitle) {
        setDisplayOptions(showTitle ? ActionBar.DISPLAY_SHOW_TITLE : 0, ActionBar.DISPLAY_SHOW_TITLE);
    }

    @Override
    public void setDisplayUseLogoEnabled(boolean useLogo) {
        setDisplayOptions(useLogo ? ActionBar.DISPLAY_USE_LOGO : 0, ActionBar.DISPLAY_USE_LOGO);
    }

    @Override
    public void setListNavigationCallbacks(SpinnerAdapter adapter, ActionBar.OnNavigationListener callback) {
        mActionView.setDropdownAdapter(adapter);
        mActionView.setCallback(callback);
    }

    @Override
    public void setNavigationMode(int mode) {
        mActionView.setNavigationMode(mode);
    }

    @Override
    public void setSelectedNavigationItem(int position) {
        switch (mActionView.getNavigationMode()) {
            default:
            case ActionBar.NAVIGATION_MODE_STANDARD:
                throw new IllegalStateException();

            case ActionBar.NAVIGATION_MODE_TABS:
                mActionView.getTabAt(position).select();
                break;

            case ActionBar.NAVIGATION_MODE_LIST:
                mActionView.setDropdownSelectedPosition(position);
                break;
        }
    }

    @Override
    public void selectTab(ActionBar.Tab tab) {
        mActionView.selectTab(tab);
    }

    @Override
    public void setSubtitle(CharSequence subtitle) {
        mActionView.setSubtitle(subtitle);
    }

    @Override
    public void setSubtitle(int resId) {
        mActionView.setSubtitle(resId);
    }

    @Override
    public void setTitle(CharSequence title) {
        mActionView.setTitle(title);
    }
    @Override
    public void setTitle(int resId) {
        mActionView.setTitle(resId);
    }

    @Override
    public void show() {
        if (mContainerView.getVisibility() != View.VISIBLE) {
            mContainerView.startAnimation(mFadeInAnimation);
            mContainerView.setVisibility(View.VISIBLE);
        }
    }
}
