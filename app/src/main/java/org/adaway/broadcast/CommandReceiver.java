package org.adaway.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.adaway.AdAwayApplication;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.error.HostErrorException;
import org.adaway.util.AppExecutors;
import org.adaway.util.Log;

/**
 * This broadcast receiver listens to commands from broadcast.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class CommandReceiver extends BroadcastReceiver {
    /**
     * This action allows to send commands to the application. See {@link Command} for extra values.
     */
    public static final String SEND_COMMAND_ACTION = "org.adaway.action.SEND_COMMAND";
    private static final String TAG = "StatusReceiver";
    private static final AppExecutors EXECUTORS = AppExecutors.getInstance();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (SEND_COMMAND_ACTION.equals(intent.getAction())) {
            AdBlockModel adBlockModel = ((AdAwayApplication) context.getApplicationContext()).getAdBlockModel();
            Command command = Command.readFromIntent(intent);
            Log.i(TAG, "CommandReceiver invoked with command "+command);
            EXECUTORS.diskIO().execute(() -> executeCommand(adBlockModel, command));
        }
    }

    private void executeCommand(AdBlockModel adBlockModel, Command command) {
        try {
            switch (command) {
                case START:
                    adBlockModel.apply();
                    break;
                case STOP:
                    adBlockModel.revert();
                    break;
                case UNKNOWN:
                    Log.i(TAG, "Failed to run an unsupported command.");
                    break;
            }
        } catch (HostErrorException e) {
            Log.w(TAG, "Failed to apply ad block command " + command + ".", e);
        }
    }
}
