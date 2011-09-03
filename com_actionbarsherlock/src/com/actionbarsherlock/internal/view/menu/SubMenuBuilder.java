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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.v4.view.SubMenu;
import android.view.View;

/**
 * The model for a sub menu, which is an extension of the menu.  Most methods
 * are proxied to the parent menu.
 */
public final class SubMenuBuilder extends MenuBuilder implements SubMenu {
    private MenuBuilder mParentMenu;
    private MenuItemImpl mItem;

    public SubMenuBuilder(Context context, MenuBuilder parentMenu, MenuItemImpl item) {
        super(context);

        mParentMenu = parentMenu;
        mItem = item;
    }

    @Override
    public void setQwertyMode(boolean isQwerty) {
        mParentMenu.setQwertyMode(isQwerty);
    }

    //@Override
    //public boolean isQwertyMode() {
    //    return mParentMenu.isQwertyMode();
    //}

    //@Override
    //public void setShortcutsVisible(boolean shortcutsVisible) {
    //    mParentMenu.setShortcutsVisible(shortcutsVisible);
    //}

    //@Override
    //public boolean isShortcutsVisible() {
    //    return mParentMenu.isShortcutsVisible();
    //}

    MenuBuilder getParentMenu() {
        return mParentMenu;
    }

    @Override
    public MenuItemImpl getItem() {
        return mItem;
    }

    //@Override
    //public Callback getCallback() {
    //    return mParentMenu.getCallback();
    //}

    //@Override
    //public void setCallback(Callback callback) {
    //    mParentMenu.setCallback(callback);
    //}

    @Override
    public MenuBuilder getRootMenu() {
        return mParentMenu;
    }

    public SubMenuBuilder setIcon(Drawable icon) {
        mItem.setIcon(icon);
        return this;
    }

    public SubMenuBuilder setIcon(int iconRes) {
        mItem.setIcon(iconRes);
        return this;
    }

    public SubMenuBuilder setHeaderIcon(Drawable icon) {
        throw new RuntimeException("Method not supported.");
    }

    public SubMenuBuilder setHeaderIcon(int iconRes) {
        throw new RuntimeException("Method not supported.");
    }

    public SubMenuBuilder setHeaderTitle(CharSequence title) {
        throw new RuntimeException("Method not supported.");
    }

    public SubMenuBuilder setHeaderTitle(int titleRes) {
        throw new RuntimeException("Method not supported.");
    }

    @Override
    public SubMenuBuilder setHeaderView(View view) {
        throw new RuntimeException("Method not supported.");
    }

    @Override
    public void clearHeader() {
        throw new RuntimeException("Method not supported.");
    }
}