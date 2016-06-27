/*
 * Copyright (C) 2011-2012 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This file is part of AdAway.
 * 
 * AdAway is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AdAway is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AdAway.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.adaway.provider;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.provider.BaseColumns;

import org.adaway.provider.AdAwayContract.BlacklistColumns;
import org.adaway.provider.AdAwayContract.HostsSourcesColumns;
import org.adaway.provider.AdAwayContract.RedirectionListColumns;
import org.adaway.provider.AdAwayContract.WhitelistColumns;
import org.adaway.util.Constants;
import org.adaway.util.Log;

public class AdAwayDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "adaway.db";
    private static final int DATABASE_VERSION = 14;

    public interface Tables {
        String HOSTS_SOURCES = "hosts_sources";
        String WHITELIST = "whitelist";
        String BLACKLIST = "blacklist";
        String REDIRECTION_LIST = "redirection_list";
    }

    private static final String CREATE_HOSTS_SOURCES = "CREATE TABLE IF NOT EXISTS "
            + Tables.HOSTS_SOURCES + "(" + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + HostsSourcesColumns.URL + " TEXT UNIQUE, " + HostsSourcesColumns.LAST_MODIFIED_LOCAL
            + " INTEGER, " + HostsSourcesColumns.LAST_MODIFIED_ONLINE + " INTEGER, "
            + HostsSourcesColumns.ENABLED + " INTEGER)";

    private static final String CREATE_WHITELIST = "CREATE TABLE IF NOT EXISTS " + Tables.WHITELIST
            + "(" + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + WhitelistColumns.HOSTNAME + " TEXT UNIQUE, " + WhitelistColumns.ENABLED + " INTEGER)";

    private static final String CREATE_BLACKLIST = "CREATE TABLE IF NOT EXISTS " + Tables.BLACKLIST
            + "(" + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + BlacklistColumns.HOSTNAME + " TEXT UNIQUE, " + BlacklistColumns.ENABLED + " INTEGER)";

    private static final String CREATE_REDIRECTION_LIST = "CREATE TABLE IF NOT EXISTS "
            + Tables.REDIRECTION_LIST + "(" + BaseColumns._ID
            + " INTEGER PRIMARY KEY AUTOINCREMENT, " + RedirectionListColumns.HOSTNAME
            + " TEXT UNIQUE, " + RedirectionListColumns.IP + " TEXT, "
            + RedirectionListColumns.ENABLED + " INTEGER)";

    AdAwayDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public long insertHostsSource(SQLiteStatement insertStmt, String url) {
        insertStmt.bindString(1, url);
        insertStmt.bindLong(2, 0); // last_modified_local starts at 0
        insertStmt.bindLong(3, 0); // last_modified_online starts at 0
        insertStmt.bindString(4, "1"); // default is enabled
        return insertStmt.executeInsert();
    }

    private void insertDefaultHostsSources(SQLiteDatabase db) {
        // fill default hosts sources
        SQLiteStatement insertStmt;
        String insertHostsSources = "INSERT OR IGNORE INTO " + Tables.HOSTS_SOURCES
                + "(url, last_modified_local, last_modified_online, enabled) VALUES (?, ?, ?, ?)";
        insertStmt = db.compileStatement(insertHostsSources);

        // http://winhelp2002.mvps.org/hosts.htm
        insertHostsSource(insertStmt, "http://winhelp2002.mvps.org/hosts.txt");

        // https://hosts-file.net
        insertHostsSource(insertStmt, "https://hosts-file.net/ad_servers.txt");

        // https://pgl.yoyo.org/adservers/
        insertHostsSource(insertStmt,
                "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext");

        // AdAway's own mobile hosts
        insertHostsSource(insertStmt, "https://adaway.org/hosts.txt");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.w(Constants.TAG, "Creating database...");

        db.execSQL(CREATE_HOSTS_SOURCES);
        db.execSQL(CREATE_WHITELIST);
        db.execSQL(CREATE_BLACKLIST);
        db.execSQL(CREATE_REDIRECTION_LIST);

        insertDefaultHostsSources(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(Constants.TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);

        if (oldVersion <= 1) {
            // introduced whitelist, blacklist and redirection list
            db.execSQL(CREATE_WHITELIST);
            db.execSQL(CREATE_BLACKLIST);
            db.execSQL(CREATE_REDIRECTION_LIST);
        }
        // if (oldVersion <= 2) {
        // introduced last modified table
        // db.execSQL(CREATE_LAST_MODIFIED);
        // insertDefaultLastModified(db);
        // }
        if (oldVersion <= 3) {
            // change mvps url
            // old url: http://www.mvps.org/winhelp2002/hosts.txt
            // new url: http://winhelp2002.mvps.org/hosts.txt
            db.execSQL("UPDATE "
                    + Tables.HOSTS_SOURCES
                    + " SET url=\"http://winhelp2002.mvps.org/hosts.txt\" WHERE url=\"http://www.mvps.org/winhelp2002/hosts.txt\"");
            // new hosts source
            db.execSQL("INSERT OR IGNORE INTO " + Tables.HOSTS_SOURCES
                    + " (url, enabled) VALUES (\"http://sysctl.org/cameleon/hosts\", 1)");
            // removed last modified table, is now a column in hosts_sources
            db.execSQL("DROP TABLE IF EXISTS last_modified");
            // add column last_modified to hosts sources
            db.execSQL("ALTER TABLE " + Tables.HOSTS_SOURCES + " ADD COLUMN last_modified_local");
            db.execSQL("ALTER TABLE " + Tables.HOSTS_SOURCES + " ADD COLUMN last_modified_online");
        }
        if (oldVersion <= 4) {
            // removed sysctl hosts source
            db.execSQL("DELETE FROM " + Tables.HOSTS_SOURCES
                    + " WHERE url=\"http://sysctl.org/cameleon/hosts\"");
            // new hosts source
            db.execSQL("INSERT OR IGNORE INTO "
                    + Tables.HOSTS_SOURCES
                    + " (url, last_modified_local, last_modified_online, enabled) VALUES (\"https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext\", 0, 0, 1)");
        }
        if (oldVersion <= 5) {
            // new hosts source
            db.execSQL("INSERT OR IGNORE INTO "
                    + Tables.HOSTS_SOURCES
                    + " (url, last_modified_local, last_modified_online, enabled) VALUES (\"http://www.ismeh.com/HOSTS\", 0, 0, 1)");
        }
        if (oldVersion <= 6) {
            // removed http://www.ismeh.com/HOSTS hosts source
            db.execSQL("DELETE FROM " + Tables.HOSTS_SOURCES
                    + " WHERE url=\"http://www.ismeh.com/HOSTS\"");
            // new hosts source
            db.execSQL("INSERT OR IGNORE INTO "
                    + Tables.HOSTS_SOURCES
                    + " (url, last_modified_local, last_modified_online, enabled) VALUES (\"http://adaway.sufficientlysecure.org/hosts.txt\", 0, 0, 1)");
        }
        if (oldVersion <= 7) {
            // change http://adaway.sufficientlysecure.org/hosts.txt to http://adaway.org/hosts.txt
            db.execSQL("UPDATE " + Tables.HOSTS_SOURCES
                    + " SET url=\"http://adaway.org/hosts.txt\""
                    + " WHERE url=\"http://adaway.sufficientlysecure.org/hosts.txt\"");
        }
        if (oldVersion <= 8) {
            // problem with db version 7: http://adaway.org/hosts.txt has only been added on db upgrade
            // not on new installations
            // remove both
            db.execSQL("DELETE FROM " + Tables.HOSTS_SOURCES
                    + " WHERE url IN (\"http://adaway.sufficientlysecure.org/hosts.txt\", \"http://adaway.org/hosts.txt\")");
            // add valid again
            db.execSQL("INSERT INTO "
                    + Tables.HOSTS_SOURCES
                    + " (url, last_modified_local, last_modified_online, enabled) VALUES (\"http://adaway.org/hosts.txt\", 0, 0, 1)");
        }
        if (oldVersion <= 10) { // 9 and 10, forgot to change on new installs
            // change to https
            // remove both
            db.execSQL("DELETE FROM " + Tables.HOSTS_SOURCES
                    + " WHERE url IN (\"http://adaway.org/hosts.txt\", \"https://adaway.org/hosts.txt\")");
            // add valid again
            db.execSQL("INSERT INTO "
                    + Tables.HOSTS_SOURCES
                    + " (url, last_modified_local, last_modified_online, enabled) VALUES (\"https://adaway.org/hosts.txt\", 0, 0, 1)");
        }
        if (oldVersion <= 11) {
            // change http://hosts-file.net/ad_servers.asp to http://hosts-file.net/ad_servers.txt
            db.execSQL("UPDATE " + Tables.HOSTS_SOURCES
                    + " SET url=\"http://hosts-file.net/ad_servers.txt\""
                    + " WHERE url=\"http://hosts-file.net/ad_servers.asp\"");
        }
        if (oldVersion <= 12) {
            // change http://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext to https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext
            db.execSQL("UPDATE " + Tables.HOSTS_SOURCES
                    + " SET url=\"https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext\""
                    + " WHERE url=\"http://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext\"");
        } 
        if (oldVersion <= 13) {
            // change http://hosts-file.net/ad_servers.txt to https://hosts-file.net/ad_servers.txt
            db.execSQL("UPDATE " + Tables.HOSTS_SOURCES
                    + " SET url=\"https://hosts-file.net/ad_servers.txt\""
                    + " WHERE url=\"http://hosts-file.net/ad_servers.txt\"");
        } else {
            db.execSQL("DROP TABLE IF EXISTS " + Tables.HOSTS_SOURCES);
            db.execSQL("DROP TABLE IF EXISTS " + Tables.WHITELIST);
            db.execSQL("DROP TABLE IF EXISTS " + Tables.BLACKLIST);
            db.execSQL("DROP TABLE IF EXISTS " + Tables.REDIRECTION_LIST);
            onCreate(db);
        }
    }
}
