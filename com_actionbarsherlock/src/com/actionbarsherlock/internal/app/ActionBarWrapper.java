/*
 * Copyright 2011 Jake Wharton
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.actionbarsherlock.internal.app;

import java.util.HashMap;

import com.actionbarsherlock.internal.view.menu.MenuInflaterImpl;
import com.actionbarsherlock.internal.view.menu.MenuItemWrapper;
import com.actionbarsherlock.internal.view.menu.MenuWrapper;
import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.v4.app.ActionBar;
import android.support.v4.view.ActionMode;
import android.support.v4.view.Menu;
import android.view.View;
import android.widget.SpinnerAdapter;

public final class ActionBarWrapper {
    //No instances
    private ActionBarWrapper() {}

    /**
     * Abstraction to get an instance of our implementing class.
     *
     * @param activity Parent activity.
     * @return {@code ActionBar} instance.
     */
    public static ActionBar createFor(Activity activity) {
        if (!(activity instanceof SherlockActivity)) {
            throw new RuntimeException("Activity must implement the SherlockActivity interface");
        }

        return new ActionBarWrapper.Impl(activity);
    }

    /**
     * Handler for Android's native {@link android.app.ActionBar}.
     */
    public static final class Impl extends ActionBar implements android.app.ActionBar.TabListener {
        /** Mapping between support listeners and native listeners. */
        private final HashMap<OnMenuVisibilityListener, android.app.ActionBar.OnMenuVisibilityListener> mMenuListenerMap = new HashMap<OnMenuVisibilityListener, android.app.ActionBar.OnMenuVisibilityListener>();

        private final Activity mActivity;

        private Impl(Activity activity) {
            mActivity = activity;
        }


        /**
         * Get the native {@link ActionBar} instance.
         *
         * @return The action bar.
         */
        private android.app.ActionBar getActionBar() {
            return mActivity.getActionBar();
        }

        /**
         * Converts our Tab wrapper to a native version containing the wrapper
         * instance as its tag.
         *
         * @param tab Tab wrapper instance.
         * @return Native tab.
         */
        private android.app.ActionBar.Tab convertTabToNative(ActionBar.Tab tab) {
            return getActionBar().newTab()
                    .setCustomView(tab.getCustomView())
                    .setIcon(tab.getIcon())
                    .setTabListener(this)
                    .setTag(tab)
                    .setText(tab.getText());
        }

        @Override
        public void onTabReselected(android.app.ActionBar.Tab tab, android.app.FragmentTransaction ft) {
            ActionBar.TabListener listener = ((ActionBar.Tab)tab.getTag()).getTabListener();
            if (listener != null) {
                listener.onTabReselected((ActionBar.Tab)tab.getTag(), null);
            }
        }

        @Override
        public void onTabSelected(android.app.ActionBar.Tab tab, android.app.FragmentTransaction ft) {
            ActionBar.TabListener listener = ((ActionBar.Tab)tab.getTag()).getTabListener();
            if (listener != null) {
                listener.onTabSelected((ActionBar.Tab)tab.getTag(), null);
            }
        }

        @Override
        public void onTabUnselected(android.app.ActionBar.Tab tab, android.app.FragmentTransaction ft) {
            ActionBar.TabListener listener = ((ActionBar.Tab)tab.getTag()).getTabListener();
            if (listener != null) {
                listener.onTabUnselected((ActionBar.Tab)tab.getTag(), null);
            }
        }

        // ---------------------------------------------------------------------
        // ACTION MODE SUPPORT
        // ---------------------------------------------------------------------

        @Override
        protected ActionMode startActionMode(final ActionMode.Callback callback) {
            //We have to re-wrap the instances in every callback since the
            //wrapped instance is needed before we could have a change to
            //properly store it.
            return new ActionModeWrapper(mActivity,
                mActivity.startActionMode(new android.view.ActionMode.Callback() {
                    @Override
                    public boolean onPrepareActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                        return callback.onPrepareActionMode(new ActionModeWrapper(mActivity, mode), new MenuWrapper(menu));
                    }

                    @Override
                    public void onDestroyActionMode(android.view.ActionMode mode) {
                        final ActionMode actionMode = new ActionModeWrapper(mActivity, mode);
                        callback.onDestroyActionMode(actionMode);

                        //Send the activity callback once the action mode callback has run.
                        //This type-check has already occurred in the action bar constructor.
                        ((SherlockActivity)mActivity).onActionModeFinished(actionMode);
                    }

                    @Override
                    public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                        return callback.onCreateActionMode(new ActionModeWrapper(mActivity, mode), new MenuWrapper(menu));
                    }

                    @Override
                    public boolean onActionItemClicked(android.view.ActionMode mode, android.view.MenuItem item) {
                        return callback.onActionItemClicked(new ActionModeWrapper(mActivity, mode), new MenuItemWrapper(item));
                    }
                })
            );
        }

        private static class ActionModeWrapper extends ActionMode {
            private final Context mContext;
            private final android.view.ActionMode mActionMode;

            ActionModeWrapper(Context context, android.view.ActionMode actionMode) {
                mContext = context;
                mActionMode = actionMode;
            }

            @Override
            public void finish() {
                mActionMode.finish();
            }

            @Override
            public View getCustomView() {
                return mActionMode.getCustomView();
            }

            @Override
            public Menu getMenu() {
                return new MenuWrapper(mActionMode.getMenu());
            }

            @Override
            public MenuInflaterImpl getMenuInflater() {
                return new MenuInflaterImpl(mContext, null);
            }

            @Override
            public CharSequence getSubtitle() {
                return mActionMode.getSubtitle();
            }

            @Override
            public CharSequence getTitle() {
                return mActionMode.getTitle();
            }

            @Override
            public void invalidate() {
                mActionMode.invalidate();
            }

            @Override
            public void setCustomView(View view) {
                mActionMode.setCustomView(view);
            }

            @Override
            public void setSubtitle(int resId) {
                mActionMode.setSubtitle(resId);
            }

            @Override
            public void setSubtitle(CharSequence subtitle) {
                mActionMode.setSubtitle(subtitle);
            }

            @Override
            public void setTitle(int resId) {
                mActionMode.setTitle(resId);
            }

            @Override
            public void setTitle(CharSequence title) {
                mActionMode.setTitle(title);
            }
        }

        // ---------------------------------------------------------------------
        // ACTION BAR SUPPORT
        // ---------------------------------------------------------------------

        private static class TabImpl extends ActionBar.Tab {
            final ActionBarWrapper.Impl mActionBar;

            View mCustomView;
            Drawable mIcon;
            ActionBar.TabListener mListener;
            Object mTag;
            CharSequence mText;

            TabImpl(ActionBarWrapper.Impl actionBar) {
                mActionBar = actionBar;
            }

            @Override
            public View getCustomView() {
                return mCustomView;
            }

            @Override
            public Drawable getIcon() {
                return mIcon;
            }

            @Override
            public int getPosition() {
                final int tabCount = mActionBar.getTabCount();
                for (int i = 0; i < tabCount; i++) {
                    if (mActionBar.getTabAt(i).equals(this)) {
                        return i;
                    }
                }
                return ActionBar.Tab.INVALID_POSITION;
            }

            @Override
            public ActionBar.TabListener getTabListener() {
                return mListener;
            }

            @Override
            public Object getTag() {
                return mTag;
            }

            @Override
            public CharSequence getText() {
                return mText;
            }

            @Override
            public void select() {
                mActionBar.selectTab(this);
            }

            @Override
            public ActionBar.Tab setCustomView(int layoutResId) {
                mCustomView = mActionBar.mActivity.getLayoutInflater().inflate(layoutResId, null);
                return this;
            }

            @Override
            public ActionBar.Tab setCustomView(View view) {
                mCustomView = view;
                return this;
            }

            @Override
            public ActionBar.Tab setIcon(Drawable icon) {
                mIcon = icon;
                return this;
            }

            @Override
            public ActionBar.Tab setIcon(int resId) {
                mIcon = mActionBar.mActivity.getResources().getDrawable(resId);
                return this;
            }

            @Override
            public ActionBar.Tab setTabListener(TabListener listener) {
                mListener = listener;
                return this;
            }

            @Override
            public ActionBar.Tab setTag(Object obj) {
                mTag = obj;
                return this;
            }

            @Override
            public ActionBar.Tab setText(int resId) {
                mText = mActionBar.mActivity.getResources().getString(resId);
                return this;
            }

            @Override
            public ActionBar.Tab setText(CharSequence text) {
                mText = text;
                return this;
            }
        }

        @Override
        public void addOnMenuVisibilityListener(final OnMenuVisibilityListener listener) {
            if ((listener != null) && !mMenuListenerMap.containsKey(listener)) {
                android.app.ActionBar.OnMenuVisibilityListener nativeListener = new android.app.ActionBar.OnMenuVisibilityListener() {
                    @Override
                    public void onMenuVisibilityChanged(boolean isVisible) {
                        listener.onMenuVisibilityChanged(isVisible);
                    }
                };
                mMenuListenerMap.put(listener, nativeListener);

                getActionBar().addOnMenuVisibilityListener(nativeListener);
            }
        }

        @Override
        public void addTab(Tab tab) {
            getActionBar().addTab(convertTabToNative(tab));
        }

        @Override
        public void addTab(Tab tab, boolean setSelected) {
            getActionBar().addTab(convertTabToNative(tab), setSelected);
        }

        @Override
        public void addTab(Tab tab, int position) {
            getActionBar().addTab(convertTabToNative(tab), position);
        }

        @Override
        public void addTab(ActionBar.Tab tab, int position, boolean setSelected) {
            getActionBar().addTab(convertTabToNative(tab), position, setSelected);
        }

        @Override
        public View getCustomView() {
            return getActionBar().getCustomView();
        }

        @Override
        public int getDisplayOptions() {
            return getActionBar().getDisplayOptions();
        }

        @Override
        public int getHeight() {
            return getActionBar().getHeight();
        }

        @Override
        public int getNavigationItemCount() {
            return getActionBar().getNavigationItemCount();
        }

        @Override
        public int getNavigationMode() {
            return getActionBar().getNavigationMode();
        }

        @Override
        public int getSelectedNavigationIndex() {
            return getActionBar().getSelectedNavigationIndex();
        }

        @Override
        public Tab getSelectedTab() {
            if (getActionBar().getSelectedTab() != null) {
                return (ActionBar.Tab)getActionBar().getSelectedTab().getTag();
            }
            return null;
        }

        @Override
        public CharSequence getSubtitle() {
            return getActionBar().getSubtitle();
        }

        @Override
        public ActionBar.Tab getTabAt(int index) {
            if (getActionBar().getTabAt(index) != null) {
                return (Tab)getActionBar().getTabAt(index).getTag();
            }
            return null;
        }

        @Override
        public int getTabCount() {
            return getActionBar().getTabCount();
        }

        @Override
        public CharSequence getTitle() {
            return getActionBar().getTitle();
        }

        @Override
        public void hide() {
            getActionBar().hide();
        }

        @Override
        public boolean isShowing() {
            return getActionBar().isShowing();
        }

        @Override
        public ActionBar.Tab newTab() {
            return new TabImpl(this);
        }

        @Override
        public void removeAllTabs() {
            getActionBar().removeAllTabs();
        }

        @Override
        public void removeOnMenuVisibilityListener(OnMenuVisibilityListener listener) {
            if ((listener != null) && mMenuListenerMap.containsKey(listener)) {
                getActionBar().removeOnMenuVisibilityListener(
                    mMenuListenerMap.remove(listener)
                );
            }
        }

        @Override
        public void removeTab(Tab tab) {
            final int tabCount = getActionBar().getTabCount();
            for (int i = 0; i < tabCount; i++) {
                if (getActionBar().getTabAt(i).getTag().equals(tab)) {
                    getActionBar().removeTabAt(i);
                    break;
                }
            }
        }

        @Override
        public void removeTabAt(int position) {
            getActionBar().removeTabAt(position);
        }

        @Override
        public void selectTab(ActionBar.Tab tab) {
            final int tabCount = getActionBar().getTabCount();
            for (int i = 0; i < tabCount; i++) {
                if (getActionBar().getTabAt(i).getTag().equals(tab)) {
                    getActionBar().setSelectedNavigationItem(i);
                    break;
                }
            }
        }

        @Override
        public void setBackgroundDrawable(Drawable d) {
            getActionBar().setBackgroundDrawable(d);
        }

        @Override
        public void setCustomView(int resId) {
            getActionBar().setCustomView(resId);
        }

        @Override
        public void setCustomView(View view) {
            getActionBar().setCustomView(view);
        }

        @Override
        public void setCustomView(View view, LayoutParams layoutParams) {
            android.app.ActionBar.LayoutParams nativeLayoutParams = new android.app.ActionBar.LayoutParams(layoutParams);
            nativeLayoutParams.gravity = layoutParams.gravity;
            getActionBar().setCustomView(view, nativeLayoutParams);
        }

        @Override
        public void setDisplayHomeAsUpEnabled(boolean showHomeAsUp) {
            getActionBar().setDisplayHomeAsUpEnabled(showHomeAsUp);
        }

        @Override
        public void setDisplayOptions(int options, int mask) {
            getActionBar().setDisplayOptions(options, mask);
        }

        @Override
        public void setDisplayOptions(int options) {
            getActionBar().setDisplayOptions(options);
        }

        @Override
        public void setDisplayShowCustomEnabled(boolean showCustom) {
            getActionBar().setDisplayShowCustomEnabled(showCustom);
        }

        @Override
        public void setDisplayShowHomeEnabled(boolean showHome) {
            getActionBar().setDisplayShowHomeEnabled(showHome);
        }

        @Override
        public void setDisplayShowTitleEnabled(boolean showTitle) {
            getActionBar().setDisplayShowTitleEnabled(showTitle);
        }

        @Override
        public void setDisplayUseLogoEnabled(boolean useLogo) {
            getActionBar().setDisplayUseLogoEnabled(useLogo);
        }

        @Override
        public void setListNavigationCallbacks(SpinnerAdapter adapter, final OnNavigationListener callback) {
            getActionBar().setListNavigationCallbacks(adapter, new android.app.ActionBar.OnNavigationListener() {
                @Override
                public boolean onNavigationItemSelected(int itemPosition, long itemId) {
                    if (callback != null) {
                        return callback.onNavigationItemSelected(itemPosition, itemId);
                    }
                    return false;
                }
            });
        }

        @Override
        public void setNavigationMode(int mode) {
            getActionBar().setNavigationMode(mode);
        }

        @Override
        public void setSelectedNavigationItem(int position) {
            getActionBar().setSelectedNavigationItem(position);
        }

        @Override
        public void setSubtitle(CharSequence subtitle) {
            getActionBar().setSubtitle(subtitle);
        }

        @Override
        public void setSubtitle(int resId) {
            getActionBar().setSubtitle(resId);
        }

        @Override
        public void setTitle(CharSequence title) {
            getActionBar().setTitle(title);
        }

        @Override
        public void setTitle(int resId) {
            getActionBar().setTitle(resId);
        }

        @Override
        public void show() {
            getActionBar().show();
        }
    }
}
