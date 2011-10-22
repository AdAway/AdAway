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

import java.util.ArrayList;
import java.util.List;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.view.KeyEvent;

/**
 * An implementation of the {@link android.view.Menu} interface for use in
 * inflating menu XML resources to be added to a third-party action bar.
 *
 * @author Jake Wharton <jakewharton@gmail.com>
 * @see <a href="http://android.git.kernel.org/?p=platform/frameworks/base.git;a=blob;f=core/java/com/android/internal/view/menu/MenuBuilder.java">com.android.internal.view.menu.MenuBuilder</a>
 */
public class MenuBuilder implements Menu {
    private static final int DEFAULT_ITEM_ID = 0;
    private static final int DEFAULT_GROUP_ID = 0;
    private static final int DEFAULT_ORDER = 0;

    public static final int NUM_TYPES = 2;
    public static final int TYPE_ACTION_BAR = 0;
    public static final int TYPE_NATIVE = 1;

    /**
     * This is the part of an order integer that the user can provide.
     * @hide
     */
    static final int USER_MASK = 0x0000ffff;

    /**
     * Bit shift of the user portion of the order integer.
     * @hide
     */
    static final int USER_SHIFT = 0;

    /**
     * This is the part of an order integer that supplies the category of the
     * item.
     * @hide
     */

    static final int CATEGORY_MASK = 0xffff0000;

    /**
     * Bit shift of the category portion of the order integer.
     * @hide
     */
    static final int CATEGORY_SHIFT = 16;

    private static final int[] CATEGORY_TO_ORDER = new int[] {
        1, /* No category */
        4, /* CONTAINER */
        5, /* SYSTEM */
        3, /* SECONDARY */
        2, /* ALTERNATIVE */
        0, /* SELECTED_ALTERNATIVE */
    };



    public interface Callback {
        public boolean onMenuItemSelected(MenuBuilder menu, MenuItem item);
    }



    /** Context used for resolving any resources. */
    private final Context mContext;

    /** Child {@link ActionBarMenuItem} items. */
    private final ArrayList<MenuItemImpl> mItems;

    /** Menu callback that will receive various events. */
    private Callback mCallback;

    private boolean mShowsActionItemText;



    /**
     * Create a new action bar menu.
     *
     * @param context Context used if resource resolution is required.
     */
    public MenuBuilder(Context context) {
        this.mContext = context;
        this.mItems = new ArrayList<MenuItemImpl>();
    }


    /**
     * Adds an item to the menu.  The other add methods funnel to this.
     *
     * @param itemId Unique item ID.
     * @param groupId Group ID.
     * @param order Order.
     * @param title Item title.
     * @return MenuItem instance.
     */
    private MenuItem addInternal(int itemId, int groupId, int order, CharSequence title) {
        final int ordering = getOrdering(order);
        final MenuItemImpl item = new MenuItemImpl(this, groupId, itemId, order, ordering, title, MenuItem.SHOW_AS_ACTION_NEVER);

        mItems.add(findInsertIndex(mItems, ordering), item);
        return item;
    }

    private static int findInsertIndex(ArrayList<MenuItemImpl> items, int ordering) {
        for (int i = items.size() - 1; i >= 0; i--) {
            MenuItemImpl item = items.get(i);
            if (item.getOrdering() <= ordering) {
                return i + 1;
            }
        }

        return 0;
    }

    /**
     * Returns the ordering across all items. This will grab the category from
     * the upper bits, find out how to order the category with respect to other
     * categories, and combine it with the lower bits.
     *
     * @param categoryOrder The category order for a particular item (if it has
     *            not been or/add with a category, the default category is
     *            assumed).
     * @return An ordering integer that can be used to order this item across
     *         all the items (even from other categories).
     */
    private static int getOrdering(int categoryOrder) {
        final int index = (categoryOrder & CATEGORY_MASK) >> CATEGORY_SHIFT;

        if (index < 0 || index >= CATEGORY_TO_ORDER.length) {
            throw new IllegalArgumentException("order does not contain a valid category.");
        }

        return (CATEGORY_TO_ORDER[index] << CATEGORY_SHIFT) | (categoryOrder & USER_MASK);
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public Callback getCallback() {
        return mCallback;
    }

    public boolean getShowsActionItemText() {
        return mShowsActionItemText;
    }

    public void setShowsActionItemText(boolean showsActionItemText) {
        mShowsActionItemText = showsActionItemText;
    }

    /**
     * Gets the root menu (if this is a submenu, find its root menu).
     *
     * @return The root menu.
     */
    public MenuBuilder getRootMenu() {
        return this;
    }

    /**
     * Get a list of the items contained in this menu.
     *
     * @return List of {@link MenuItemImpl}s.
     */
    public final List<MenuItemImpl> getItems() {
        return this.mItems;
    }

    final MenuItemImpl remove(int index) {
        return this.mItems.remove(index);
    }

    final Context getContext() {
        return this.mContext;
    }

    void setExclusiveItemChecked(MenuItem item) {
        final int group = item.getGroupId();

        final int N = mItems.size();
        for (int i = 0; i < N; i++) {
            MenuItemImpl curItem = mItems.get(i);
            if (curItem.getGroupId() == group) {
                if (!curItem.isExclusiveCheckable()) continue;
                if (!curItem.isCheckable()) continue;

                // Check the item meant to be checked, uncheck the others (that are in the group)
                curItem.setCheckedInt(curItem == item);
            }
        }
    }

    // ** Menu Methods ** \\

    @Override
    public MenuItem add(int titleResourceId) {
        return addInternal(0, 0, 0, mContext.getResources().getString(titleResourceId));
    }

    @Override
    public MenuItem add(int groupId, int itemId, int order, int titleResourceId) {
        return addInternal(itemId, groupId, order, mContext.getResources().getString(titleResourceId));
    }

    @Override
    public MenuItem add(int groupId, int itemId, int order, CharSequence title) {
        return addInternal(itemId, groupId, order, title);
    }

    @Override
    public MenuItem add(CharSequence title) {
        return addInternal(0, 0, 0, title);
    }

    @Override
    public SubMenuBuilder addSubMenu(CharSequence title) {
        return this.addSubMenu(DEFAULT_GROUP_ID, DEFAULT_ITEM_ID, DEFAULT_ORDER, title);
    }

    @Override
    public SubMenuBuilder addSubMenu(int titleResourceId) {
        return this.addSubMenu(DEFAULT_GROUP_ID, DEFAULT_ITEM_ID, DEFAULT_ORDER, titleResourceId);
    }

    @Override
    public SubMenuBuilder addSubMenu(int groupId, int itemId, int order, int titleResourceId) {
        String title = this.mContext.getResources().getString(titleResourceId);
        return this.addSubMenu(groupId, itemId, order, title);
    }

    @Override
    public SubMenuBuilder addSubMenu(int groupId, int itemId, int order, CharSequence title) {
        MenuItemImpl item = (MenuItemImpl)this.add(groupId, itemId, order, title);
        SubMenuBuilder subMenu = new SubMenuBuilder(this.mContext, this, item);
        item.setSubMenu(subMenu);
        return subMenu;
    }

    @Override
    public void clear() {
        this.mItems.clear();
    }

    @Override
    public void close() {}

    @Override
    public MenuItemImpl findItem(int itemId) {
        for (MenuItemImpl item : this.mItems) {
            if (item.getItemId() == itemId) {
                return item;
            }
        }
        return null;
    }

    @Override
    public MenuItemImpl getItem(int index) {
        return this.mItems.get(index);
    }

    @Override
    public boolean hasVisibleItems() {
        for (MenuItem item : this.mItems) {
            if (item.isVisible()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void removeItem(int itemId) {
        final int size = this.mItems.size();
        for (int i = 0; i < size; i++) {
            if (this.mItems.get(i).getItemId() == itemId) {
                this.mItems.remove(i);
                return;
            }
        }
    }

    @Override
    public int size() {
        return this.mItems.size();
    }

    @Override
    public int addIntentOptions(int groupId, int itemId, int order, ComponentName caller, Intent[] specifics, Intent intent, int flags, android.view.MenuItem[] outSpecificItems) {
        PackageManager pm = mContext.getPackageManager();
        final List<ResolveInfo> lri =
                pm.queryIntentActivityOptions(caller, specifics, intent, 0);
        final int N = lri != null ? lri.size() : 0;

        if ((flags & FLAG_APPEND_TO_GROUP) == 0) {
            removeGroup(groupId);
        }

        for (int i=0; i<N; i++) {
            final ResolveInfo ri = lri.get(i);
            Intent rintent = new Intent(
                ri.specificIndex < 0 ? intent : specifics[ri.specificIndex]);
            rintent.setComponent(new ComponentName(
                    ri.activityInfo.applicationInfo.packageName,
                    ri.activityInfo.name));
            final MenuItem item = add(groupId, itemId, order, ri.loadLabel(pm))
                    .setIcon(ri.loadIcon(pm))
                    .setIntent(rintent);
            if (outSpecificItems != null && ri.specificIndex >= 0) {
                outSpecificItems[ri.specificIndex] = item;
            }
        }

        return N;
    }

    @Override
    public boolean isShortcutKey(int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public boolean performIdentifierAction(int id, int flags) {
        throw new RuntimeException("Method not supported.");
    }

    @Override
    public boolean performShortcut(int keyCode, KeyEvent event, int flags) {
        return false;
    }

    @Override
    public void removeGroup(int groupId) {
        for (int i = mItems.size() - 1; i > 0; i--) {
            if (mItems.get(i).getGroupId() == groupId) {
                mItems.remove(i);
            }
        }
    }

    @Override
    public void setGroupCheckable(int groupId, boolean checkable, boolean exclusive) {
        final int N = mItems.size();
        for (int i = 0; i < N; i++) {
            MenuItemImpl item = mItems.get(i);
            if (item.getGroupId() == groupId) {
                item.setExclusiveCheckable(exclusive);
                item.setCheckable(checkable);
            }
        }
    }

    @Override
    public void setGroupEnabled(int groupId, boolean enabled) {
        final int size = this.mItems.size();
        for (int i = 0; i < size; i++) {
            MenuItemImpl item = mItems.get(i);
            if (item.getGroupId() == groupId) {
                item.setEnabled(enabled);
            }
        }
    }

    @Override
    public void setGroupVisible(int groupId, boolean visible) {
        final int size = this.mItems.size();
        for (int i = 0; i < size; i++) {
            MenuItemImpl item = mItems.get(i);
            if (item.getGroupId() == groupId) {
                item.setVisible(visible);
            }
        }
    }

    @Override
    public void setQwertyMode(boolean isQwerty) {
        throw new RuntimeException("Method not supported.");
    }
}
