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

package android.support.v4.app;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.v4.view.ActionMode;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.SpinnerAdapter;

/**
 * This is the public interface to the contextual ActionBar. The ActionBar acts
 * as a replacement for the title bar in Activities. It provides facilities for
 * creating toolbar actions as well as methods of navigating around an
 * application.
 */
public abstract class ActionBar {
    // ------------------------------------------------------------------------
    // ACTION MODE SUPPORT
    // ------------------------------------------------------------------------

    protected abstract ActionMode startActionMode(ActionMode.Callback callback);

    // ------------------------------------------------------------------------
    // ACTION BAR SUPPORT
    // ------------------------------------------------------------------------

    /**
     * Per-child layout information associated with action bar custom views.
     */
    public static class LayoutParams extends MarginLayoutParams {
        /**
         * Gravity for the view associated with these LayoutParams.
         *
         * @see android.view.Gravity
         */
        public int gravity = -1;

        public LayoutParams() {
            this(-1);
        }
        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }
        public LayoutParams(int width, int height) {
            super(width, height);
        }
        public LayoutParams(int width, int height, int gravity) {
            this(width, height);
            this.gravity = gravity;
        }
        public LayoutParams(int gravity) {
            this(0, 0, gravity);
        }
        public LayoutParams(LayoutParams source) {
            super(source);
            this.gravity = source.gravity;
        }
        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }
    }

    /**
     * Listener for receiving events when action bar menus are shown or hidden.
     */
    public interface OnMenuVisibilityListener {
        /**
         * Called when an action bar menu is shown or hidden. Applications may
         * want to use this to tune auto-hiding behavior for the action bar or
         * pause/resume video playback, gameplay, or other activity within the
         * main content area.
         *
         * @param isVisible True if an action bar menu is now visible, false if
         * no action bar menus are visible.
         */
        void onMenuVisibilityChanged(boolean isVisible);
    }

    /**
     * Listener interface for ActionBar navigation events.
     */
    public interface OnNavigationListener {
        /**
         * This method is called whenever a navigation item in your action bar
         * is selected.
         *
         * @param itemPosition Position of the item clicked.
         * @param itemId ID of the item clicked.
         * @return True if the event was handled, false otherwise.
         */
        boolean onNavigationItemSelected(int itemPosition, long itemId);
    }

    /**
     * <p>A tab in the action bar.</p>
     *
     * <p>Tabs manage the hiding and showing of
     * {@link android.support.v4.app.Fragment}.</p>
     */
    public static abstract class Tab {
        /**
         * An invalid position for a tab.
         *
         * @see #getPosition()
         */
        public static int INVALID_POSITION = android.app.ActionBar.Tab.INVALID_POSITION;


        /**
         * Retrieve a previously set custom view for this tab.
         *
         * @return The custom view set by {@link #setCustomView(View)}.
         */
        public abstract View getCustomView();

        /**
         * Return the icon associated with this tab.
         *
         * @return The tab's icon
         */
        public abstract Drawable getIcon();

        /**
         * Return the current position of this tab in the action bar.
         *
         * @return Current position, or {@link #INVALID_POSITION} if this tab is
         * not currently in the action bar.
         */
        public abstract int getPosition();

        /**
         * Return the current tab listener.
         *
         * @return Tab listener.
         */
        public abstract ActionBar.TabListener getTabListener();

        /**
         * @return This Tab's tag object.
         */
        public abstract Object getTag();

        /**
         * Return the text of this tab.
         *
         * @return The tab's text
         */
        public abstract CharSequence getText();

        /**
         * Select this tab. Only valid if the tab has been added to the action
         * bar.
         */
        public abstract void select();

        /**
         * Set a custom view to be used for this tab. This overrides values set
         * by {@link #setText(CharSequence)} and {@link #setIcon(Drawable)}.
         *
         * @param layoutResId A layout resource to inflate and use as a custom
         * tab view
         * @return The current instance for call chaining
         */
        public abstract ActionBar.Tab setCustomView(int layoutResId);

        /**
         * Set a custom view to be used for this tab. This overrides values set
         * by {@link #setText(CharSequence)} and {@link #setIcon(Drawable)}.
         *
         * @param view Custom view to be used as a tab.
         * @return The current instance for call chaining
         */
        public abstract ActionBar.Tab setCustomView(View view);

        /**
         * Set the icon displayed on this tab.
         *
         * @param icon The drawable to use as an icon
         * @return The current instance for call chaining
         */
        public abstract ActionBar.Tab setIcon(Drawable icon);

        /**
         * Set the icon displayed on this tab.
         *
         * @param resId Resource ID referring to the drawable to use as an icon
         * @return The current instance for call chaining
         */
        public abstract ActionBar.Tab setIcon(int resId);

        /**
         * Set the {@link ActionBar.TabListener} that will handle switching to
         * and from this tab. All tabs must have a TabListener set before being
         * added to the ActionBar.
         *
         * @param listener Listener to handle tab selection events
         * @return The current instance for call chaining
         */
        public abstract ActionBar.Tab setTabListener(ActionBar.TabListener listener);

        /**
         * Give this Tab an arbitrary object to hold for later use.
         *
         * @param obj Object to store
         * @return The current instance for call chaining
         */
        public abstract ActionBar.Tab setTag(Object obj);

        /**
         * Set the text displayed on this tab. Text may be truncated if there is
         * not room to display the entire string.
         *
         * @param resId A resource ID referring to the text that should be displayed
         * @return The current instance for call chaining
         */
        public abstract ActionBar.Tab setText(int resId);

        /**
         * Set the text displayed on this tab. Text may be truncated if there is
         * not room to display the entire string.
         *
         * @param text The text to display
         * @return The current instance for call chaining
         */
        public abstract ActionBar.Tab setText(CharSequence text);
    }

    /**
     * Callback interface invoked when a tab is focused, unfocused, added, or
     * removed.
     */
    public interface TabListener {
        /**
         * Called when a tab that is already selected is chosen again by the
         * user. Some applications may use this action to return to the top
         * level of a category.
         *
         * @param tab The tab that was reselected.
         * @param ft Unused, always {@code null}. Begin your own transaction by
         * calling {@link FragmentActivity#getSupportFragmentManager()}.
         */
        void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft);

        /**
         * Called when a tab enters the selected state.
         *
         * @param tab The tab that was selected
         * @param ft Unused, always {@code null}. Begin your own transaction by
         * calling {@link FragmentActivity#getSupportFragmentManager()}.
         */
        void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft);

        /**
         * Called when a tab exits the selected state.
         *
         * @param tab The tab that was unselected
         * @param ft Unused, always {@code null}. Begin your own transaction by
         * calling {@link FragmentActivity#getSupportFragmentManager()}.
         */
        void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft);
    }



    /**
     * Display the 'home' element such that it appears as an 'up' affordance.
     * e.g. show an arrow to the left indicating the action that will be taken.
     * Set this flag if selecting the 'home' button in the action bar to return
     * up by a single level in your UI rather than back to the top level or
     * front page.
     *
     * @see #setDisplayOptions(int)
     * @see #setDisplayOptions(int, int)
     */
    public static final int DISPLAY_HOME_AS_UP = android.app.ActionBar.DISPLAY_HOME_AS_UP;

    /**
     * Show the custom view if one has been set.
     *
     * @see #setCustomView(int)
     * @see #setCustomView(View)
     * @see #setCustomView(View, LayoutParams)
     * @see #setDisplayOptions(int)
     * @see #setDisplayOptions(int, int)
     */
    public static final int DISPLAY_SHOW_CUSTOM = android.app.ActionBar.DISPLAY_SHOW_CUSTOM;

    /**
     * Show 'home' elements in this action bar, leaving more space for other
     * navigation elements. This includes logo and icon.
     *
     * @see #setDisplayOptions(int)
     * @see #setDisplayOptions(int, int)
     */
    public static final int DISPLAY_SHOW_HOME = android.app.ActionBar.DISPLAY_SHOW_HOME;

    /**
     * Show the activity title and subtitle, if present.
     *
     * @see #setTitle(CharSequence)
     * @see #setTitle(int)
     * @see #setSubtitle(CharSequence)
     * @see #setSubtitle(int)
     * @see #setDisplayOptions(int)
     * @see #setDisplayOptions(int, int)
     */
    public static final int DISPLAY_SHOW_TITLE = android.app.ActionBar.DISPLAY_SHOW_TITLE;

    /**
     * Use logo instead of icon if available. This flag will cause appropriate
     * navigation modes to use a wider logo in place of the standard icon.
     *
     * @see #setDisplayOptions(int)
     * @see #setDisplayOptions(int, int)
     */
    public static final int DISPLAY_USE_LOGO = android.app.ActionBar.DISPLAY_USE_LOGO;

    /**
     * List navigation mode. Instead of static title text this mode presents a
     * list menu for navigation within the activity. e.g. this might be
     * presented to the user as a dropdown list.
     */
    public static final int NAVIGATION_MODE_LIST = android.app.ActionBar.NAVIGATION_MODE_LIST;

    /**
     * Standard navigation mode. Consists of either a logo or icon and title
     * text with an optional subtitle. Clicking any of these elements will
     * dispatch onOptionsItemSelected to the host Activity with a MenuItem with
     * item ID android.R.id.home.
     */
    public static final int NAVIGATION_MODE_STANDARD = android.app.ActionBar.NAVIGATION_MODE_STANDARD;

    /**
     * Tab navigation mode. Instead of static title text this mode presents a
     * series of tabs for navigation within the activity.
     */
    public static final int NAVIGATION_MODE_TABS = android.app.ActionBar.NAVIGATION_MODE_TABS;



    /**
     * Add a listener that will respond to menu visibility change events.
     *
     * @param listener The new listener to add
     */
    public abstract void addOnMenuVisibilityListener(ActionBar.OnMenuVisibilityListener listener);

    /**
     * Add a tab for use in tabbed navigation mode. The tab will be added at the
     * end of the list. If this is the first tab to be added it will become the
     * selected tab.
     *
     * @param tab Tab to add
     */
    public abstract void addTab(ActionBar.Tab tab);

    /**
     * Add a tab for use in tabbed navigation mode. The tab will be added at the
     * end of the list.
     *
     * @param tab Tab to add
     * @param setSelected True if the added tab should become the selected tab.
     */
    public abstract void addTab(ActionBar.Tab tab, boolean setSelected);

    /**
     * Add a tab for use in tabbed navigation mode. The tab will be inserted at
     * {@code position}. If this is the first tab to be added it will become the
     * selected tab.
     *
     * @param tab The tab to add
     * @param position The new position of the tab
     */
    public abstract void addTab(ActionBar.Tab tab, int position);

    /**
     * Add a tab for use in tabbed navigation mode. The tab will be insterted at
     * {@code position}.
     *
     * @param tab The tab to add
     * @param position The new position of the tab
     * @param setSelected True if the added tab should become the selected tab.
     */
    public abstract void addTab(ActionBar.Tab tab, int position, boolean setSelected);

    /**
     * @return The current custom view.
     */
    public abstract View getCustomView();

    /**
     * @return The current set of display options.
     */
    public abstract int getDisplayOptions();

    /**
     * Retrieve the current height of the ActionBar.
     *
     * @return The ActionBar's height
     */
    public abstract int getHeight();

    /**
     * Get the number of navigation items present in the current navigation
     * mode.
     *
     * @return Number of navigation items.
     */
    public abstract int getNavigationItemCount();

    /**
     * Returns the current navigation mode. The result will be one of:
     * <ul>
     * <li>{@link #NAVIGATION_MODE_STANDARD}</li>
     * <li>{@link #NAVIGATION_MODE_LIST}</li>
     * <li>{@link #NAVIGATION_MODE_TABS}</li>
     * </ul>
     *
     * @return The current navigation mode.
     * @see #setNavigationMode(int)
     */
    public abstract int getNavigationMode();

    /**
     * Get the position of the selected navigation item in list or tabbed
     * navigation modes.
     *
     * @return Position of the selected item.
     */
    public abstract int getSelectedNavigationIndex();

    /**
     * Returns the currently selected tab if in tabbed navigation mode and there
     * is at least one tab present.
     *
     * @return The currently selected tab or null.
     */
    public abstract ActionBar.Tab getSelectedTab();

    /**
     * Returns the current ActionBar subtitle in standard mode. Returns null if
     * {@link #getNavigationMode()} would not return
     * {@link #NAVIGATION_MODE_STANDARD}.
     *
     * @return The current ActionBar subtitle or null.
     */
    public abstract CharSequence getSubtitle();

    /**
     * Returns the tab at the specified index.
     *
     * @param index Index value in the range 0-get
     * @return Tab at specified index
     */
    public abstract ActionBar.Tab getTabAt(int index);

    /**
     * Returns the number of tabs currently registered with the action bar.
     *
     * @return Tab count
     */
    public abstract int getTabCount();

    /**
     * Returns the current ActionBar title in standard mode. Returns null if
     * {@link #getNavigationMode()} would not return
     * {@link #NAVIGATION_MODE_STANDARD}.
     *
     * @return The current ActionBar title or null.
     */
    public abstract CharSequence getTitle();

    /**
     * Hide the ActionBar if it is not currently showing. If the window hosting
     * the ActionBar does not have the feature
     * {@link android.support.v4.view.Window#FEATURE_ACTION_BAR_OVERLAY}
     * it will resize application content to fit the new space available.
     */
    public abstract void hide();

    /**
     * @return {@code true} if the ActionBar is showing, {@code false}
     * otherwise.
     */
    public abstract boolean isShowing();

    /**
     * Create and return a new ActionBar.Tab. This tab will not be included in
     * the action bar until it is added.
     *
     * @return A new Tab
     * @see #addTab(Tab)
     * @see #addTab(Tab, boolean)
     * @see #addTab(Tab, int)
     * @see #addTab(Tab, int, boolean)
     */
    public abstract ActionBar.Tab newTab();

    /**
     * Remove all tabs from the action bar and deselect the current tab.
     */
    public abstract void removeAllTabs();

    /**
     * Remove a menu visibility listener. This listener will no longer receive
     * menu visibility change events.
     *
     * @param listener A listener to remove that was previously added
     */
    public abstract void removeOnMenuVisibilityListener(ActionBar.OnMenuVisibilityListener listener);

    /**
     * Remove a tab from the action bar. If the removed tab was selected it will
     * be deselected and another tab will be selected if present.
     *
     * @param tab The tab to remove
     */
    public abstract void removeTab(ActionBar.Tab tab);

    /**
     * Remove a tab from the action bar. If the removed tab was selected it will
     * be deselected and another tab will be selected if present.
     *
     * @param position Position of the tab to remove
     */
    public abstract void removeTabAt(int position);

    /**
     * <p>Select the specified tab. If it is not a child of this action bar it
     * will be added.</p>
     *
     * <p>Note: If you want to select by index, use
     * {@link #setSelectedNavigationItem(int)}.</p>
     *
     * @param tab Tab to select
     */
    public abstract void selectTab(ActionBar.Tab tab);

    /**
     * Set the ActionBar's background.
     *
     * @param d Background drawable
     */
    public abstract void setBackgroundDrawable(Drawable d);

    /**
     * <p>Set the action bar into custom navigation mode, supplying a view for
     * custom navigation.</p>
     *
     * <p>Custom navigation views appear between the application icon and any
     * action buttons and may use any space available there. Common use cases
     * for custom navigation views might include an auto-suggesting address bar
     * for a browser or other navigation mechanisms that do not translate well
     * to provided navigation modes.</p>
     *
     * <p>The display option {@link #DISPLAY_SHOW_CUSTOM} must be set for the
     * custom view to be displayed.</p>
     *
     * @param resId Resource ID of a layout to inflate into the ActionBar.
     * @see #setDisplayOptions(int, int)
     */
    public abstract void setCustomView(int resId);

    /**
     * Set the action bar into custom navigation mode, supplying a view for
     * custom navigation. Custom navigation views appear between the application
     * icon and any action buttons and may use any space available there. Common
     * use cases for custom navigation views might include an auto-suggesting
     * address bar for a browser or other navigation mechanisms that do not
     * translate well to provided navigation modes.
     *
     * @param view Custom  navigation view to place in the ActionBar.
     */
    public abstract void setCustomView(View view);

    /**
     * <p>Set the action bar into custom navigation mode, supplying a view for
     * custom navigation.</p>
     *
     * <p>Custom navigation views appear between the application icon and any
     * action buttons and may use any space available there. Common use cases
     * for custom navigation views might include an auto-suggesting address bar
     * for a browser or other navigation mechanisms that do not translate well
     * to provided navigation modes.</p>
     *
     * <p>The display option {@link #DISPLAY_SHOW_CUSTOM} must be set for the
     * custom view to be displayed.</p>
     *
     * @param view Custom navigation view to place in the ActionBar.
     * @param layoutParams How this custom view should layout in the bar.
     * @see #setDisplayOptions(int, int)
     */
    public abstract void setCustomView(View view, ActionBar.LayoutParams layoutParams);

    /**
     * <p>Set whether home should be displayed as an "up" affordance. Set this
     * to true if selecting "home" returns up by a single level in your UI
     * rather than back to the top level or front page.</p>
     *
     * <p>To set several display options at once, see the setDisplayOptions
     * methods.</p>
     *
     * @param showHomeAsUp {@code true} to show the user that selecting home
     * will return one level up rather than to the top level of the app.
     * @see #setDisplayOptions(int)
     * @see #setDisplayOptions(int, int)
     */
    public abstract void setDisplayHomeAsUpEnabled(boolean showHomeAsUp);

    /**
     * <p>Set selected display options. Only the options specified by mask will
     * be changed. To change all display option bits at once, see
     * {@link #setDisplayOptions(int)}.
     *
     * <p>Example: {@code setDisplayOptions(0, DISPLAY_SHOW_HOME)} will disable
     * the {@link #DISPLAY_SHOW_HOME} option.
     * {@code setDisplayOptions(DISPLAY_SHOW_HOME, DISPLAY_SHOW_HOME | DISPLAY_USE_LOGO)}
     * will enable {@link #DISPLAY_SHOW_HOME} and disable
     * {@link #DISPLAY_USE_LOGO}.</p>
     *
     * @param options A combination of the bits defined by the DISPLAY_
     * constants defined in ActionBar.
     * @param mask A bit mask declaring which display options should be changed.
     */
    public abstract void setDisplayOptions(int options, int mask);

    /**
     * Set display options. This changes all display option bits at once. To
     * change a limited subset of display options, see
     * {@link #setDisplayOptions(int, int)}.
     *
     * @param options A combination of the bits defined by the DISPLAY_
     * constants defined in ActionBar.
     */
    public abstract void setDisplayOptions(int options);

    /**
     * <p>Set whether a custom view should be displayed, if set.</p>
     *
     * <p>To set several display options at once, see the setDisplayOptions
     * methods.</p>
     *
     * @param showCustom {@code true} if the currently set custom view should be
     * displayed, {@code false} otherwise.
     * @see #setDisplayOptions(int)
     * @see #setDisplayOptions(int, int)
     */
    public abstract void setDisplayShowCustomEnabled(boolean showCustom);

    /**
     * <p>Set whether to include the application home affordance in the action
     * bar. Home is presented as either an activity icon or logo.</p>
     *
     * <p>To set several display options at once, see the setDisplayOptions
     * methods.</p>
     *
     * @param showHome {@code true} to show home, {@code false} otherwise.
     * @see #setDisplayOptions(int)
     * @see #setDisplayOptions(int, int)
     */
    public abstract void setDisplayShowHomeEnabled(boolean showHome);

    /**
     * <p>Set whether an activity title/subtitle should be displayed.</p>
     *
     * <p>To set several display options at once, see the setDisplayOptions
     * methods.</p>
     *
     * @param showTitle {@code true} to display a title/subtitle if present.
     * @see #setDisplayOptions(int)
     * @see #setDisplayOptions(int, int)
     */
    public abstract void setDisplayShowTitleEnabled(boolean showTitle);

    /**
     * <p>Set whether to display the activity logo rather than the activity
     * icon. A logo is often a wider, more detailed image.</p>
     *
     * <p>To set several display options at once, see the setDisplayOptions
     * methods.</p>
     *
     * @param useLogo {@code true} to use the activity logo, {@code false} to
     * use the activity icon.
     * @see #setDisplayOptions(int)
     * @see #setDisplayOptions(int, int)
     */
    public abstract void setDisplayUseLogoEnabled(boolean useLogo);

    /**
     * Set the adapter and navigation callback for list navigation mode. The
     * supplied adapter will provide views for the expanded list as well as the
     * currently selected item. (These may be displayed differently.) The
     * supplied OnNavigationListener will alert the application when the user
     * changes the current list selection.
     *
     * @param adapter An adapter that will provide views both to display the
     * current navigation selection and populate views within the dropdown
     * navigation menu.
     * @param callback An OnNavigationListener that will receive events when the
     * user selects a navigation item.
     */
    public abstract void setListNavigationCallbacks(SpinnerAdapter adapter, ActionBar.OnNavigationListener callback);

    /**
     * Set the current navigation mode.
     *
     * @param mode The new mode to set.
     * @see #NAVIGATION_MODE_STANDARD
     * @see #NAVIGATION_MODE_LIST
     * @see #NAVIGATION_MODE_TABS
     */
    public abstract void setNavigationMode(int mode);

    /**
     * Set the selected navigation item in list or tabbed navigation modes.
     *
     * @param position Position of the item to select.
     */
    public abstract void setSelectedNavigationItem(int position);

    /**
     * Set the action bar's subtitle. This will only be displayed if
     * @link #DISPLAY_SHOW_TITLE} is set.
     *
     * @param resId Resource ID of subtitle string to set
     * @see #setSubtitle(CharSequence)
     * @see #setDisplayOptions(int, int)
     */
    public abstract void setSubtitle(int resId);

    /**
     * Set the action bar's subtitle. This will only be displayed if
     * @{link #DISPLAY_SHOW_TITLE} is set. Set to null to disable the subtitle
     * entirely.
     *
     * @param subtitle Subtitle to set
     * @see #setSubtitle(int)
     * @see #setDisplayOptions(int, int)
     */
    public abstract void setSubtitle(CharSequence subtitle);

    /**
     * Set the action bar's title. This will only be displayed if
     * @{link #DISPLAY_SHOW_TITLE} is set.
     *
     * @param title Title to set
     * @see #setTitle(int)
     * @see #setDisplayOptions(int, int)
     */
    public abstract void setTitle(CharSequence title);

    /**
     * Set the action bar's title. This will only be displayed if
     * {@link #DISPLAY_SHOW_TITLE} is set.
     *
     * @param resId Resource ID of title string to set
     * @see #setTitle(CharSequence)
     * @see #setDisplayOptions(int, int)
     */
    public abstract void setTitle(int resId);

    /**
     * Show the ActionBar if it is not currently showing. If the window hosting
     * the ActionBar does not have the feature
     * {@link android.support.v4.view.Window#FEATURE_ACTION_BAR_OVERLAY}
     * it will resize application content to fit the new space available.
     */
    public abstract void show();
}