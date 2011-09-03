package com.actionbarsherlock.internal.widget;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.support.v4.app.ActionBar;
import android.support.v4.view.Window;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import com.actionbarsherlock.R;
import com.actionbarsherlock.internal.view.menu.ActionMenuItem;
import com.actionbarsherlock.internal.view.menu.ActionMenuItemView;

public final class ActionBarView extends RelativeLayout {
    /** Default display options if none are defined in the theme. */
    private static final int DEFAULT_DISPLAY_OPTIONS = ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_HOME;

    /** Default navigation mode if one is not defined in the theme. */
    private static final int DEFAULT_NAVIGATION_MODE = ActionBar.NAVIGATION_MODE_STANDARD;



    private final View mHomeAsUpView;
    private final View mHomeLayout;
    private final ActionMenuItem mLogoNavItem;

    private final CharSequence mTitle;
    private final TextView mTitleLayout;

    private final CharSequence mSubtitle;
    private final TextView mSubtitleLayout;

    /** Indeterminate progress bar. */
    private final ProgressBar mIndeterminateProgress;

    /** List view. */
    private final Spinner mListView;

    /** Custom view parent. */
    private final FrameLayout mCustomView;

    private ImageView mIconView;
    private Drawable mLogo;
    private Drawable mIcon;
    private final Drawable mDivider;

    /** Container for all action items. */
    private final LinearLayout mActionsView;

    /** Container for all tab items. */
    private final LinearLayout mTabsView;

    /**
     * Display state flags.
     *
     * @see #getDisplayOptions()
     * @see #getDisplayOptionValue(int)
     * @see #setDisplayOptions(int)
     * @see #setDisplayOptions(int, int)
     * @see #setDisplayOption(int, boolean)
     * @see #reloadDisplay()
     */
    private int mFlags;

    /**
     * Current navigation mode
     *
     * @see #getNavigationMode()
     * @see #setNavigationMode(int)
     */
    private int mNavigationMode = -1;

    private boolean mIsConstructing;



    public ActionBarView(Context context) {
        this(context, null);
    }

    public ActionBarView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ActionBarView(final Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mIsConstructing = true;
        LayoutInflater.from(context).inflate(R.layout.abs__action_bar, this, true);

        setBackgroundResource(0);

        final TypedArray attrsActionBar = context.obtainStyledAttributes(attrs, R.styleable.SherlockTheme, defStyle, 0);
        final ApplicationInfo appInfo = context.getApplicationInfo();
        final PackageManager pm = context.getPackageManager();


        //// TITLE ////

        mTitleLayout = (TextView)findViewById(R.id.abs__action_bar_title);

        //Try to load title style from the theme
        final int titleTextStyle = attrsActionBar.getResourceId(R.styleable.SherlockTheme_abTitleTextStyle, 0);
        if (titleTextStyle != 0) {
            mTitleLayout.setTextAppearance(context, titleTextStyle);
        }

        //Try to load title from the theme
        mTitle = attrsActionBar.getString(R.styleable.SherlockTheme_abTitle);
        if (mTitle != null) {
            setTitle(mTitle);
        }


        //// SUBTITLE ////

        mSubtitleLayout = (TextView)findViewById(R.id.abs__action_bar_subtitle);

        //Try to load subtitle style from the theme
        final int subtitleTextStyle = attrsActionBar.getResourceId(R.styleable.SherlockTheme_abSubtitleTextStyle, 0);
        if (subtitleTextStyle != 0) {
            mSubtitleLayout.setTextAppearance(context, subtitleTextStyle);
        }

        //Try to load subtitle from theme
        mSubtitle = attrsActionBar.getString(R.styleable.SherlockTheme_abSubtitle);
        if (mSubtitle != null) {
            setSubtitle(mSubtitle);
        }


        /// HOME ////

        //TODO load optional home layout from theme
        mHomeLayout = findViewById(R.id.abs__home_wrapper);

        //Try to load the logo from the theme
        mLogo = attrsActionBar.getDrawable(R.styleable.SherlockTheme_abLogo);
        /*
        if ((mLogo == null) && (context instanceof Activity)) {
            //LOGO LOADING DOES NOT WORK
            //SEE: http://stackoverflow.com/questions/6105504/load-activity-and-or-application-logo-programmatically-from-manifest
            //SEE: https://groups.google.com/forum/#!topic/android-developers/UFR4l0ZwJWc
        }
        */

        //Try to load the icon from the theme
        mIcon = attrsActionBar.getDrawable(R.styleable.SherlockTheme_abIcon);
        if ((mIcon == null) && (context instanceof Activity)) {
            mIcon = appInfo.loadIcon(pm);
        }

        mHomeAsUpView = findViewById(R.id.abs__up);
        mIconView = (ImageView)findViewById(R.id.abs__home);


        //// NAVIGATION ////

        mListView = (Spinner)findViewById(R.id.abs__nav_list);
        mTabsView = (LinearLayout)findViewById(R.id.abs__nav_tabs);


        //// CUSTOM VIEW ////

        mCustomView = (FrameLayout)findViewById(R.id.abs__custom);

        //Try to load a custom view from the theme. This will NOT automatically
        //trigger the visibility of the custom layout, however.
        final int customViewResourceId = attrsActionBar.getResourceId(R.styleable.SherlockTheme_abCustomNavigationLayout, 0);
        if (customViewResourceId != 0) {
            setCustomView(customViewResourceId);
        }




        mActionsView = (LinearLayout)findViewById(R.id.abs__actions);
        mDivider = attrsActionBar.getDrawable(R.styleable.SherlockTheme_abDivider);

        mIndeterminateProgress = (ProgressBar)findViewById(R.id.abs__iprogress);

        //Try to get the display options defined in the theme, or fall back to
        //displaying the title and home icon
        setDisplayOptions(attrsActionBar.getInteger(R.styleable.SherlockTheme_abDisplayOptions, DEFAULT_DISPLAY_OPTIONS));

        //Try to get the navigation defined in the theme, or, fall back to
        //use standard navigation by default
        setNavigationMode(attrsActionBar.getInteger(R.styleable.SherlockTheme_abNavigationMode, DEFAULT_NAVIGATION_MODE));


        //Reduce, Reuse, Recycle!
        attrsActionBar.recycle();
        mIsConstructing = false;

        mLogoNavItem = new ActionMenuItem(context, 0, android.R.id.home, 0, 0, mTitle);
        mHomeLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (context instanceof Activity) {
                    ((Activity)context).onMenuItemSelected(Window.FEATURE_OPTIONS_PANEL, mLogoNavItem);
                }
            }
        });
        mHomeLayout.setClickable(true);
        mHomeLayout.setFocusable(true);

        reloadDisplay();
    }



    // ------------------------------------------------------------------------
    // HELPER METHODS
    // ------------------------------------------------------------------------

    /**
     * Helper to get a boolean value for a specific flag.
     *
     * @param flag Target flag.
     * @return Value.
     */
    private boolean getDisplayOptionValue(int flag) {
        return (mFlags & flag) == flag;
    }

    /**
     * Reload the current action bar display state.
     */
    private void reloadDisplay() {
        if (mIsConstructing) {
            return; //Do not run if we are in the constructor
        }

        final boolean isStandard = mNavigationMode == ActionBar.NAVIGATION_MODE_STANDARD;
        final boolean isList = mNavigationMode == ActionBar.NAVIGATION_MODE_LIST;
        final boolean isTab = mNavigationMode == ActionBar.NAVIGATION_MODE_TABS;
        final boolean hasSubtitle = (mSubtitleLayout.getText() != null) && !mSubtitleLayout.getText().equals("");
        final boolean displayHome = getDisplayOptionValue(ActionBar.DISPLAY_SHOW_HOME);
        final boolean displayHomeAsUp = getDisplayOptionValue(ActionBar.DISPLAY_HOME_AS_UP);
        final boolean displayTitle = getDisplayOptionValue(ActionBar.DISPLAY_SHOW_TITLE);
        final boolean displayCustom = getDisplayOptionValue(ActionBar.DISPLAY_SHOW_CUSTOM);
        final boolean displayLogo = getDisplayOptionValue(ActionBar.DISPLAY_USE_LOGO) && (mLogo != null);

        mHomeLayout.setVisibility(displayHome ? View.VISIBLE : View.GONE);
        if (displayHome) {
            mHomeAsUpView.setVisibility(displayHomeAsUp ? View.VISIBLE : View.GONE);
            mIconView.setImageDrawable(displayLogo ? mLogo : mIcon);
        }

        //Only show list if we are in list navigation and there are list items
        mListView.setVisibility(isList ? View.VISIBLE : View.GONE);

        // Show tabs if in tabs navigation mode.
        mTabsView.setVisibility(isTab ? View.VISIBLE : View.GONE);

        //Show title view if we are not in list navigation, not showing custom
        //view, and the show title flag is true
        mTitleLayout.setVisibility(isStandard && !displayCustom && displayTitle ? View.VISIBLE : View.GONE);
        //Show subtitle view if we are not in list navigation, not showing
        //custom view, show title flag is true, and a subtitle is set
        mSubtitleLayout.setVisibility(isStandard && !displayCustom && displayTitle && hasSubtitle ? View.VISIBLE : View.GONE);
        //Show custom view if we are not in list navigation and showing custom
        //flag is set
        mCustomView.setVisibility(isStandard && displayCustom ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------------------
    // ACTION BAR API
    // ------------------------------------------------------------------------

    public void addTab(ActionBar.Tab tab) {
        final int tabCount = getTabCount();
        addTab(tab, tabCount, tabCount == 0);
    }

    public void addTab(ActionBar.Tab tab, boolean setSelected) {
        addTab(tab, getTabCount(), setSelected);
    }

    public void addTab(ActionBar.Tab tab, int position) {
        addTab(tab, position, getTabCount() == 0);
    }

    public void addTab(ActionBar.Tab tab, int position, boolean setSelected) {
        mTabsView.addView(((TabImpl)tab).mView, position);
        if (setSelected) {
            tab.select();
        }
    }

    public View getCustomView() {
        return mCustomView.getChildAt(0);
    }

    public int getDisplayOptions() {
        return mFlags;
    }

    //public int getHeight();

    public int getNavigationItemCount() {
        if (mNavigationMode == ActionBar.NAVIGATION_MODE_LIST) {
            return mListView.getCount();
        }
        if (mNavigationMode == ActionBar.NAVIGATION_MODE_TABS) {
            return mTabsView.getChildCount();
        }
        return 0;
    }

    public int getNavigationMode() {
        return mNavigationMode;
    }

    public int getSelectedNavigationIndex() {
        if (mNavigationMode == ActionBar.NAVIGATION_MODE_LIST) {
            return mListView.getSelectedItemPosition();
        }
        if (mNavigationMode == ActionBar.NAVIGATION_MODE_TABS) {
            final int count = mTabsView.getChildCount();
            for (int i = 0; i < count; i++) {
                if (((TabImpl)mTabsView.getChildAt(i).getTag()).mView.isSelected()) {
                    return i;
                }
            }
        }
        return -1;
    }

    public TabImpl getSelectedTab() {
        final int count = mTabsView.getChildCount();
        for (int i = 0; i < count; i++) {
            TabImpl tab = (TabImpl)mTabsView.getChildAt(i).getTag();
            if (tab.mView.isSelected()) {
                return tab;
            }
        }
        return null;
    }

    public CharSequence getSubtitle() {
        if ((mNavigationMode == ActionBar.NAVIGATION_MODE_STANDARD) && !mSubtitleLayout.getText().equals("")) {
            return mSubtitleLayout.getText();
        } else {
            return null;
        }
    }

    public TabImpl getTabAt(int index) {
        View view = mTabsView.getChildAt(index);
        return (view != null) ? (TabImpl)view.getTag() : null;
    }

    public int getTabCount() {
        return mTabsView.getChildCount();
    }

    public CharSequence getTitle() {
        if ((mNavigationMode == ActionBar.NAVIGATION_MODE_STANDARD) && !mTitleLayout.getText().equals("")) {
            return mTitleLayout.getText();
        } else {
            return null;
        }
    }

    public boolean isShowing() {
        return getVisibility() == View.VISIBLE;
    }

    public TabImpl newTab() {
        return new TabImpl(this);
    }

    public void removeAllTabs() {
        TabImpl selected = getSelectedTab();
        if (selected != null) {
            selected.unselect();
        }
        mTabsView.removeAllViews();
    }

    public void removeTab(ActionBar.Tab tab) {
        final int count = mTabsView.getChildCount();
        for (int i = 0; i < count; i++) {
            TabImpl existingTab = (TabImpl)mTabsView.getChildAt(i).getTag();
            if (existingTab.equals(tab)) {
                removeTabAt(i);
                break;
            }
        }
    }

    public void removeTabAt(int position) {
        TabImpl tab = (TabImpl)getTabAt(position);
        if (tab != null) {
            tab.unselect();
            mTabsView.removeViewAt(position);

            if (position > 0) {
                //Select previous tab
                ((TabImpl)mTabsView.getChildAt(position - 1).getTag()).select();
            } else if (mTabsView.getChildCount() > 0) {
                //Select first tab
                ((TabImpl)mTabsView.getChildAt(0).getTag()).select();
            }
        }
    }

    //public void setBackgroundDrawable(Drawable d);

    public void setCustomView(int resId) {
        mCustomView.removeAllViews();
        LayoutInflater.from(getContext()).inflate(resId, mCustomView, true);
    }

    public void setCustomView(View view) {
        mCustomView.removeAllViews();
        mCustomView.addView(view);
    }

    public void setCustomView(View view, ActionBar.LayoutParams layoutParams) {
        view.setLayoutParams(layoutParams);
        setCustomView(view);
    }

    public void setDisplayHomeAsUpEnabled(boolean showHomeAsUp) {
        setDisplayOptions(showHomeAsUp ? ActionBar.DISPLAY_HOME_AS_UP : 0, ActionBar.DISPLAY_HOME_AS_UP);
    }

    public void setDisplayOptions(int options, int mask) {
        mFlags = (mFlags & ~mask) | options;
        reloadDisplay();
    }

    public void setDisplayOptions(int options) {
        mFlags = options;
        reloadDisplay();
    }

    public void setDisplayShowCustomEnabled(boolean showCustom) {
        setDisplayOptions(showCustom ? ActionBar.DISPLAY_SHOW_CUSTOM : 0, ActionBar.DISPLAY_SHOW_CUSTOM);
    }

    public void setDisplayShowHomeEnabled(boolean showHome) {
        setDisplayOptions(showHome ? ActionBar.DISPLAY_SHOW_HOME : 0, ActionBar.DISPLAY_SHOW_HOME);
    }

    public void setDisplayShowTitleEnabled(boolean showTitle) {
        setDisplayOptions(showTitle ? ActionBar.DISPLAY_SHOW_TITLE : 0, ActionBar.DISPLAY_SHOW_TITLE);
    }

    public void setDisplayUseLogoEnabled(boolean useLogo) {
        setDisplayOptions(useLogo ? ActionBar.DISPLAY_USE_LOGO : 0, ActionBar.DISPLAY_USE_LOGO);
    }

    public void setProgressBarIndeterminateVisibility(boolean visible) {
        mIndeterminateProgress.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void setListNavigationCallbacks(SpinnerAdapter adapter, final ActionBar.OnNavigationListener callback) {
        mListView.setAdapter(adapter);
        mListView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long itemId) {
                if (callback != null) {
                    callback.onNavigationItemSelected(position, itemId);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {}
        });

        reloadDisplay();
    }

    public void setNavigationMode(int mode) {
        if ((mode != ActionBar.NAVIGATION_MODE_STANDARD) && (mode != ActionBar.NAVIGATION_MODE_LIST)
                && (mode != ActionBar.NAVIGATION_MODE_TABS)) {
            throw new IllegalArgumentException("Unknown navigation mode value " + Integer.toString(mode));
        }

        if (mode != mNavigationMode) {
            mNavigationMode = mode;
            reloadDisplay();
        }
    }

    public void setSelectedNavigationItem(int position) {
        if (mNavigationMode == ActionBar.NAVIGATION_MODE_TABS) {
            ActionBar.Tab tab = getTabAt(position);
            if (tab != null) {
                tab.select();
            }
        } else if (mNavigationMode == ActionBar.NAVIGATION_MODE_LIST) {
            mListView.setSelection(position);
        }
    }

    public void selectTab(ActionBar.Tab tab) {
        final int count = mTabsView.getChildCount();
        for (int i = 0; i < count; i++) {
            TabImpl existingTab = (TabImpl)mTabsView.getChildAt(i).getTag();
            if (existingTab.equals(tab)) {
                existingTab.select();
                break;
            }
        }
    }

    public void setSubtitle(CharSequence subtitle) {
        mSubtitleLayout.setText((subtitle == null) ? "" : subtitle);
        reloadDisplay();
    }

    public void setSubtitle(int resId) {
        mSubtitleLayout.setText(resId);
        reloadDisplay();
    }

    public void setTitle(CharSequence title) {
        mTitleLayout.setText((title == null) ? "" : title);
    }

    public void setTitle(int resId) {
        mTitleLayout.setText(resId);
    }

    // ------------------------------------------------------------------------
    // ACTION ITEMS SUPPORT
    // ------------------------------------------------------------------------

    public ActionMenuItemView newItem() {
        ActionMenuItemView item = (ActionMenuItemView)LayoutInflater.from(getContext()).inflate(R.layout.abs__action_bar_item_layout, mActionsView, false);
        return item;
    }

    public void addItem(ActionMenuItemView item) {
        if (mDivider != null) {
            ImageView divider = new ImageView(getContext());
            divider.setImageDrawable(mDivider);
            divider.setScaleType(ImageView.ScaleType.FIT_XY);

            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.FILL_PARENT
            );

            mActionsView.addView(divider, dividerParams);
            item.setDivider(divider);
        }

        mActionsView.addView(item);
    }

    public void removeAllItems() {
        mActionsView.removeAllViews();
    }

    // ------------------------------------------------------------------------
    // HELPER INTERFACES AND HELPER CLASSES
    // ------------------------------------------------------------------------

    private static class TabImpl implements ActionBar.Tab {
        private static final View.OnClickListener clickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((TabImpl)v.getTag()).select();
            }
        };

        final ActionBarView mActionBar;
        final View mView;
        final ImageView mIconView;
        final TextView mTextView;
        final FrameLayout mCustomView;

        ActionBar.TabListener mListener;
        Object mTag;


        TabImpl(ActionBarView actionBar) {
            mActionBar = actionBar;
            mView = LayoutInflater.from(mActionBar.getContext()).inflate(R.layout.abs__action_bar_tab_layout, actionBar.mTabsView, false);
            mView.setTag(this);
            mView.setOnClickListener(clickListener);

            mIconView = (ImageView)mView.findViewById(R.id.abs__tab_icon);
            mTextView = (TextView)mView.findViewById(R.id.abs__tab);
            mCustomView = (FrameLayout)mView.findViewById(R.id.abs__tab_custom);
        }

        /**
         * Update display to reflect current property state.
         */
        void reloadDisplay() {
            boolean hasCustom = mCustomView.getChildCount() > 0;
            mIconView.setVisibility(hasCustom ? View.GONE : View.VISIBLE);
            mTextView.setVisibility(hasCustom ? View.GONE : View.VISIBLE);
            mCustomView.setVisibility(hasCustom ? View.VISIBLE : View.GONE);
        }

        @Override
        public View getCustomView() {
            return mCustomView.getChildAt(0);
        }

        @Override
        public Drawable getIcon() {
            return mIconView.getDrawable();
        }

        @Override
        public int getPosition() {
            final int count = mActionBar.mTabsView.getChildCount();
            for (int i = 0; i < count; i++) {
                if (mActionBar.mTabsView.getChildAt(i).getTag().equals(this)) {
                    return i;
                }
            }
            return -1;
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
            return mTextView.getText();
        }

        @Override
        public TabImpl setCustomView(int layoutResId) {
            mCustomView.removeAllViews();
            LayoutInflater.from(mActionBar.getContext()).inflate(layoutResId, mCustomView, true);
            reloadDisplay();
            return this;
        }

        @Override
        public TabImpl setCustomView(View view) {
            mCustomView.removeAllViews();
            if (view != null) {
                mCustomView.addView(view);
            }
            reloadDisplay();
            return this;
        }

        @Override
        public TabImpl setIcon(Drawable icon) {
            mIconView.setImageDrawable(icon);
            return this;
        }

        @Override
        public TabImpl setIcon(int resId) {
            mIconView.setImageResource(resId);
            return this;
        }

        @Override
        public TabImpl setTabListener(ActionBar.TabListener listener) {
            mListener = listener;
            return this;
        }

        @Override
        public TabImpl setTag(Object obj) {
            mTag = obj;
            return this;
        }

        @Override
        public TabImpl setText(int resId) {
            mTextView.setText(resId);
            return this;
        }

        @Override
        public TabImpl setText(CharSequence text) {
            mTextView.setText(text);
            return this;
        }

        @Override
        public void select() {
            if (mView.isSelected()) {
                if (mListener != null) {
                    mListener.onTabReselected(this, null);
                }
                return;
            }

            TabImpl current = mActionBar.getSelectedTab();
            if (current != null) {
                current.unselect();
            }

            mView.setSelected(true);
            if (mListener != null) {
                mListener.onTabSelected(this, null);
            }
        }

        /**
         * Unselect this tab. Only valid if the tab has been added to the
         * action bar and was previously selected.
         */
        void unselect() {
            if (mView.isSelected()) {
                mView.setSelected(false);

                if (mListener != null) {
                    mListener.onTabUnselected(this, null);
                }
            }
        }
    }
}
