/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.support.v4.widget;

import android.content.Context;
import android.os.Build;
import android.view.View;

/**
 * Helper for accessing features in {@link android.widget.SearchView}
 * introduced after API level 4 in a backwards compatible fashion.
 */
public class SearchViewCompat {

    interface SearchViewCompatImpl {
        View newSearchView(Context context);
        Object newOnQueryTextListener(OnQueryTextListenerCompat listener);
        void setOnQueryTextListener(Object searchView, Object listener);
    }

    static class SearchViewCompatStubImpl implements SearchViewCompatImpl {

        @Override
        public View newSearchView(Context context) {
            return null;
        }

        @Override
        public Object newOnQueryTextListener(OnQueryTextListenerCompat listener) {
            return null;
        }

        @Override
        public void setOnQueryTextListener(Object searchView, Object listener) {

        }
    }

    static class SearchViewCompatHoneycombImpl extends SearchViewCompatStubImpl {

        @Override
        public View newSearchView(Context context) {
            return SearchViewCompatHoneycomb.newSearchView(context);
        }

        @Override
        public Object newOnQueryTextListener(final OnQueryTextListenerCompat listener) {
            return SearchViewCompatHoneycomb.newOnQueryTextListener(
                    new SearchViewCompatHoneycomb.OnQueryTextListenerCompatBridge() {
                        @Override
                        public boolean onQueryTextSubmit(String query) {
                            return listener.onQueryTextSubmit(query);
                        }
                        @Override
                        public boolean onQueryTextChange(String newText) {
                            return listener.onQueryTextChange(newText);
                        }
                    });
        }

        @Override
        public void setOnQueryTextListener(Object searchView, Object listener) {
            SearchViewCompatHoneycomb.setOnQueryTextListener(searchView, listener);
        }
    }

    private static final SearchViewCompatImpl IMPL;

    static {
        if (Build.VERSION.SDK_INT >= 11) { // Honeycomb
            IMPL = new SearchViewCompatHoneycombImpl();
        } else {
            IMPL = new SearchViewCompatStubImpl();
        }
    }

    private SearchViewCompat(Context context) {
        /* Hide constructor */
    }

    /**
     * Creates a new SearchView.
     *
     * @param context The Context the view is running in.
     * @return A SearchView instance if the class is present on the current
     *         platform, null otherwise.
     */
    public static View newSearchView(Context context) {
        return IMPL.newSearchView(context);
    }

    /**
     * Sets a listener for user actions within the SearchView.
     *
     * @param searchView The SearchView in which to register the listener.
     * @param listener the listener object that receives callbacks when the user performs
     *     actions in the SearchView such as clicking on buttons or typing a query.
     */
    public static void setOnQueryTextListener(View searchView, OnQueryTextListenerCompat listener) {
        IMPL.setOnQueryTextListener(searchView, listener.mListener);
    }

    /**
     * Callbacks for changes to the query text.
     */
    public static abstract class OnQueryTextListenerCompat {
        final Object mListener;

        public OnQueryTextListenerCompat() {
            mListener = IMPL.newOnQueryTextListener(this);
        }

        /**
         * Called when the user submits the query. This could be due to a key press on the
         * keyboard or due to pressing a submit button.
         * The listener can override the standard behavior by returning true
         * to indicate that it has handled the submit request. Otherwise return false to
         * let the SearchView handle the submission by launching any associated intent.
         *
         * @param query the query text that is to be submitted
         *
         * @return true if the query has been handled by the listener, false to let the
         * SearchView perform the default action.
         */
        public boolean onQueryTextSubmit(String query) {
            return false;
        }

        /**
         * Called when the query text is changed by the user.
         *
         * @param newText the new content of the query text field.
         *
         * @return false if the SearchView should perform the default action of showing any
         * suggestions if available, true if the action was handled by the listener.
         */
        public boolean onQueryTextChange(String newText) {
            return false;
        }
    }
}
