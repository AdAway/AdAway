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

package android.support.v4.view;

import com.actionbarsherlock.internal.view.menu.MenuInflaterImpl;

import android.view.View;

/**
 * Represents a contextual mode of the user interface. Action modes can be used
 * for modal interactions with content and replace parts of the normal UI until
 * finished. Examples of good action modes include selection modes, search,
 * content editing, etc.
 */
public abstract class ActionMode {
    /**
     * <p>Callback interface for action modes. Supplied to
     * {@link android.support.v4.app.FragmentActivity#startActionMode(Callback)},
     * a Callback configures and handles events raised by a user's interaction
     * with an action mode.</p>
     *
     * <p>An action mode's lifecycle is as follows:
     * <ul>
     *  <li>{@link #onCreateActionMode(ActionMode, Menu)} once on initial
     *  creation</li>
     *  <li>{@link #onPrepareActionMode(ActionMode, Menu)} after creation and
     *  any time the ActionMode is invalidated</li>
     *  <li>{@link #onActionItemClicked(ActionMode, MenuItem)} any time a
     *  contextual action button is clicked</li>
     *  <li>{@link #onDestroyActionMode(ActionMode)} when the action mode is
     *  closed</li>
     * </ul>
     * </p>
     */
    public interface Callback {
        /**
         * Called to report a user click on an action button.
         *
         * @param mode The current ActionMode
         * @param item The item that was clicked
         * @return true if this callback handled the event, false if the
         * standard MenuItem invocation should continue.
         */
        boolean onActionItemClicked(ActionMode mode, MenuItem item);

        /**
         * Called when action mode is first created. The menu supplied will be
         * used to generate action buttons for the action mode.
         *
         * @param mode ActionMode being created
         * @param menu Menu used to populate action buttons
         * @return true if the action mode should be created, false if entering
         * this mode should be aborted.
         */
        boolean onCreateActionMode(ActionMode mode, Menu menu);

        /**
         * Called when an action mode is about to be exited and destroyed.
         *
         * @param mode The current ActionMode being destroyed
         */
        void onDestroyActionMode(ActionMode mode);

        /**
         * Called to refresh an action mode's action menu whenever it is
         * invalidated.
         *
         * @param mode ActionMode being prepared
         * @param menu Menu used to populate action buttons
         * @return true if the menu or action mode was updated, false otherwise.
         */
        boolean onPrepareActionMode(ActionMode mode, Menu menu);
    }

    /**
     * Finish and close this action mode. The action mode's
     * {@link ActionMode.Callback} will have its
     * {@link ActionMode.Callback#onDestroyActionMode(ActionMode)} method
     * called.
     */
    public abstract void finish();

    /**
     * Returns the current custom view for this action mode.
     *
     * @return The current custom view
     */
    public abstract View getCustomView();

    /**
     * Returns the menu of actions that this action mode presents.
     *
     * @return The action mode's menu.
     */
    public abstract Menu getMenu();

    /**
     * Returns a {@link MenuInflaterImpl} with the ActionMode's context.
     *
     * @return Menu inflater.
     */
    public abstract MenuInflaterImpl getMenuInflater();

    /**
     * Returns the current subtitle of this action mode.
     *
     * @return Subtitle text
     */
    public abstract CharSequence getSubtitle();

    /**
     * Returns the current title of this action mode.
     *
     * @return Title text
     */
    public abstract CharSequence getTitle();

    /**
     * Invalidate the action mode and refresh menu content. The mode's
     * {@link ActionMode.Callback} will have its
     * {@link ActionMode.Callback#onPrepareActionMode(ActionMode, Menu)} method
     * called. If it returns true the menu will be scanned for updated content
     * and any relevant changes will be reflected to the user.
     */
    public abstract void invalidate();

    /**
     * Set a custom view for this action mode. The custom view will take the
     * place of the title and subtitle. Useful for things like search boxes.
     *
     * @param view Custom view to use in place of the title/subtitle.
     * @see #setTitle(CharSequence)
     * @see #setSubtitle(CharSequence)
     */
    public abstract void setCustomView(View view);

    /**
     * Set the subtitle of the action mode. This method will have no visible
     * effect if a custom view has been set.
     *
     * @param resId Resource ID of a string to set as the subtitle
     * @see #setSubtitle(CharSequence)
     * @see #setCustomView(View)
     */
    public abstract void setSubtitle(int resId);

    /**
     * Set the subtitle of the action mode. This method will have no visible
     * effect if a custom view has been set.
     *
     * @param subtitle Subtitle string to set
     * @see #setSubtitle(int)
     * @see #setCustomView(View)
     */
    public abstract void setSubtitle(CharSequence subtitle);

    /**
     * Set the title of the action mode. This method will have no visible effect
     * if a custom view has been set.
     *
     * @param resId Resource ID of a string to set as the title
     * @see #setTitle(CharSequence)
     * @see #setCustomView(View)
     */
    public abstract void setTitle(int resId);

    /**
     * Set the title of the action mode. This method will have no visible effect
     * if a custom view has been set.
     *
     * @param title Title string to set
     * @see #setTitle(int)
     * @see #setCustomView(View)
     */
    public abstract void setTitle(CharSequence title);
}
