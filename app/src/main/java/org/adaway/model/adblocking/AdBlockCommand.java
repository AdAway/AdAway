/*
 * Derived from dns66:
 * Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * Derived from AdBuster:
 * Copyright (C) 2016 Daniel Brodie <dbrodie@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */
package org.adaway.model.adblocking;

import android.content.Intent;

/**
 * This enumerate list the commands for ad-blocking status.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public enum AdBlockCommand {
    /**
     * Start the ad-blocking.
     */
    START,
    /**
     * Stop the ad-blocking.
     */
    STOP;

    private static final String INTENT_EXTRA_COMMAND = "COMMAND";

    /**
     * Read command from intent.
     *
     * @param intent The intent to read command from.
     * @return The read intent.
     */
    public static AdBlockCommand readFromIntent(Intent intent) {
        AdBlockCommand command = START;
        if (intent != null && intent.hasExtra(INTENT_EXTRA_COMMAND)) {
            int ordinal = intent.getIntExtra(INTENT_EXTRA_COMMAND, command.ordinal());
            command = AdBlockCommand.values()[ordinal];
        }
        return command;
    }

    /**
     * Append command to intent.
     *
     * @param intent The intent to append command to.
     */
    public void appendToIntent(Intent intent) {
        intent.putExtra(INTENT_EXTRA_COMMAND, this.ordinal());
    }
}
