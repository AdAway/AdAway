package com.actionbarsherlock.internal.widget;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.support.v4.app.ActionBar;
import android.support.v4.view.Menu;
import android.support.v4.view.Window;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.actionbarsherlock.internal.view.menu.ActionMenuPresenter;
import com.actionbarsherlock.internal.view.menu.ActionMenuView;
import com.actionbarsherlock.internal.view.menu.MenuBuilder;
import com.actionbarsherlock.internal.view.menu.MenuPresenter;

public final class ActionBarView extends RelativeLayout {
    /** Default display options if none are defined in the theme. */
    private static final int DEFAULT_DISPLAY_OPTIONS = ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_HOME;

    /** Default navigation mode if one is not defined in the theme. */
    private static final int DEFAULT_NAVIGATION_MODE = ActionBar.NAVIGATION_MODE_STANDARD;



    private final Context mContext;

    private final View mHomeAsUpView;
    private final ViewGroup mHomeLayout;
    private final ActionMenuItem mLogoNavItem;

    private final CharSequence mTitle;
    private final TextView mTitleLayout;

    private final CharSequence mSubtitle;
    private final TextView mSubtitleLayout;

    /** Indeterminate progress bar. */
    private final ProgressBar mIndeterminateProgress;
    private boolean mAllowsIndeterminateProgress = false;

    /** List view. */
    private final Spinner mSpinner;
    private SpinnerAdapter mSpinnerAdapter;
    private final AdapterView.OnItemSelectedListener mNavItemSelectedListener;
    private ActionBar.OnNavigationListener mCallback;

    /** Custom view parent. */
    private final FrameLayout mCustomView;
    private View mCustomNavView;

    private ImageView mIconView;
    private Drawable mLogo;
    private Drawable mIcon;

    /** Container for action item view. */
    private final FrameLayout mActionsView;
    private MenuBuilder mOptionsMenu;
    private ActionMenuView mMenuView;
    private ActionMenuPresenter mActionMenuPresenter;

    /** Container for all tab items. */
    private final LinearLayout mTabsView;
    private final ViewGroup mTabViewContainer;

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
    private int mDisplayOptions;

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
        mContext = context;
        mIsConstructing = true;
        LayoutInflater.from(context).inflate(R.layout.abs__action_bar, this, true);

        mNavItemSelectedListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                if (mCallback != null) {
                    mCallback.onNavigationItemSelected(arg2, arg3);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
                //No op
            }
        };

        setBackgroundResource(0);

        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SherlockTheme, defStyle, 0);
        final ApplicationInfo appInfo = context.getApplicationInfo();
        final PackageManager pm = context.getPackageManager();


        //// TITLE ////

        mTitleLayout = (TextView)findViewById(R.id.abs__action_bar_title);

        //Try to load title style from the theme
        final int titleTextStyle = a.getResourceId(R.styleable.SherlockTheme_abTitleTextStyle, 0);
        if (titleTextStyle != 0) {
            mTitleLayout.setTextAppearance(context, titleTextStyle);
        }

        //Try to load title from the theme
        mTitle = a.getString(R.styleable.SherlockTheme_abTitle);
        if (mTitle != null) {
            setTitle(mTitle);
        }


        //// SUBTITLE ////

        mSubtitleLayout = (TextView)findViewById(R.id.abs__action_bar_subtitle);

        //Try to load subtitle style from the theme
        final int subtitleTextStyle = a.getResourceId(R.styleable.SherlockTheme_abSubtitleTextStyle, 0);
        if (subtitleTextStyle != 0) {
            mSubtitleLayout.setTextAppearance(context, subtitleTextStyle);
        }

        //Try to load subtitle from theme
        mSubtitle = a.getString(R.styleable.SherlockTheme_abSubtitle);
        if (mSubtitle != null) {
            setSubtitle(mSubtitle);
        }


        /// HOME ////

        mHomeLayout = (ViewGroup)findViewById(R.id.abs__home_wrapper);
        final int homeLayoutResource = a.getResourceId(R.styleable.SherlockTheme_abHomeLayout, R.layout.abs__action_bar_home);
        LayoutInflater.from(context).inflate(homeLayoutResource, mHomeLayout, true);

        //Try to load the logo from the theme
        mLogo = a.getDrawable(R.styleable.SherlockTheme_abLogo);
        /*
        if ((mLogo == null) && (context instanceof Activity)) {
            //LOGO LOADING DOES NOT WORK
            //SEE: http://stackoverflow.com/questions/6105504/load-activity-and-or-application-logo-programmatically-from-manifest
            //SEE: https://groups.google.com/forum/#!topic/android-developers/UFR4l0ZwJWc
        }
        */

        //Try to load the icon from the theme
        mIcon = a.getDrawable(R.styleable.SherlockTheme_abIcon);
        if ((mIcon == null) && (context instanceof Activity)) {
            mIcon = appInfo.loadIcon(pm);
        }

        mHomeAsUpView = findViewById(R.id.abs__up);
        mIconView = (ImageView)findViewById(R.id.abs__home);


        //// NAVIGATION ////

        mSpinner = (Spinner)findViewById(R.id.abs__nav_list);
        mSpinner.setOnItemSelectedListener(mNavItemSelectedListener);

        mTabsView = (LinearLayout)findViewById(R.id.abs__nav_tabs);
        mTabViewContainer = (ViewGroup)findViewById(R.id.abs__nav_tabs_layout);


        //// CUSTOM VIEW ////

        mCustomView = (FrameLayout)findViewById(R.id.abs__custom);

        //Try to load a custom view from the theme. This will NOT automatically
        //trigger the visibility of the custom layout, however.
        final int customViewResourceId = a.getResourceId(R.styleable.SherlockTheme_abCustomNavigationLayout, 0);
        if (customViewResourceId != 0) {
            mCustomNavView = LayoutInflater.from(context).inflate(customViewResourceId, mCustomView, true);
            mNavigationMode = ActionBar.NAVIGATION_MODE_STANDARD;
            setDisplayOptions(mDisplayOptions | ActionBar.DISPLAY_SHOW_CUSTOM);
        }




        mActionsView = (FrameLayout)findViewById(R.id.abs__actions);

        mIndeterminateProgress = (ProgressBar)findViewById(R.id.abs__iprogress);

        //Try to get the display options defined in the theme, or fall back to
        //displaying the title and home icon
        setDisplayOptions(a.getInteger(R.styleable.SherlockTheme_abDisplayOptions, DEFAULT_DISPLAY_OPTIONS));

        //Try to get the navigation defined in the theme, or, fall back to
        //use standard navigation by default
        setNavigationMode(a.getInteger(R.styleable.SherlockTheme_abNavigationMode, DEFAULT_NAVIGATION_MODE));


        //Reduce, Reuse, Recycle!
        a.recycle();
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
        return (mDisplayOptions & flag) == flag;
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
        final boolean isTabUnderAb = isTab && getContext().getString(R.string.abs__tab_under_ab_tag).equals(mTabsView.getTag());
        final boolean hasSubtitle = (mSubtitleLayout.getText() != null) && !mSubtitleLayout.getText().equals("");
        final boolean displayHome = getDisplayOptionValue(ActionBar.DISPLAY_SHOW_HOME);
        final boolean displayHomeAsUp = getDisplayOptionValue(ActionBar.DISPLAY_HOME_AS_UP);
        final boolean displayTitle = getDisplayOptionValue(ActionBar.DISPLAY_SHOW_TITLE);
        final boolean displayCustom = getDisplayOptionValue(ActionBar.DISPLAY_SHOW_CUSTOM);
        final boolean displayLogo = getDisplayOptionValue(ActionBar.DISPLAY_USE_LOGO) && (mLogo != null);

        mHomeLayout.setVisibility(displayHome ? View.VISIBLE : View.GONE);
        if (displayHome) {
            if (mHomeAsUpView != null) {
                mHomeAsUpView.setVisibility(displayHomeAsUp ? View.VISIBLE : View.GONE);
            }
            if (mIconView != null) {
                mIconView.setImageDrawable(displayLogo ? mLogo : mIcon);
            }
        }

        //Only show list if we are in list navigation and there are list items
        mSpinner.setVisibility(isList ? View.VISIBLE : View.GONE);

        // Show tabs if in tabs navigation mode.
        mTabsView.setVisibility(isTab ? View.VISIBLE : View.GONE);
        if (mTabViewContainer != null) {
            mTabViewContainer.setVisibility(isTab ? View.VISIBLE : View.GONE);
        }

        //Show title view if we are not in list navigation, not showing custom
        //view, and the show title flag is true
        mTitleLayout.setVisibility((isStandard || isTabUnderAb) && !displayCustom && displayTitle ? View.VISIBLE : View.GONE);
        //Show subtitle view if we are not in list navigation, not showing
        //custom view, show title flag is true, and a subtitle is set
        mSubtitleLayout.setVisibility((isStandard || isTabUnderAb) && !displayCustom && displayTitle && hasSubtitle ? View.VISIBLE : View.GONE);
        //Show custom view if we are not in list navigation and showing custom
        //flag is set
        mCustomView.setVisibility(isStandard && displayCustom ? View.VISIBLE : View.GONE);
    }

    public void initIndeterminateProgress() {
        mAllowsIndeterminateProgress = true;
    }

    public void setMenu(Menu menu, MenuPresenter.Callback cb) {
        if (menu == mOptionsMenu) return;

        if (mOptionsMenu != null) {
            mOptionsMenu.removeMenuPresenter(mActionMenuPresenter);
        }

        MenuBuilder builder = (MenuBuilder) menu;
        mOptionsMenu = builder;
        if (mMenuView != null) {
            final ViewGroup oldParent = (ViewGroup) mMenuView.getParent();
            if (oldParent != null) {
                oldParent.removeView(mMenuView);
            }
        }
        if (mActionMenuPresenter == null) {
            mActionMenuPresenter = new ActionMenuPresenter(mContext);
            mActionMenuPresenter.setCallback(cb);
            mActionMenuPresenter.setId(R.id.abs__action_menu_presenter);
        }

        ActionMenuView menuView;
        final LayoutParams layoutParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.FILL_PARENT);
        configPresenters(builder);
        menuView = (ActionMenuView) mActionMenuPresenter.getMenuView(this);
        final ViewGroup oldParent = (ViewGroup) menuView.getParent();
        if (oldParent != null && oldParent != this) {
            oldParent.removeView(menuView);
        }
        mActionsView.addView(menuView, layoutParams);
        mMenuView = menuView;
    }

    private void configPresenters(MenuBuilder builder) {
        if (builder != null) {
            builder.addMenuPresenter(mActionMenuPresenter);
        } else {
            mActionMenuPresenter.initForMenu(mContext, null);
            mActionMenuPresenter.updateMenuView(true);
        }
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
        return mCustomNavView;
    }

    public int getDisplayOptions() {
        return mDisplayOptions;
    }

    public SpinnerAdapter getDropdownAdapter() {
        return this.mSpinnerAdapter;
    }

    public int getDropdownSelectedPosition() {
        return this.mSpinner.getSelectedItemPosition();
    }

    public int getNavigationMode() {
        return mNavigationMode;
    }

    public ActionBar.Tab getSelectedTab() {
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

    public ActionBar.Tab getTabAt(int index) {
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

    public TabImpl newTab() {
        return new TabImpl(this);
    }

    public void removeAllTabs() {
        TabImpl selected = (TabImpl)getSelectedTab();
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

    public void setCallback(ActionBar.OnNavigationListener callback) {
        mCallback = callback;
    }

    public void setCustomNavigationView(View view) {
        mCustomNavView = view;
        mCustomView.removeAllViews();
        mCustomView.addView(view);
    }

    public void setDisplayOptions(int options) {
        mDisplayOptions = options;
        reloadDisplay();
    }

    public void setDropdownAdapter(SpinnerAdapter spinnerAdapter) {
        mSpinnerAdapter = spinnerAdapter;
        if (mSpinner != null) {
            mSpinner.setAdapter(mSpinnerAdapter);
        }
    }

    public void setDropdownSelectedPosition(int position) {
        mSpinner.setSelection(position);
    }

    public void setProgressBarIndeterminateVisibility(boolean visible) {
        if (mAllowsIndeterminateProgress) {
            mIndeterminateProgress.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
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
    // HELPER INTERFACES AND HELPER CLASSES
    // ------------------------------------------------------------------------

    private static class TabImpl extends ActionBar.Tab {
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

            TabImpl current = (TabImpl)mActionBar.getSelectedTab();
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
