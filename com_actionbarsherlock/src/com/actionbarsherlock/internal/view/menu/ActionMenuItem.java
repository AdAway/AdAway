package com.actionbarsherlock.internal.view.menu;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.support.v4.view.MenuItem;
import android.support.v4.view.SubMenu;
import android.view.ContextMenu;
import android.view.View;

public class ActionMenuItem implements MenuItem {
    private static final int CHECKABLE = MenuItemImpl.CHECKABLE;
    private static final int CHECKED = MenuItemImpl.CHECKED;
    private static final int ENABLED = MenuItemImpl.ENABLED;
    private static final int EXCLUSIVE = MenuItemImpl.EXCLUSIVE;
    private static final int HIDDEN = MenuItemImpl.HIDDEN;
    private static final int NO_ICON = 0;

    //XXX UNUSED: private final int mCategoryOrder;
    private MenuItem.OnMenuItemClickListener mClickListener;
    private Context mContext;
    private int mFlags = ENABLED;
    private final int mGroup;
    private Drawable mIconDrawable;
    private int mIconResId = NO_ICON;
    private final int mId;
    private Intent mIntent;
    private final int mOrdering;
    private char mShortcutAlphabeticChar;
    private char mShortcutNumericChar;
    private CharSequence mTitle;
    private CharSequence mTitleCondensed;

    public ActionMenuItem(Context context, int group, int id, int categoryOrder, int ordering, CharSequence title) {
        mContext = context;
        mId = id;
        mGroup = group;
        //XXX UNUSED mCategoryOrder = categoryOrder;
        mOrdering = ordering;
        mTitle = title;
    }

    @Override
    public View getActionView() {
        return null;
    }

    @Override
    public char getAlphabeticShortcut() {
        return mShortcutAlphabeticChar;
    }

    @Override
    public int getGroupId() {
        return mGroup;
    }

    @Override
    public Drawable getIcon() {
        return mIconDrawable;
    }

    @Override
    public Intent getIntent() {
        return mIntent;
    }

    @Override
    public int getItemId() {
        return mId;
    }

    @Override
    public ContextMenu.ContextMenuInfo getMenuInfo() {
        return null;
    }

    @Override
    public char getNumericShortcut() {
        return mShortcutNumericChar;
    }

    @Override
    public int getOrder() {
        return mOrdering;
    }

    @Override
    public SubMenu getSubMenu() {
        return null;
    }

    @Override
    public CharSequence getTitle() {
        return mTitle;
    }

    @Override
    public CharSequence getTitleCondensed() {
        return mTitleCondensed;
    }

    @Override
    public boolean hasSubMenu() {
        return false;
    }

    public boolean invoke() {
        if ((mClickListener != null) && mClickListener.onMenuItemClick(this)) {
            return true;
        } else if (mIntent != null) {
            mContext.startActivity(mIntent);
            return true;
        }
        return false;
    }

    @Override
    public boolean isCheckable() {
        return (mFlags & CHECKABLE) != 0;
    }

    @Override
    public boolean isChecked() {
        return (mFlags & CHECKED) != 0;
    }

    @Override
    public boolean isEnabled() {
        return (mFlags & ENABLED) != 0;
    }

    @Override
    public boolean isVisible() {
        return (mFlags & HIDDEN) == 0;
    }

    @Override
    public MenuItem setActionView(int layoutResId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MenuItem setActionView(View view) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MenuItem setAlphabeticShortcut(char shortcut) {
        mShortcutAlphabeticChar = shortcut;
        return this;
    }

    @Override
    public MenuItem setCheckable(boolean checkable) {
        mFlags = (mFlags & ~CHECKABLE) | (checkable ? CHECKABLE : 0);
        return this;
    }

    @Override
    public MenuItem setChecked(boolean checked) {
        mFlags = (mFlags & ~CHECKED) | (checked ? CHECKED : 0);
        return this;
    }

    @Override
    public MenuItem setEnabled(boolean enabled) {
        mFlags = (mFlags & ~ENABLED) | (enabled ? ENABLED : 0);
        return this;
    }

    public ActionMenuItem setExclusiveCheckable(boolean exclusive) {
        mFlags = (mFlags & ~EXCLUSIVE) | (exclusive ? EXCLUSIVE : 0);
        return this;
    }

    @Override
    public MenuItem setIcon(int resId) {
        mIconResId = resId;
        mIconDrawable = mContext.getResources().getDrawable(mIconResId);
        return this;
    }

    @Override
    public MenuItem setIcon(Drawable icon) {
        mIconDrawable = icon;
        mIconResId = NO_ICON;
        return this;
    }

    @Override
    public MenuItem setIntent(Intent intent) {
        mIntent = intent;
        return this;
    }

    @Override
    public MenuItem setNumericShortcut(char shortcut) {
        mShortcutNumericChar = shortcut;
        return this;
    }

    @Override
    public MenuItem setOnMenuItemClickListener(MenuItem.OnMenuItemClickListener listener) {
        mClickListener = listener;
        return this;
    }

    @Override
    public android.view.MenuItem setOnMenuItemClickListener(final android.view.MenuItem.OnMenuItemClickListener listener) {
        mClickListener = new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                return listener.onMenuItemClick(item);
            }
        };
        return null;
    }

    @Override
    public MenuItem setShortcut(char numericShortcut, char alphabeticShortcut) {
        mShortcutNumericChar = numericShortcut;
        mShortcutAlphabeticChar = alphabeticShortcut;
        return this;
    }

    @Override
    public void setShowAsAction(int layoutResId) {
        //No op
    }

    @Override
    public MenuItem setTitle(int resId) {
        this.mTitle = mContext.getResources().getString(resId);
        return this;
    }

    @Override
    public MenuItem setTitle(CharSequence title) {
        mTitle = title;
        return this;
    }

    @Override
    public MenuItem setTitleCondensed(CharSequence title) {
        mTitleCondensed = title;
        return this;
    }

    @Override
    public MenuItem setVisible(boolean visible) {
        mFlags = (mFlags & HIDDEN) | (visible ? 0 : HIDDEN);
        return this;
    }
}