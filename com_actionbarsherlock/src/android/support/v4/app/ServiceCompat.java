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

package android.support.v4.app;

/**
 * Helper for accessing features in {@link android.app.Service}
 * introduced after API level 4 in a backwards compatible fashion.
 */
public class ServiceCompat {

    private ServiceCompat() {
        /* Hide constructor */
    }

    /**
     * Constant to return from {@link android.app.Service#onStartCommand}: if this
     * service's process is killed while it is started (after returning from
     * {@link android.app.Service#onStartCommand}), then leave it in the started
     * state but don't retain this delivered intent.  Later the system will try to
     * re-create the service.  Because it is in the started state, it will
     * guarantee to call {@link android.app.Service#onStartCommand} after creating
     * the new service instance; if there are not any pending start commands to be
     * delivered to the service, it will be called with a null intent
     * object, so you must take care to check for this.
     *
     * <p>This mode makes sense for things that will be explicitly started
     * and stopped to run for arbitrary periods of time, such as a service
     * performing background music playback.
     */
    public static final int START_STICKY = 1;
}
