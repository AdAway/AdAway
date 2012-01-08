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

package android.support.v4.view;

import android.view.ViewConfiguration;

/**
 * Helper for accessing features in {@link ViewConfiguration}
 * introduced after API level 4 in a backwards compatible fashion.
 */
public class ViewConfigurationCompat {
    /**
     * Interface for the full API.
     */
    interface ViewConfigurationVersionImpl {
        public int getScaledPagingTouchSlop(ViewConfiguration config);
    }

    /**
     * Interface implementation that doesn't use anything about v4 APIs.
     */
    static class BaseViewConfigurationVersionImpl implements ViewConfigurationVersionImpl {
        @Override
        public int getScaledPagingTouchSlop(ViewConfiguration config) {
            return config.getScaledTouchSlop();
        }
    }

    /**
     * Interface implementation for devices with at least v11 APIs.
     */
    static class FroyoViewConfigurationVersionImpl implements ViewConfigurationVersionImpl {
        @Override
        public int getScaledPagingTouchSlop(ViewConfiguration config) {
            return ViewConfigurationCompatFroyo.getScaledPagingTouchSlop(config);
        }
    }

    /**
     * Select the correct implementation to use for the current platform.
     */
    static final ViewConfigurationVersionImpl IMPL;
    static {
        if (android.os.Build.VERSION.SDK_INT >= 11) {
            IMPL = new FroyoViewConfigurationVersionImpl();
        } else {
            IMPL = new BaseViewConfigurationVersionImpl();
        }
    }

    // -------------------------------------------------------------------

    /**
     * Call {@link ViewConfiguration#getScaledPagingTouchSlop()}.
     * If running on a pre-{@android.os.Build.VERSION_CODES#FROYO} device,
     * returns {@link ViewConfiguration#getScaledTouchSlop()}.
     */
    public static int getScaledPagingTouchSlop(ViewConfiguration config) {
        return IMPL.getScaledPagingTouchSlop(config);
    }
}
