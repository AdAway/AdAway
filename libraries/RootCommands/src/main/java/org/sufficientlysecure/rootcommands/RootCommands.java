/*
 * Copyright (C) 2012 Dominik Schürmann <dominik@dominikschuermann.de>
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

package org.sufficientlysecure.rootcommands;

import org.sufficientlysecure.rootcommands.util.Log;

public class RootCommands {
    private static boolean debug = false;
    public static final int DEFAULT_TIMEOUT = 50000;

    public static final String TAG = "RootCommands";

    /**
     * Check if debug mode is enabled.
     *
     * @return <code>true</code> if debug moode is enabled, <code>false</code> otherwise.
     */
    public static boolean isDebugEnabled() {
        return debug;
    }

    /**
     * Enable application debug mode.
     */
    public static void enableDebug() {
        debug = true;
    }

    /**
     * Disable application debug mode.
     */
    public static void disableDebug() {
        debug = false;
    }

    /**
     * General method to check if user has su binary and accepts root access for this program!
     *
     * @return true if everything worked
     */
    public static boolean rootAccessGiven() {
        boolean rootAccess = false;

        try {
            Shell rootShell = Shell.startRootShell();

            Toolbox tb = new Toolbox(rootShell);
            if (tb.isRootAccessGiven()) {
                rootAccess = true;
            }

            rootShell.close();
        } catch (Exception e) {
            Log.e(TAG, "Problem while checking for root access!", e);
        }

        return rootAccess;
    }
}
