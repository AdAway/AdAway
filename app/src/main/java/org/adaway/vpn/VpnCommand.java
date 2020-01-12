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
package org.adaway.vpn;

import android.content.Context;
import android.content.Intent;

public enum VpnCommand {
    START,
    STOP;

    private static final String INTENT_EXTRA_COMMAND = "COMMAND";

    public static VpnCommand fromIntent(Intent intent) {
        VpnCommand command = START;
        if (intent != null && intent.hasExtra(INTENT_EXTRA_COMMAND)) {
            int ordinal = intent.getIntExtra(INTENT_EXTRA_COMMAND, command.ordinal());
            command = VpnCommand.values()[ordinal];
        }
        return command;
    }

    public Intent toIntent(Context context) {
        Intent intent = new Intent(context, VpnService.class);
        intent.putExtra(INTENT_EXTRA_COMMAND, this.ordinal());
        return intent;
    }
}
