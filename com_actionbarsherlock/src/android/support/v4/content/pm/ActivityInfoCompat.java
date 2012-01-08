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

package android.support.v4.content.pm;

/**
 * Helper for accessing features in {@link android.content.pm.ActivityInfo}
 * introduced after API level 4 in a backwards compatible fashion.
 */
public class ActivityInfoCompat {

    private ActivityInfoCompat() {
        /* Hide constructor */
    }

    /**
     * Bit in ActivityInfo#configChanges that indicates that the
     * activity can itself handle the ui mode. Set from the
     * {@link android.R.attr#configChanges} attribute.
     */
    public static final int CONFIG_UI_MODE = 0x0200;
}
