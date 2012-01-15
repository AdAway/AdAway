package org.adaway.service;

import java.util.Calendar;

import org.adaway.helper.PreferencesHelper;
import org.adaway.util.Constants;
import org.adaway.util.Log;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.SystemClock;
import com.commonsware.cwac.wakeful.WakefulIntentService;
import com.commonsware.cwac.wakeful.WakefulIntentService.AlarmListener;

public class UpdateListener implements AlarmListener {
    public void scheduleAlarms(AlarmManager mgr, PendingIntent pi, Context ctxt) {
        Log.d(Constants.TAG, "schedule alarms...");

        // register when enabled in preferences
        if (PreferencesHelper.getUpdateCheckDaily(ctxt)) {
            Log.d(Constants.TAG, "schedule update check...");

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
                mgr.setRepeating(AlarmManager.RTC, calendar.getTimeInMillis(),
                        AlarmManager.INTERVAL_DAY, pi);
            }
        }
    }

    public void sendWakefulWork(Context ctxt) {
        WakefulIntentService.sendWakefulWork(ctxt, UpdateService.class);
    }

    public long getMaxAge() {
        Log.d(Constants.TAG, "getMaxAge");
        
        if (Constants.DEBUG_UPDATE_CHECK_SERVICE) {
            return (60 * 1000);
        } else {
          return (AlarmManager.INTERVAL_DAY + AlarmManager.INTERVAL_FIFTEEN_MINUTES);
        }
    }
}
