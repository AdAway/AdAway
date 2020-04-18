package org.adaway.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.adaway.AdAwayApplication;
import org.adaway.model.adblocking.AdBlockCommand;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.error.HostErrorException;
import org.adaway.util.Log;

/**
 * This broadcast receiver listens to ad-blocking status change request.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class StatusCommandReceiver extends BroadcastReceiver {
    /**
     * This action controls the ad blocking status.<br>See {@link AdBlockCommand} for extra values.
     */
    public static final String STATUS_COMMAND_ACTION = "org.adaway.AD_BLOCK_STATUS";
    private static final String TAG = "StatusReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (STATUS_COMMAND_ACTION.equals(intent.getAction())) {
            Log.i(TAG, "StatusReceiver invoked");
            AdBlockModel adBlockModel = ((AdAwayApplication) context.getApplicationContext()).getAdBlockModel();
            AdBlockCommand command = AdBlockCommand.readFromIntent(intent);
            try {
                switch (command) {
                    case START:
                        adBlockModel.apply();
                        break;
                    case STOP:
                        adBlockModel.revert();
                        break;
                }
            } catch (HostErrorException e) {
                Log.w(TAG, "Failed to apply ad block command " + command + ".", e);
            }
        }
    }
}
