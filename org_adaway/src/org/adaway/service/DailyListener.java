/*
 * Copyright (C) 2011-2012 Dominik Schürmann <dominik@dominikschuermann.de>
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AdAway. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.adaway.service;

import java.util.Calendar;

import org.adaway.helper.PreferenceHelper;
import org.adaway.util.Constants;
import org.adaway.util.Log;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.SystemClock;
import com.commonsware.cwac.wakeful.WakefulIntentService;
import com.commonsware.cwac.wakeful.WakefulIntentService.AlarmListener;

public class DailyListener implements AlarmListener {
    public void scheduleAlarms(AlarmManager mgr, PendingIntent pi, Context context) {
        // register when enabled in preferences
        if (PreferenceHelper.getUpdateCheckDaily(context)) {
            Log.i(Constants.TAG, "Schedule update check...");

            // every day at 9 am
            Calendar calendar = Calendar.getInstance();
            // if it's after or equal 9 am schedule for next day
            if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= 9) {
                calendar.add(Calendar.DAY_OF_YEAR, 1); // add, not set!
            }
            calendar.set(Calendar.HOUR_OF_DAY, 9);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);

            if (Constants.DEBUG_UPDATE_CHECK_SERVICE) {
                // for debugging execute service ever minute
                mgr.setRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime(),
                        60 * 1000, pi);
            } else {
                mgr.setInexactRepeating(AlarmManager.RTC, calendar.getTimeInMillis(),
                        AlarmManager.INTERVAL_DAY, pi);
            }
        }
    }

    public void sendWakefulWork(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();

        // only when connected or while connecting...
        if (netInfo != null && netInfo.isConnectedOrConnecting()) {

            boolean updateOnlyOnWifi = PreferenceHelper.getUpdateOnlyOnWifi(context);

            // if we have mobile or wifi connectivity...
            if (((netInfo.getType() == ConnectivityManager.TYPE_MOBILE) && updateOnlyOnWifi == false)
                    || (netInfo.getType() == ConnectivityManager.TYPE_WIFI)) {
                Log.d(Constants.TAG, "We have internet, start update check directly now!");

                Intent updateIntent = new Intent(context, UpdateService.class);
                updateIntent.putExtra(UpdateService.EXTRA_BACKGROUND_EXECUTION, true);
                WakefulIntentService.sendWakefulWork(context, updateIntent);
            } else {
                Log.d(Constants.TAG, "We have no internet, enable ConnectivityReceiver!");

                // enable receiver to schedule update when internet is available!
                ConnectivityReceiver.enableReceiver(context);
            }
        } else {
            Log.d(Constants.TAG, "We have no internet, enable ConnectivityReceiver!");

            // enable receiver to schedule update when internet is available!
            ConnectivityReceiver.enableReceiver(context);
        }
    }

    public long getMaxAge() {
        if (Constants.DEBUG_UPDATE_CHECK_SERVICE) {
            return (60 * 1000);
        } else {
            return (AlarmManager.INTERVAL_DAY + 60 * 1000);
        }
    }
}