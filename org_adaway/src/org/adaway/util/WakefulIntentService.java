/***
 Copyright (c) 2008-2011 CommonsWare, LLC
 Licensed under the Apache License, Version 2.0 (the "License"); you may not
 use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 by applicable law or agreed to in writing, software distributed under the
 License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 OF ANY KIND, either express or implied. See the License for the specific
 language governing permissions and limitations under the License.
 
 From _The Busy Coder's Guide to Advanced Android Development_
 http://commonsware.com/AdvAndroid
 */

package org.adaway.util;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

/**
 * WakefulIntentService extends normal IntentService with a Wakelock. You can override doWakefulWork
 * to do work that is long running and has a Wakelock, meaning the phone will not get back to sleep
 * while executing this code. Before starting a WakefulIntentService acquire a wakelock by
 * WakefulIntentService.acquireStaticLock(context).
 */
abstract public class WakefulIntentService extends IntentService {
    public static final String LOCK_NAME_STATIC = "org.adaway.WakefulIntentService.Static";
    private static PowerManager.WakeLock lockStatic = null;

    /**
     * Override this method to do long running tasks with a wakelock, meaning without going back to
     * sleep while executing code
     * 
     * @param intent
     */
    abstract public void doWakefulWork(Intent intent);

    /**
     * Execute this method to acquire a wakelock before executing doWakefulWork
     * 
     * @param context
     */
    public static void acquireStaticLock(Context context) {
        Log.d(Constants.TAG, "Acquireing wake lock...");
        getLock(context).acquire();
    }

    synchronized private static PowerManager.WakeLock getLock(Context context) {
        if (lockStatic == null) {
            PowerManager mgr = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

            lockStatic = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, LOCK_NAME_STATIC);
            lockStatic.setReferenceCounted(true);
        }

        return (lockStatic);
    }

    public WakefulIntentService(String name) {
        super(name);
    }

    @Override
    final protected void onHandleIntent(Intent intent) {
        try {
            doWakefulWork(intent);
        } finally {
            Log.d(Constants.TAG, "Release wake lock...");
            getLock(this).release();
        }
    }
}