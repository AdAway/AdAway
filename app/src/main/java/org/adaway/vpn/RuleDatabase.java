/* Copyright (C) 2016 - 2017 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.adaway.vpn;

import android.content.Context;

/**
 * Represents hosts that are blocked.
 * <p>
 * This is a very basic set of hosts. But it supports lock-free
 * readers with writers active at the same time, only the writers
 * having to take a lock.
 */
public class RuleDatabase {
    private static final RuleDatabase instance = new RuleDatabase();

    /**
     * Package-private constructor for instance and unit tests.
     */
    RuleDatabase() {

    }


    /**
     * Returns the instance of the rule database.
     */
    public static RuleDatabase getInstance() {
        return instance;
    }

    /**
     * Checks if a host is blocked.
     *
     * @param host A hostname
     * @return true if the host is blocked, false otherwise.
     */
    public boolean isBlocked(String host) {
        return host.contains("facebook.com");
    }

    /**
     * Load the hosts according to the configuration
     *
     * @param context A context used for opening files.
     * @throws InterruptedException Thrown if the thread was interrupted, so we don't waste time
     *                              reading more host files than needed.
     */
    public synchronized void initialize(Context context) {

    }
}
