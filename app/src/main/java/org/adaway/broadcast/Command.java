package org.adaway.broadcast;

import android.content.Intent;

import timber.log.Timber;

/**
 * This enumerate lists the commands of {@link CommandReceiver}.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public enum Command {
    /**
     * Start the ad-blocking.
     */
    START,
    /**
     * Stop the ad-blocking.
     */
    STOP,
    /**
     * Unknown command.
     */
    UNKNOWN;

    private static final String INTENT_EXTRA_COMMAND = "COMMAND";

    /**
     * Read command from intent.
     *
     * @param intent The intent to read command from.
     * @return The read intent.
     */
    public static Command readFromIntent(Intent intent) {
        Command command = UNKNOWN;
        if (intent != null && intent.hasExtra(INTENT_EXTRA_COMMAND)) {
            String commandName = intent.getStringExtra(INTENT_EXTRA_COMMAND);
            if (commandName != null) {
                try {
                    command = Command.valueOf(commandName);
                } catch (IllegalArgumentException e) {
                    Timber.w("Failed to read command named %s.", commandName);
                }
            }
        }
        return command;
    }

    /**
     * Append command to intent.
     *
     * @param intent The intent to append command to.
     */
    public void appendToIntent(Intent intent) {
        intent.putExtra(INTENT_EXTRA_COMMAND, name());
    }
}
