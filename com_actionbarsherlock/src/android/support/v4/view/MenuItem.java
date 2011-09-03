/*
 * Copyright (C) 2006 The Android Open Source Project
 *               2011 Jake Wharton
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

package android.support.v4.view;

import com.actionbarsherlock.internal.view.menu.MenuItemWrapper;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * <p>Interface for direct access to a previously created menu item.</p>
 *
 * <p>An Item is returned by calling one of the {@link Menu#add(int)}
 * methods.</p>
 *
 * <p>For a feature set of specific menu types, see {@link Menu}.</p>
 */
public interface MenuItem extends android.view.MenuItem {
    /**
     * Interface definition for a callback to be invoked when a menu item is
     * clicked.
     */
    public static abstract class OnMenuItemClickListener implements android.view.MenuItem.OnMenuItemClickListener {
        /**
         * Called when a menu item has been invoked. This is the first code
         * that is executed; if it returns true, no other callbacks will be
         * executed.
         *
         * @param item The menu item that was invoked.
         * @return Return true to consume this click and prevent others from
         * executing.
         */
        public abstract boolean onMenuItemClick(MenuItem item);

        @Override
        public final boolean onMenuItemClick(android.view.MenuItem item) {
            return this.onMenuItemClick(new MenuItemWrapper(item));
        }
    }



    /**
     * Always show this item as a button in an Action Bar. Use sparingly! If too
     * many items are set to always show in the Action Bar it can crowd the
     * Action Bar and degrade the user experience on devices with smaller
     * screens. A good rule of thumb is to have no more than 2 items set to
     * always show at a time.
     */
    public static final int SHOW_AS_ACTION_ALWAYS = android.view.MenuItem.SHOW_AS_ACTION_ALWAYS;

    /**
     * Show this item as a button in an Action Bar if the system decides there
     * is room for it.
     */
    public static final int SHOW_AS_ACTION_IF_ROOM = android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM;

    /**
     * Never show this item as a button in an Action Bar.
     */
    public static final int SHOW_AS_ACTION_NEVER = android.view.MenuItem.SHOW_AS_ACTION_NEVER;

    /**
     * When this item is in the action bar, always show it with a text label
     * even if it also has an icon specified.
     */
    public static final int SHOW_AS_ACTION_WITH_TEXT = android.view.MenuItem.SHOW_AS_ACTION_WITH_TEXT;



    /**
     * Returns the currently set action view for this menu item.
     *
     * @return The item's action view
     * @see #setActionView(int)
     * @see #setActionView(View)
     * @see #setShowAsAction(int)
     */
    View getActionView();

    /**
     * Set an action view for this menu item. An action view will be displayed
     * in place of an automatically generated menu item element in the UI when
     * this item is shown as an action within a parent.
     *
     * @param resId Layout resource to use for presenting this item to the user.
     * @return This Item so additional setters can be called.
     * @see #setActionView(View)
     */
    MenuItem setActionView(int resId);

    /**
     * Set an action view for this menu item. An action view will be displayed
     * in place of an automatically generated menu item element in the UI when
     * this item is shown as an action within a parent.
     *
     * @param view View to use for presenting this item to the user.
     * @return This Item so additional setters can be called.
     * @see #setActionView(int)
     */
    MenuItem setActionView(View view);

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
    void setShowAsAction(int actionEnum);

    // ---------------------------------------------------------------------
    // MENU ITEM SUPPORT
    // ---------------------------------------------------------------------

    @Override
    SubMenu getSubMenu();

    @Override
    MenuItem setAlphabeticShortcut(char alphaChar);

    @Override
    MenuItem setCheckable(boolean checkable);

    @Override
    MenuItem setChecked(boolean checked);

    @Override
    MenuItem setEnabled(boolean enabled);

    @Override
    MenuItem setIcon(Drawable icon);

    @Override
    MenuItem setIcon(int iconRes);

    @Override
    MenuItem setIntent(Intent intent);

    @Override
    MenuItem setNumericShortcut(char numericChar);

    /**
     * Set a custom listener for invocation of this menu item.
     *
     * @param menuItemClickListener The object to receive invokations.
     * @return This Item so additional setters can be called.
     */
    MenuItem setOnMenuItemClickListener(OnMenuItemClickListener menuItemClickListener);

    @Override
    MenuItem setShortcut(char numericChar, char alphaChar);

    @Override
    MenuItem setTitle(CharSequence title);

    @Override
    MenuItem setTitle(int title);

    @Override
    MenuItem setTitleCondensed(CharSequence title);

    @Override
    MenuItem setVisible(boolean visible);
}
