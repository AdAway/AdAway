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

import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents hosts that are blocked.
 * <p>
 * This is a very basic set of hosts. But it supports lock-free
 * readers with writers active at the same time, only the writers
 * having to take a lock.
 */
public class RuleDatabase {
    private final Set<String> blacklist;
    private HostListItemDao hostListItemDao;

    public RuleDatabase() {
        this.blacklist = new HashSet<>();
    }

    /**
     * Checks if a host is blocked.
     *
     * @param host A hostname
     * @return true if the host is blocked, false otherwise.
     */
    public boolean isBlocked(String host) {
        // Check cache
        boolean cached = this.blacklist.contains(host);
        if (cached) {
            return true;
        }
        // Check database
        if (this.hostListItemDao == null) {
            return false;
        }
        boolean blocked = this.hostListItemDao.isHostBlocked(host);
        // Update cache
        if (blocked) {
            // TODO Improve cache invalidation
            if (this.blacklist.size() > 1024) {
                this.blacklist.clear();
            }
            this.blacklist.add(host);
        }
        return blocked;
    }

    /**
     * Load the hosts according to the configuration
     *
     * @param context A context used for opening files.
     */
    public void initialize(Context context) {
        AppDatabase database = AppDatabase.getInstance(context);
        this.hostListItemDao = database.hostsListItemDao();
    }
}
