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

package android.support.v4.view.accessibility;

//import android.accessibilityservice.AccessibilityServiceInfo;
//import android.view.accessibility.AccessibilityManager;
//import android.view.accessibility.AccessibilityManager.AccessibilityStateChangeListener;

//import java.util.List;

/**
 * ICS specific AccessibilityManager API implementation.
 */
class AccessibilityManagerCompatIcs {
/*
    interface AccessibilityStateChangeListenerBridge {
        public void onAccessibilityStateChanged(boolean enabled);
    }

    public static Object newAccessibilityStateChangeListener(
            final AccessibilityStateChangeListenerBridge bridge) {
        return new AccessibilityStateChangeListener() {
            @Override
            public void onAccessibilityStateChanged(boolean enabled) {
                bridge.onAccessibilityStateChanged(enabled);
            }
        };
    }

    public static boolean addAccessibilityStateChangeListener(AccessibilityManager manager,
            Object listener) {
        return manager.addAccessibilityStateChangeListener(
                (AccessibilityStateChangeListener)listener);
    }

    public static boolean removeAccessibilityStateChangeListener(AccessibilityManager manager,
            Object listener) {
        return manager.removeAccessibilityStateChangeListener(
                (AccessibilityStateChangeListener)listener);
    }

    public static List<AccessibilityServiceInfo> getEnabledAccessibilityServiceList(
            AccessibilityManager manager,int feedbackTypeFlags) {
        return manager.getEnabledAccessibilityServiceList(feedbackTypeFlags);
    }

    public static List<AccessibilityServiceInfo> getInstalledAccessibilityServiceList(
            AccessibilityManager manager) {
        return manager.getInstalledAccessibilityServiceList();
    }

    public static boolean isTouchExplorationEnabled(AccessibilityManager manager) {
        return manager.isTouchExplorationEnabled();
    }
*/
}
