package org.adaway.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import static android.content.Intent.ACTION_MY_PACKAGE_REPLACED;
import static org.adaway.BuildConfig.VERSION_NAME;

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
            Timber.d("UpdateReceiver invoked");
            Timber.i("Application update to version %s", VERSION_NAME);
        }
    }
}
