package org.adaway.broadcast;

import static android.content.Intent.ACTION_MY_PACKAGE_REPLACED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.adaway.AdAwayApplication;

import timber.log.Timber;

/**
 * This broadcast receiver is executed at application update.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class UpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            AdAwayApplication application = (AdAwayApplication) context.getApplicationContext();
            String versionName = application.getUpdateModel().getVersionName();
            Timber.d("UpdateReceiver invoked");
            Timber.i("Application update to version %s", versionName);
        }
    }
}
