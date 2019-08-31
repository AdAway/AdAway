package org.adaway.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.adaway.util.Log;

import static android.content.Intent.ACTION_MY_PACKAGE_REPLACED;
import static org.adaway.BuildConfig.VERSION_NAME;
import static org.adaway.util.Constants.TAG;

/**
 * This broadcast receiver is executed at application update.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class UpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            Log.d(TAG, "UpdateReceiver invoked");
            Log.w(TAG, "Application update to version " + VERSION_NAME);
        }
    }
}
