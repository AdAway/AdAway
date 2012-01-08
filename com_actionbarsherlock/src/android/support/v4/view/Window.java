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

import android.content.Context;

/**
 * <p>Abstract base class for a top-level window look and behavior policy. An
 * instance of this class should be used as the top-level view added to the
 * window manager. It provides standard UI policies such as a background, title
 * area, default key processing, etc.</p>
 *
 * <p>The only existing implementation of this abstract class is
 * android.policy.PhoneWindow, which you should instantiate when needing a
 * Window. Eventually that class will be refactored and a factory method added
 * for creating Window instances without knowing about a particular
 * implementation.</p>
 */
public abstract class Window extends android.view.Window {
    /*
     * We use long values so that we can intercept the call to
     * requestWindowFeature in our Activity.
     */

    /**
     * Flag for enabling the Action Bar. This is enabled by default for some
     * devices. The Action Bar replaces the title bar and provides an alternate
     * location for an on-screen menu button on some devices.
     */
    public static final long FEATURE_ACTION_BAR = android.view.Window.FEATURE_ACTION_BAR;

    /**
     * Flag for requesting an Action Bar that overlays window content. Normally
     * an Action Bar will sit in the space above window content, but if this
     * feature is requested along with {@link #FEATURE_ACTION_BAR} it will be
     * layered over the window content itself. This is useful if you would like
     * your app to have more control over how the Action Bar is displayed, such
     * as letting application content scroll beneath an Action Bar with a
     * transparent background or otherwise displaying a transparent/translucent
     * Action Bar over application content.
     */
    public static final long FEATURE_ACTION_BAR_OVERLAY = android.view.Window.FEATURE_ACTION_BAR_OVERLAY;

    /**
     * Flag for specifying the behavior of action modes when an Action Bar is
     * not present. If overlay is enabled, the action mode UI will be allowed to
     * cover existing window content.
     */
    public static final long FEATURE_ACTION_MODE_OVERLAY = android.view.Window.FEATURE_ACTION_MODE_OVERLAY;

    /**
     * Flag for indeterminate progress .
     */
    public static final long FEATURE_INDETERMINATE_PROGRESS = android.view.Window.FEATURE_INDETERMINATE_PROGRESS;



    /**
     * Create a new instance for a context.
     *
     * @param context Context.
     */
    public Window(Context context) {
        super(context);
    }
}
