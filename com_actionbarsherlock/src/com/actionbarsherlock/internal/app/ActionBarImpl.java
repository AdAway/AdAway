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
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.support.v4.app.ActionBar;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ActionMode;
import android.support.v4.view.MenuItem;
import android.view.View;
import android.widget.SpinnerAdapter;
import com.actionbarsherlock.R;
import com.actionbarsherlock.internal.view.menu.ActionMenuItemView;
import com.actionbarsherlock.internal.view.menu.MenuBuilder;
import com.actionbarsherlock.internal.view.menu.MenuItemImpl;
import com.actionbarsherlock.internal.widget.ActionBarContainer;
import com.actionbarsherlock.internal.widget.ActionBarView;

public final class ActionBarImpl extends ActionBar {
    /** Maximum action bar items in portrait mode. */
    private static final int MAX_ACTION_BAR_ITEMS_PORTRAIT = 3;

    /** Maximum action bar items in landscape mode. */
    private static final int MAX_ACTION_BAR_ITEMS_LANDSCAPE = 4;



    /** Action bar container. */
    private ActionBarContainer mContainerView;

    /** Action bar view. */
    private ActionBarView mActionBar;

    /** List of listeners to the menu visibility. */
    private final List<OnMenuVisibilityListener> mMenuListeners = new ArrayList<OnMenuVisibilityListener>();



    public ActionBarImpl(FragmentActivity activity) {
        super(activity);
    }


    // ------------------------------------------------------------------------
    // ACTION BAR SHERLOCK SUPPORT
    // ------------------------------------------------------------------------

    @Override
    protected ActionBar getPublicInstance() {
        return (mActionBar != null) ? this : null;
    }

    public void init() {
        mContainerView = (ActionBarContainer)getActivity().findViewById(R.id.abs__action_bar_container);
        mActionBar = (ActionBarView)getActivity().findViewById(R.id.abs__action_bar);

        if (mActionBar == null) {
            throw new IllegalStateException(getClass().getSimpleName() + " can only be used with a screen_*.xml layout");
        }

        final PackageManager pm = getActivity().getPackageManager();
        ActivityInfo actInfo = null;
        try {
            actInfo = pm.getActivityInfo(getActivity().getComponentName(), PackageManager.GET_ACTIVITIES);
        } catch (NameNotFoundException e) {}


        if (mActionBar.getTitle() == null) {
            if ((actInfo != null) && (actInfo.labelRes != 0)) {
                //Load label string resource from the activity entry
                mActionBar.setTitle(actInfo.labelRes);
            } else {
                //No activity label string resource and none in theme
                mActionBar.setTitle(actInfo.loadLabel(pm));
            }
        }
    }

    public void onMenuInflated(MenuBuilder menu) {
        if (mActionBar == null) {
            return;
        }

        int maxItems = MAX_ACTION_BAR_ITEMS_PORTRAIT;
        if (getActivity().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            maxItems = MAX_ACTION_BAR_ITEMS_LANDSCAPE;
        }

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
        mActionBar.removeAllItems();
        for (MenuItemImpl item : keep) {
            item.setIsShownOnActionBar(true);

            //Get a new item for this menu item
            ActionMenuItemView watsonItem = mActionBar.newItem();
            watsonItem.initialize(item, MenuBuilder.TYPE_WATSON);

            //Associate the itemview with the item so changes will be reflected
            item.setItemView(MenuBuilder.TYPE_WATSON, watsonItem);

            //Add to the action bar for display
            mActionBar.addItem(watsonItem);
        }
    }

    public void onMenuVisibilityChanged(boolean isVisible) {
        //Marshal to all listeners
        for (OnMenuVisibilityListener listener : mMenuListeners) {
            listener.onMenuVisibilityChanged(isVisible);
        }
    }

    public void setProgressBarIndeterminateVisibility(boolean visible) {
        if (mActionBar != null) {
            mActionBar.setProgressBarIndeterminateVisibility(visible);
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
        mActionBar.addTab(tab);
    }

    @Override
    public void addTab(Tab tab, boolean setSelected) {
        mActionBar.addTab(tab, setSelected);
    }

    @Override
    public void addTab(Tab tab, int position) {
        mActionBar.addTab(tab, position);
    }

    @Override
    public void addTab(ActionBar.Tab tab, int position, boolean setSelected) {
        mActionBar.addTab(tab, position, setSelected);
    }

    @Override
    public View getCustomView() {
        return mActionBar.getCustomView();
    }

    @Override
    public int getDisplayOptions() {
        return mActionBar.getDisplayOptions();
    }

    @Override
    public int getHeight() {
        return mActionBar.getHeight();
    }

    @Override
    public int getNavigationItemCount() {
        return mActionBar.getNavigationItemCount();
    }

    @Override
    public int getNavigationMode() {
        return mActionBar.getNavigationMode();
    }

    @Override
    public int getSelectedNavigationIndex() {
        return mActionBar.getSelectedNavigationIndex();
    }

    @Override
    public ActionBar.Tab getSelectedTab() {
        return mActionBar.getSelectedTab();
    }

    @Override
    public CharSequence getSubtitle() {
        return mActionBar.getSubtitle();
    }

    @Override
    public ActionBar.Tab getTabAt(int index) {
        return mActionBar.getTabAt(index);
    }

    @Override
    public int getTabCount() {
        return mActionBar.getTabCount();
    }

    @Override
    public CharSequence getTitle() {
        return mActionBar.getTitle();
    }

    @Override
    public void hide() {
        //TODO: animate
        mContainerView.setVisibility(View.GONE);
    }

    @Override
    public boolean isShowing() {
        return mContainerView.getVisibility() == View.VISIBLE;
    }

    @Override
    public ActionBar.Tab newTab() {
        return mActionBar.newTab();
    }

    @Override
    public void removeAllTabs() {
        mActionBar.removeAllTabs();
    }

    @Override
    public void removeOnMenuVisibilityListener(OnMenuVisibilityListener listener) {
        mMenuListeners.remove(listener);
    }

    @Override
    public void removeTab(ActionBar.Tab tab) {
        mActionBar.removeTab(tab);
    }

    @Override
    public void removeTabAt(int position) {
        mActionBar.removeTabAt(position);
    }

    @Override
    public void setBackgroundDrawable(Drawable d) {
        mContainerView.setBackgroundDrawable(d);
    }

    @Override
    public void setCustomView(int resId) {
        mActionBar.setCustomView(resId);
    }

    @Override
    public void setCustomView(View view) {
        mActionBar.setCustomView(view);
    }

    @Override
    public void setCustomView(View view, ActionBar.LayoutParams layoutParams) {
        mActionBar.setCustomView(view, layoutParams);
    }

    @Override
    public void setDisplayHomeAsUpEnabled(boolean showHomeAsUp) {
        mActionBar.setDisplayHomeAsUpEnabled(showHomeAsUp);
    }

    @Override
    public void setDisplayOptions(int options, int mask) {
        mActionBar.setDisplayOptions(options, mask);
    }

    @Override
    public void setDisplayOptions(int options) {
        mActionBar.setDisplayOptions(options);
    }

    @Override
    public void setDisplayShowCustomEnabled(boolean showCustom) {
        mActionBar.setDisplayShowCustomEnabled(showCustom);
    }

    @Override
    public void setDisplayShowHomeEnabled(boolean showHome) {
        mActionBar.setDisplayShowHomeEnabled(showHome);
    }

    @Override
    public void setDisplayShowTitleEnabled(boolean showTitle) {
        mActionBar.setDisplayShowTitleEnabled(showTitle);
    }

    @Override
    public void setDisplayUseLogoEnabled(boolean useLogo) {
        mActionBar.setDisplayUseLogoEnabled(useLogo);
    }

    @Override
    public void setListNavigationCallbacks(SpinnerAdapter adapter, ActionBar.OnNavigationListener callback) {
        mActionBar.setListNavigationCallbacks(adapter, callback);
    }

    @Override
    public void setNavigationMode(int mode) {
        mActionBar.setNavigationMode(mode);
    }

    @Override
    public void setSelectedNavigationItem(int position) {
        mActionBar.setSelectedNavigationItem(position);
    }

    @Override
    public void selectTab(ActionBar.Tab tab) {
        mActionBar.selectTab(tab);
    }

    @Override
    public void setSubtitle(CharSequence subtitle) {
        mActionBar.setSubtitle(subtitle);
    }

    @Override
    public void setSubtitle(int resId) {
        mActionBar.setSubtitle(resId);
    }

    @Override
    public void setTitle(CharSequence title) {
        mActionBar.setTitle(title);
    }
    @Override
    public void setTitle(int resId) {
        mActionBar.setTitle(resId);
    }

    @Override
    public void show() {
        //TODO: animate
        mContainerView.setVisibility(View.VISIBLE);
    }
}
