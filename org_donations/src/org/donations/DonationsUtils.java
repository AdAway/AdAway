/*
 * Copyright (C) 2011-2012 Dominik Schürmann <dominik@dominikschuermann.de>
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

package org.donations;

import android.content.Context;

public class DonationsUtils {

    public static final String TAG = "Donations Library";

    public static final boolean DEBUG = false;

    /**
     * Gets resource string from values xml without relying on generated R.java. This allows to
     * compile the donations lib on its own, while having the configuration xml in the main project.
     * 
     * @param name
     * @param context
     * @return
     */
    public static String getResourceString(Context context, String name) {
        int nameResourceID = context.getResources().getIdentifier(name, "string",
                context.getApplicationInfo().packageName);
        if (nameResourceID == 0) {
            throw new IllegalArgumentException("No resource string found with name " + name);
        } else {
            return context.getString(nameResourceID);
        }
    }

    /**
     * Gets resource boolean from values xml without relying on generated R.java. This allows to
     * compile the donations lib on its own, while having the configuration xml in the main project.
     * 
     * @param name
     * @param context
     * @return
     */
    public static boolean getResourceBoolean(Context context, String name) {
        int nameResourceID = context.getResources().getIdentifier(name, "bool",
                context.getApplicationInfo().packageName);
        if (nameResourceID == 0) {
            throw new IllegalArgumentException("No resource boolean found with name " + name);
        } else {
            return context.getResources().getBoolean(nameResourceID);
        }
    }

    /**
     * Gets resource string-array from values xml without relying on generated R.java. This allows
     * to compile the donations lib on its own, while having the configuration xml in the main
     * project.
     * 
     * @param name
     * @param context
     * @return
     */
    public static String[] getResourceStringArray(Context context, String name) {
        int nameResourceID = context.getResources().getIdentifier(name, "array",
                context.getApplicationInfo().packageName);
        if (nameResourceID == 0) {
            throw new IllegalArgumentException("No resource string-array found with name " + name);
        } else {
            return context.getResources().getStringArray(nameResourceID);
        }
    }
}
