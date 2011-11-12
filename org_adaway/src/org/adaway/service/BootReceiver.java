/*
 * Copyright (C) 2011 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This file is part of AdAway.
 * 
 * AdAway is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AdAway is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AdAway.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.adaway.service;

import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.adaway.util.WakefulIntentService;
import org.adaway.util.WebserverUtils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

/**
 * This broadcast receiver is executed after boot
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i(Constants.TAG, "BootReceiver invoked, starting BootService in background");

            // acquire lock to execute operation in new thread in intentservice when phone is in
            // sleep
//            WakefulIntentService.acquireStaticLock(context);

//            context.startService(new Intent(context, BootService.class));

            final Context applicationContext = context.getApplicationContext();

            // register alarm and start webserver asynchronous not blocking the receiver
            // AsyncTask<Void, Void, Void> asyncOnBoot = new AsyncTask<Void, Void, Void>() {
            // @Override
            // protected Void doInBackground(Void... arg0) {
            // Log.d(Constants.TAG,
            // "BootReceiver async: Register alarm and start webserver if enabled...");

             UpdateCheckService.registerAlarmWhenEnabled(applicationContext);
             Log.d(Constants.TAG, "between reg alarm and webserver");
            
             WebserverUtils.startWebserverOnBoot(applicationContext);
            
             Log.d(Constants.TAG, "after reg alarm and webserver");

            // return null;
            // }
            // };
            // asyncOnBoot.execute();

        }
    }
}
