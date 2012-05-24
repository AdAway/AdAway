package org.adaway.service;

import org.adaway.helper.PreferencesHelper;
import org.adaway.util.Constants;
import org.adaway.util.Log;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class ConnectivityReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            Log.d(Constants.TAG, "ConnectivityReceiver invoked...");

            // only when background update is enabled in prefs
            if (PreferencesHelper.getUpdateCheckDaily(context)) {
                Log.d(Constants.TAG, "Update check daily is enabled!");

                boolean noConnectivity = intent.getBooleanExtra(
                        ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);

                if (!noConnectivity) {
                    // get last update time
                    long lastUpdate = PreferencesHelper.getLastUpdateCheck(context);

                    // only when the last update is older than one day!
                    if ((lastUpdate + AlarmManager.INTERVAL_DAY) < System.currentTimeMillis()) {
                        Log.d(Constants.TAG, "Last update is older than one day!");

                        // be backward compatible
                        @SuppressWarnings("deprecation")
                        NetworkInfo aNetworkInfo = (NetworkInfo) intent
                                .getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);

                        boolean updateOnlyOnWifi = PreferencesHelper.getUpdateOnlyOnWifi(context);

                        // if we have mobile or wifi connectivity...
                        if (((aNetworkInfo.getType() == ConnectivityManager.TYPE_MOBILE) && updateOnlyOnWifi == false)
                                || (aNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI)) {
                            Log.d(Constants.TAG, "We have internet, start update check!");

                            // Start service with wakelock by using WakefulIntentService
                            Intent updateIntent = new Intent(context, UpdateService.class);
                            updateIntent.putExtra(UpdateService.EXTRA_APPLY_AFTER_CHECK, true);
                            WakefulIntentService.sendWakefulWork(context, updateIntent);
                        }
                    }
                }
            }
        }
    }
}
