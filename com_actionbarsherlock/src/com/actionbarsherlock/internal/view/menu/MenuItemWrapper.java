/*
 * Copyright (C) 2006 The Android Open Source Project
 * Copyright (C) 2011 Jake Wharton
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

package com.actionbarsherlock.internal.view.menu;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.support.v4.view.MenuItem;
import android.support.v4.view.SubMenu;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;

/**
 * <p>Interface for direct access to a previously created menu item.</p>
 *
 * <p>An Item is returned by calling one of the {@link Menu#add(int)}
 * methods.</p>
 *
 * <p>For a feature set of specific menu types, see {@link Menu}.</p>
 */
public final class MenuItemWrapper implements MenuItem {
    private static final class HoneycombMenuItem {
        static View getActionView(android.view.MenuItem item) {
            return item.getActionView();
        }

        static void setActionView(android.view.MenuItem item, int resId) {
            item.setActionView(resId);
        }

        static void setActionView(android.view.MenuItem item, View view) {
            item.setActionView(view);
        }

        static void setShowAsAction(android.view.MenuItem item, int actionEnum) {
            item.setShowAsAction(actionEnum);
        }
    }

    /** Native {@link android.view.MenuItem} whose methods are wrapped. */
    private final android.view.MenuItem mMenuItem;

    /**
     * Constructor used to create a wrapper to a native
     * {@link android.view.MenuItem} so we can return the same type for native
     * and {@link MenuItemImpl} instances, the latter of which will override
     * all the methods defined in this base class.
     *
     * @param menuItem Native instance.
     */
    public MenuItemWrapper(android.view.MenuItem menuItem) {
        mMenuItem = menuItem;
    }


    /**
     * Returns the currently set action view for this menu item.
     *
     * @return The item's action view
     * @see #setActionView(int)
     * @see #setActionView(View)
     * @see #setShowAsAction(int)
     */
    public View getActionView() {
        if (mMenuItem != null) {
            return HoneycombMenuItem.getActionView(mMenuItem);
        }
        return null;
    }

    /**
     * Set an action view for this menu item. An action view will be displayed
     * in place of an automatically generated menu item element in the UI when
     * this item is shown as an action within a parent.
     *
     * @param resId Layout resource to use for presenting this item to the user.
     * @return This Item so additional setters can be called.
     * @see #setActionView(View)
     */
    public MenuItem setActionView(int resId) {
        if (mMenuItem != null) {
            HoneycombMenuItem.setActionView(mMenuItem, resId);
        }
        return this;
    }

    /**
     * Set an action view for this menu item. An action view will be displayed
     * in place of an automatically generated menu item element in the UI when
     * this item is shown as an action within a parent.
     *
     * @param view View to use for presenting this item to the user.
     * @return This Item so additional setters can be called.
     * @see #setActionView(int)
     */
    public MenuItem setActionView(View view) {
        if (mMenuItem != null) {
            HoneycombMenuItem.setActionView(mMenuItem, view);
        }
        return this;
    }

    /**
     * Sets how this item should display in the presence of an Action Bar. The
     * parameter actionEnum is a flag set. One of
     * {@link #SHOW_AS_ACTION_ALWAYS}, {@link #SHOW_AS_ACTION_IF_ROOM}, or
     * {@link #SHOW_AS_ACTION_NEVER} should be used, and you may optionally OR
     * the value with {@link #SHOW_AS_ACTION_WITH_TEXT}.
     * {@link #SHOW_AS_ACTION_WITH_TEXT} requests that when the item is shown as
     * an action, it should be shown with a text label.
     *
     * @param actionEnum How the item should display. One of
     * {@link #SHOW_AS_ACTION_ALWAYS}, {@link #SHOW_AS_ACTION_IF_ROOM}, or
     * {@link #SHOW_AS_ACTION_NEVER}. {@link #SHOW_AS_ACTION_NEVER} is the
     * default.
     */
    public void setShowAsAction(int actionEnum) {
        if (mMenuItem != null) {
            HoneycombMenuItem.setShowAsAction(mMenuItem, actionEnum);
        }
    }

    // ---------------------------------------------------------------------
    // MENU ITEM SUPPORT
    // ---------------------------------------------------------------------

    @Override
    public char getAlphabeticShortcut() {
        return mMenuItem.getAlphabeticShortcut();
    }

    @Override
    public int getGroupId() {
        return mMenuItem.getGroupId();
    }

    @Override
    public Drawable getIcon() {
        return mMenuItem.getIcon();
    }

    @Override
    public Intent getIntent() {
        return mMenuItem.getIntent();
    }

    @Override
    public int getItemId() {
        return mMenuItem.getItemId();
    }

    @Override
    public ContextMenuInfo getMenuInfo() {
        return mMenuItem.getMenuInfo();
    }

    @Override
    public char getNumericShortcut() {
        return mMenuItem.getNumericShortcut();
    }

    @Override
    public int getOrder() {
        return mMenuItem.getOrder();
    }

    @Override
    public SubMenu getSubMenu() {
        return new SubMenuWrapper(mMenuItem.getSubMenu());
    }

    @Override
    public CharSequence getTitle() {
        return mMenuItem.getTitle();
    }

    @Override
    public CharSequence getTitleCondensed() {
        return mMenuItem.getTitleCondensed();
    }

    @Override
    public boolean hasSubMenu() {
        return mMenuItem.hasSubMenu();
    }

    @Override
    public boolean isCheckable() {
        return mMenuItem.isCheckable();
    }

    @Override
    public boolean isChecked() {
        return mMenuItem.isChecked();
    }

    @Override
    public boolean isEnabled() {
        return mMenuItem.isEnabled();
    }

    @Override
    public boolean isVisible() {
        return mMenuItem.isVisible();
    }

    @Override
    public MenuItem setAlphabeticShortcut(char alphaChar) {
        mMenuItem.setAlphabeticShortcut(alphaChar);
        return this;
    }

    @Override
    public MenuItem setCheckable(boolean checkable) {
        mMenuItem.setCheckable(checkable);
        return this;
    }

    @Override
    public MenuItem setChecked(boolean checked) {
        mMenuItem.setChecked(checked);
        return this;
    }

    @Override
    public MenuItem setEnabled(boolean enabled) {
        mMenuItem.setEnabled(enabled);
        return this;
    }

    @Override
    public MenuItem setIcon(Drawable icon) {
        mMenuItem.setIcon(icon);
        return this;
    }

    @Override
    public MenuItem setIcon(int iconRes) {
        mMenuItem.setIcon(iconRes);
        return this;
    }

    @Override
    public MenuItem setIntent(Intent intent) {
        mMenuItem.setIntent(intent);
        return this;
    }

    @Override
    public MenuItem setNumericShortcut(char numericChar) {
        mMenuItem.setNumericShortcut(numericChar);
        return this;
    }

    @Override
    public MenuItem setOnMenuItemClickListener(android.view.MenuItem.OnMenuItemClickListener menuItemClickListener) {
        mMenuItem.setOnMenuItemClickListener(menuItemClickListener);
        return this;
    }

    /**
     * Set a custom listener for invocation of this menu item.
     *
     * @param menuItemClickListener The object to receive invokations.
     * @return This Item so additional setters can be called.
     */
    public MenuItem setOnMenuItemClickListener(OnMenuItemClickListener menuItemClickListener) {
        mMenuItem.setOnMenuItemClickListener(menuItemClickListener);
        return this;
    }

    @Override
    public MenuItem setShortcut(char numericChar, char alphaChar) {
        mMenuItem.setShortcut(numericChar, alphaChar);
        return this;
    }

    @Override
    public MenuItem setTitle(CharSequence title) {
        mMenuItem.setTitle(title);
        return this;
    }

    @Override
    public MenuItem setTitle(int title) {
        mMenuItem.setTitle(title);
        return this;
    }

    @Override
    public MenuItem setTitleCondensed(CharSequence title) {
        mMenuItem.setTitleCondensed(title);
        return this;
    }

    @Override
    public MenuItem setVisible(boolean visible) {
        mMenuItem.setVisible(visible);
        return this;
    }
}
