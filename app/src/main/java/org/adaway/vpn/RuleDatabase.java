/*
 * Derived from dns66:
 * Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.adaway.vpn;

import android.content.Context;

import org.adaway.util.HostsParser;
import org.adaway.util.Log;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

import static org.adaway.util.Constants.HOSTS_FILENAME;
import static org.adaway.util.Constants.TAG;

/**
 * Represents hosts that are blocked.
 * <p>
 * This is a very basic set of hosts. But it supports lock-free
 * readers with writers active at the same time, only the writers
 * having to take a lock.
 */
public class RuleDatabase {
    private static final RuleDatabase instance = new RuleDatabase();

    private Set<String> blacklist;

    /**
     * Package-private constructor for instance and unit tests.
     */
    private RuleDatabase() {
        this.blacklist = new HashSet<>();
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
        return this.blacklist.contains(host);
    }

    /**
     * Load the hosts according to the configuration
     *
     * @param context A context used for opening files.
     */
    public synchronized void initialize(Context context) {
        this.blacklist.clear();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.openFileInput(HOSTS_FILENAME)))) {
            HostsParser hostsParser = new HostsParser(reader, false, false);
            this.blacklist.addAll(hostsParser.getBlacklist());
        } catch (FileNotFoundException exception) {
            Log.i(TAG, "No private hosts file.");
        } catch (IOException exception) {
            Log.e(TAG, "Failed to parse private host file to load VPN blacklist.");
        }
    }
}
