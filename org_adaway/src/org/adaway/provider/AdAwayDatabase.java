/*
 * Copyright (C) 2011 Dominik Schürmann <dominik@dominikschuermann.de>
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

/**
 * 
 * 
 * TODO: rework everything to extend directly OpenHelper
 * 
 * 
 * 
 */


package org.adaway.provider;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.provider.BaseColumns;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.adaway.provider.AdAwayContract.BlacklistColumns;
import org.adaway.provider.AdAwayContract.HostsSourcesColumns;
import org.adaway.provider.AdAwayContract.RedirectionListColumns;
import org.adaway.provider.AdAwayContract.WhitelistColumns;
import org.adaway.util.Constants;
import org.adaway.util.Log;

public class AdAwayDatabase {

    private Context mContext;
    private SQLiteDatabase mDB;

    private static final String DATABASE_NAME = "adaway.db";
    private static final int DATABASE_VERSION = 5;

    public interface Tables {
        String HOSTS_SOURCES = "hosts_sources";
        String WHITELIST = "whitelist";
        String BLACKLIST = "blacklist";
        String REDIRECTION_LIST = "redirection_list";
    }

    private static final String CREATE_HOSTS_SOURCES = "CREATE TABLE IF NOT EXISTS "
            + Tables.HOSTS_SOURCES
            + "("
            + BaseColumns._ID
            + " INTEGER PRIMARY KEY AUTOINCREMENT,"
            + HostsSourcesColumns.URL
            + " TEXT UNIQUE, last_modified_local INTEGER, last_modified_online INTEGER, enabled INTEGER)";

    private static final String CREATE_WHITELIST = "CREATE TABLE IF NOT EXISTS " + Tables.WHITELIST
            + "(" + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," + WhitelistColumns.URL
            + " TEXT UNIQUE," + WhitelistColumns.ENABLED + " INTEGER)";

    private static final String CREATE_BLACKLIST = "CREATE TABLE IF NOT EXISTS " + Tables.BLACKLIST
            + "(" + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," + BlacklistColumns.URL
            + " TEXT UNIQUE," + BlacklistColumns.ENABLED + " INTEGER)";

    private static final String CREATE_REDIRECTION_LIST = "CREATE TABLE IF NOT EXISTS "
            + Tables.REDIRECTION_LIST + "(" + BaseColumns._ID
            + " INTEGER PRIMARY KEY AUTOINCREMENT," + RedirectionListColumns.URL + " TEXT UNIQUE,"
            + RedirectionListColumns.IP + " TEXT," + RedirectionListColumns.ENABLED + " INTEGER)";

    private SQLiteStatement insertStmtHostsSources;
    private static final String INSERT_HOSTS_SOURCES = "INSERT OR IGNORE INTO "
            + Tables.HOSTS_SOURCES
            + "(url, last_modified_local, last_modified_online, enabled) VALUES (?, ?, ?, ?)";
    private SQLiteStatement insertStmtWhitelist;
    private static final String INSERT_WHITELIST = "INSERT OR IGNORE INTO " + Tables.WHITELIST
            + "(url, enabled) VALUES (?, ?)";
    private SQLiteStatement insertStmtBlacklist;
    private static final String INSERT_BLACKLIST = "INSERT OR IGNORE INTO " + Tables.BLACKLIST
            + "(url, enabled) VALUES (?, ?)";
    private SQLiteStatement insertStmtRedirectionList;
    private static final String INSERT_REDIRECTION_LIST = "INSERT OR IGNORE INTO "
            + Tables.REDIRECTION_LIST + "(url, ip, enabled) VALUES (?, ?, ?)";

    public AdAwayDatabase(Context context) {
        this.mContext = context;
        OpenHelper openHelper = new OpenHelper(this.mContext);
        this.mDB = openHelper.getWritableDatabase();
        this.insertStmtHostsSources = this.mDB.compileStatement(INSERT_HOSTS_SOURCES);
        this.insertStmtWhitelist = this.mDB.compileStatement(INSERT_WHITELIST);
        this.insertStmtBlacklist = this.mDB.compileStatement(INSERT_BLACKLIST);
        this.insertStmtRedirectionList = this.mDB.compileStatement(INSERT_REDIRECTION_LIST);
    }

    /**
     * Close the database helper.
     */
    public void close() {
        mDB.close();
    }

    /* HOSTS SOURCES */

    public long insertHostsSource(String url) {
        insertStmtHostsSources.bindString(1, url);
        insertStmtHostsSources.bindLong(2, 0); // last_modified_local starts at 0
        insertStmtHostsSources.bindLong(3, 0); // last_modified_online starts at 0
        insertStmtHostsSources.bindString(4, "1"); // default is enabled
        return insertStmtHostsSources.executeInsert();
    }

    public void deleteHostsSource(long rowId) {
        mDB.delete(Tables.HOSTS_SOURCES, "_id=" + rowId, null);
    }

    public void updateHostsSourceURL(long rowId, String url) {
        ContentValues args = new ContentValues();
        args.put("url", url);
        mDB.update(Tables.HOSTS_SOURCES, args, "_id=" + rowId, null);
    }

    public void updateHostsSourceLastModifiedLocal(long rowId, long lastModifiedLocal) {
        ContentValues args = new ContentValues();
        args.put("last_modified_local", lastModifiedLocal);
        mDB.update(Tables.HOSTS_SOURCES, args, "_id=" + rowId, null);
    }

    public void updateHostsSourceLastModifiedOnline(long rowId, long lastModifiedOnline) {
        ContentValues args = new ContentValues();
        args.put("last_modified_online", lastModifiedOnline);
        mDB.update(Tables.HOSTS_SOURCES, args, "_id=" + rowId, null);
    }

    public void updateHostsSourceStatus(long rowId, Integer status) {
        ContentValues args = new ContentValues();
        args.put("enabled", status);
        mDB.update(Tables.HOSTS_SOURCES, args, "_id=" + rowId, null);
    }

    public Cursor getHostsSourcesCursor() {
        Cursor cursor = this.mDB.query(Tables.HOSTS_SOURCES, new String[] { "_id", "url",
                "last_modified_local", "last_modified_online", "enabled" }, null, null, null, null,
                "url asc");

        return cursor;
    }

    public Cursor getEnabledHostsSourcesCursor() {
        Cursor cursor = this.mDB.query(Tables.HOSTS_SOURCES, new String[] { "_id", "url",
                "last_modified_local", "last_modified_online", "enabled" }, "enabled=1", null,
                null, null, "url asc");

        return cursor;
    }

    public ArrayList<String> getAllEnabledHostsSources() {
        ArrayList<String> list = new ArrayList<String>();
        Cursor cursor = this.mDB.query(Tables.HOSTS_SOURCES, new String[] { "_id", "url",
                "last_modified_local", "last_modified_online", "enabled" }, "enabled=1", null,
                null, null, "url asc");
        if (cursor.moveToFirst()) {
            do {
                list.add(cursor.getString(1));
            } while (cursor.moveToNext());
        }
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }
        return list;
    }

    /**
     * Go through all enabled hosts sources and set local last modified to online last modified
     */
    public void updateAllEnabledHostsSourcesLastModifiedLocalFromOnline() {
        Cursor cursor = this.mDB.query(Tables.HOSTS_SOURCES, new String[] { "_id", "url",
                "last_modified_local", "last_modified_online", "enabled" }, "enabled=1", null,
                null, null, "url asc");
        int idCol = cursor.getColumnIndex("_id");
        int lastModifiedOnlineCol = cursor.getColumnIndex("last_modified_online");

        long lastModifiedOnline;
        long id;

        if (cursor.moveToFirst()) {
            do {
                lastModifiedOnline = cursor.getLong(lastModifiedOnlineCol);
                id = cursor.getLong(idCol);

                // set last_modified_local to last modified_online
                this.updateHostsSourceLastModifiedLocal(id, lastModifiedOnline);

            } while (cursor.moveToNext());
        }
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }
    }

    /* WHITELIST */

    public long insertWhitelistItem(String url) {
        insertStmtWhitelist.bindString(1, url);
        insertStmtWhitelist.bindString(2, "1"); // default is enabled
        return insertStmtWhitelist.executeInsert();
    }

    public void deleteWhitelistItem(long rowId) {
        mDB.delete(Tables.WHITELIST, "_id=" + rowId, null);
    }

    public void updateWhitelistItemURL(long rowId, String url) {
        ContentValues args = new ContentValues();
        args.put("url", url);
        mDB.update(Tables.WHITELIST, args, "_id=" + rowId, null);
    }

    public void updateWhitelistItemStatus(long rowId, Integer status) {
        ContentValues args = new ContentValues();
        args.put("enabled", status);
        mDB.update(Tables.WHITELIST, args, "_id=" + rowId, null);
    }

    public Cursor getWhitelistCursor() {
        Cursor cursor = this.mDB.query(Tables.WHITELIST, new String[] { "_id", "url", "enabled" },
                null, null, null, null, "url asc");

        return cursor;
    }

    public HashSet<String> getAllEnabledWhitelistItems() {
        HashSet<String> list = new HashSet<String>();
        Cursor cursor = this.mDB.query(Tables.WHITELIST, new String[] { "_id", "url", "enabled" },
                "enabled=1", null, null, null, "url asc");
        if (cursor.moveToFirst()) {
            do {
                list.add(cursor.getString(1));
            } while (cursor.moveToNext());
        }
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }
        return list;
    }

    /* BLACKLIST */

    public long insertBlacklistItem(String url) {
        insertStmtBlacklist.bindString(1, url);
        insertStmtBlacklist.bindString(2, "1"); // default is enabled
        return insertStmtBlacklist.executeInsert();
    }

    public void deleteBlacklistItem(long rowId) {
        mDB.delete(Tables.BLACKLIST, "_id=" + rowId, null);
    }

    public void updateBlacklistItemURL(long rowId, String url) {
        ContentValues args = new ContentValues();
        args.put("url", url);
        mDB.update(Tables.BLACKLIST, args, "_id=" + rowId, null);
    }

    public void updateBlacklistItemStatus(long rowId, Integer status) {
        ContentValues args = new ContentValues();
        args.put("enabled", status);
        mDB.update(Tables.BLACKLIST, args, "_id=" + rowId, null);
    }

    public Cursor getBlacklistCursor() {
        Cursor cursor = this.mDB.query(Tables.BLACKLIST, new String[] { "_id", "url", "enabled" },
                null, null, null, null, "url asc");

        return cursor;
    }

    public HashSet<String> getAllEnabledBlacklistItems() {
        HashSet<String> list = new HashSet<String>();
        Cursor cursor = this.mDB.query(Tables.BLACKLIST, new String[] { "_id", "url", "enabled" },
                "enabled=1", null, null, null, "url asc");
        if (cursor.moveToFirst()) {
            do {
                list.add(cursor.getString(1));
            } while (cursor.moveToNext());
        }
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }
        return list;
    }

    /* REDIRECTION LIST */

    public long insertRedirectionItem(String url, String ip) {
        insertStmtRedirectionList.bindString(1, url);
        insertStmtRedirectionList.bindString(2, ip);
        insertStmtRedirectionList.bindString(3, "1"); // default is enabled
        return insertStmtRedirectionList.executeInsert();
    }

    public void deleteRedirectionItem(long rowId) {
        mDB.delete(Tables.REDIRECTION_LIST, "_id=" + rowId, null);
    }

    public void updateRedirectionItemURL(long rowId, String url, String ip) {
        ContentValues args = new ContentValues();
        args.put("url", url);
        args.put("ip", ip);
        mDB.update(Tables.REDIRECTION_LIST, args, "_id=" + rowId, null);
    }

    public void updateRedirectionItemStatus(long rowId, Integer status) {
        ContentValues args = new ContentValues();
        args.put("enabled", status);
        mDB.update(Tables.REDIRECTION_LIST, args, "_id=" + rowId, null);
    }

    public Cursor getRedirectionCursor() {
        Cursor cursor = this.mDB.query(Tables.REDIRECTION_LIST, new String[] { "_id", "url", "ip",
                "enabled" }, null, null, null, null, "url asc");

        return cursor;
    }

    public HashMap<String, String> getAllEnabledRedirectionItems() {
        HashMap<String, String> list = new HashMap<String, String>();
        Cursor cursor = this.mDB.query(Tables.REDIRECTION_LIST, new String[] { "_id", "url", "ip",
                "enabled" }, "enabled=1", null, null, null, "url asc");
        if (cursor.moveToFirst()) {
            do {
                list.put(cursor.getString(1), cursor.getString(2));
            } while (cursor.moveToNext());
        }
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }
        return list;
    }

    /* HELPER */

    public class OpenHelper extends SQLiteOpenHelper {
        OpenHelper(Context context) {
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
            String insertHostsSources = "INSERT OR IGNORE INTO "
                    + Tables.HOSTS_SOURCES
                    + "(url, last_modified_local, last_modified_online, enabled) VALUES (?, ?, ?, ?)";
            insertStmt = db.compileStatement(insertHostsSources);

            // http://winhelp2002.mvps.org/hosts.htm
            insertHostsSource(insertStmt, "http://winhelp2002.mvps.org/hosts.txt");

            // http://hosts-file.net - This file contains ad/tracking servers in the hpHosts
            // database.
            insertHostsSource(insertStmt, "http://hosts-file.net/ad_servers.asp");

            // http://pgl.yoyo.org/adservers/
            insertHostsSource(insertStmt,
                    "http://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext");
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
            Log.w(Constants.TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion);

            if (oldVersion <= 1) {
                // introduced whitelist, blacklist and redirection list
                db.execSQL(CREATE_WHITELIST);
                db.execSQL(CREATE_BLACKLIST);
                db.execSQL(CREATE_REDIRECTION_LIST);
            }
            if (oldVersion <= 2) {
                // introduced last modified table
                // db.execSQL(CREATE_LAST_MODIFIED);
                // insertDefaultLastModified(db);
            }
            if (oldVersion <= 3) {
                // change mvps url
                // old url: http://www.mvps.org/winhelp2002/hosts.txt
                // new url: http://winhelp2002.mvps.org/hosts.txt
                db.execSQL("UPDATE "
                        + Tables.HOSTS_SOURCES
                        + " SET url=\"http://winhelp2002.mvps.org/hosts.txt\" WHERE url=\"http://www.mvps.org/winhelp2002/hosts.txt\"");
                // new hosts source
                db.execSQL("INSERT INTO " + Tables.HOSTS_SOURCES
                        + " (url, enabled) VALUES (\"http://sysctl.org/cameleon/hosts\", 1)");
                // removed last modified table, is now a column in hosts_sources
                db.execSQL("DROP TABLE IF EXISTS last_modified");
                // add column last_modified to hosts sources
                db.execSQL("ALTER TABLE " + Tables.HOSTS_SOURCES
                        + " ADD COLUMN last_modified_local");
                db.execSQL("ALTER TABLE " + Tables.HOSTS_SOURCES
                        + " ADD COLUMN last_modified_online");
            }
            if (oldVersion <= 4) {
                // removed sysctl hosts source
                db.execSQL("DELETE FROM " + Tables.HOSTS_SOURCES
                        + " WHERE url=\"http://sysctl.org/cameleon/hosts\"");
                // new hosts source
                db.execSQL("INSERT INTO "
                        + Tables.HOSTS_SOURCES
                        + " (url, last_modified_local, last_modified_online, enabled) VALUES (\"http://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext\", 0, 0, 1)");
            } else {
                db.execSQL("DROP TABLE IF EXISTS " + Tables.HOSTS_SOURCES);
                db.execSQL("DROP TABLE IF EXISTS " + Tables.WHITELIST);
                db.execSQL("DROP TABLE IF EXISTS " + Tables.BLACKLIST);
                db.execSQL("DROP TABLE IF EXISTS " + Tables.REDIRECTION_LIST);
                onCreate(db);
            }
        }
    }
}