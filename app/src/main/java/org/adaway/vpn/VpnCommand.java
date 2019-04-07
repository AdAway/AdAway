/* Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
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

/**
 * Created by jak on 18/10/16.
 */

public enum VpnCommand {
    START, STOP, PAUSE, RESUME;

    public static VpnCommand fromExtra(int extra) {
        return VpnCommand.values()[extra];
    }

    public int toExtra() {
        return this.ordinal();
    }
}
