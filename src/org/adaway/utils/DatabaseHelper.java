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

package org.adaway.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;

public class DatabaseHelper {

    private Context mContext;
    private SQLiteDatabase mDB;

    private static final String DATABASE_NAME = "adaway.db";
    private static final int DATABASE_VERSION = 2;

    private static final String TABLE_HOSTS_SOURCES = "hosts_sources";
    private static final String TABLE_WHITELIST = "whitelist";
    private static final String TABLE_BLACKLIST = "blacklist";
    private static final String TABLE_REDIRECTION_LIST = "redirection_list";

    private static final String CREATE_HOSTS_SOURCES = "CREATE TABLE " + TABLE_HOSTS_SOURCES
            + "(_id INTEGER PRIMARY KEY, url TEXT, enabled INTEGER)";
    private static final String CREATE_WHITELIST = "CREATE TABLE " + TABLE_WHITELIST
            + "(_id INTEGER PRIMARY KEY, url TEXT, enabled INTEGER)";
    private static final String CREATE_BLACKLIST = "CREATE TABLE " + TABLE_BLACKLIST
            + "(_id INTEGER PRIMARY KEY, url TEXT, enabled INTEGER)";
    private static final String CREATE_REDIRECTION_LIST = "CREATE TABLE " + TABLE_REDIRECTION_LIST
            + "(_id INTEGER PRIMARY KEY, url TEXT, ip TEXT, enabled INTEGER)";

    private SQLiteStatement insertStmtHostsSources;
    private static final String INSERT_HOSTS_SOURCES = "insert into " + TABLE_HOSTS_SOURCES
            + "(url, enabled) values (?, ?)";
    private SQLiteStatement insertStmtWhitelist;
    private static final String INSERT_WHITELIST = "insert into " + TABLE_WHITELIST
            + "(url, enabled) values (?, ?)";
    private SQLiteStatement insertStmtBlacklist;
    private static final String INSERT_BLACKLIST = "insert into " + TABLE_BLACKLIST
            + "(url, enabled) values (?, ?)";
    private SQLiteStatement insertStmtRedirectionList;
    private static final String INSERT_REDIRECTION_LIST = "insert into " + TABLE_REDIRECTION_LIST
            + "(url, ip, enabled) values (?, ?, ?)";

    public DatabaseHelper(Context context) {
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
        insertStmtHostsSources.bindString(2, "1"); // default is enabled
        return insertStmtHostsSources.executeInsert();
    }

    public void deleteHostsSource(long rowId) {
        mDB.delete(TABLE_HOSTS_SOURCES, "_id=" + rowId, null);
    }

    public void updateHostsSourceURL(long rowId, String url) {
        ContentValues args = new ContentValues();
        args.put("url", url);
        mDB.update(TABLE_HOSTS_SOURCES, args, "_id=" + rowId, null);
    }

    public void updateHostsSourceStatus(long rowId, Integer status) {
        ContentValues args = new ContentValues();
        args.put("enabled", status);
        mDB.update(TABLE_HOSTS_SOURCES, args, "_id=" + rowId, null);
    }

    public Cursor getHostsSourcesCursor() {
        Cursor cursor = this.mDB.query(TABLE_HOSTS_SOURCES,
                new String[] { "_id", "url", "enabled" }, null, null, null, null, "url asc");

        return cursor;
    }

    public ArrayList<String> getAllEnabledHostsSources() {
        ArrayList<String> list = new ArrayList<String>();
        Cursor cursor = this.mDB.query(TABLE_HOSTS_SOURCES,
                new String[] { "_id", "url", "enabled" }, "enabled is 1", null, null, null,
                "url asc");
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

    /* WHITELIST */

    public long insertWhitelistItem(String url) {
        insertStmtWhitelist.bindString(1, url);
        insertStmtWhitelist.bindString(2, "1"); // default is enabled
        return insertStmtWhitelist.executeInsert();
    }

    public void deleteWhitelistItem(long rowId) {
        mDB.delete(TABLE_WHITELIST, "_id=" + rowId, null);
    }

    public void updateWhitelistItemURL(long rowId, String url) {
        ContentValues args = new ContentValues();
        args.put("url", url);
        mDB.update(TABLE_WHITELIST, args, "_id=" + rowId, null);
    }

    public void updateWhitelistItemStatus(long rowId, Integer status) {
        ContentValues args = new ContentValues();
        args.put("enabled", status);
        mDB.update(TABLE_WHITELIST, args, "_id=" + rowId, null);
    }

    public Cursor getWhitelistCursor() {
        Cursor cursor = this.mDB.query(TABLE_WHITELIST, new String[] { "_id", "url", "enabled" },
                null, null, null, null, "url asc");

        return cursor;
    }

    public HashSet<String> getAllEnabledWhitelistItems() {
        HashSet<String> list = new HashSet<String>();
        Cursor cursor = this.mDB.query(TABLE_WHITELIST, new String[] { "_id", "url", "enabled" },
                "enabled is 1", null, null, null, "url asc");
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
        mDB.delete(TABLE_BLACKLIST, "_id=" + rowId, null);
    }

    public void updateBlacklistItemURL(long rowId, String url) {
        ContentValues args = new ContentValues();
        args.put("url", url);
        mDB.update(TABLE_BLACKLIST, args, "_id=" + rowId, null);
    }

    public void updateBlacklistItemStatus(long rowId, Integer status) {
        ContentValues args = new ContentValues();
        args.put("enabled", status);
        mDB.update(TABLE_BLACKLIST, args, "_id=" + rowId, null);
    }

    public Cursor getBlacklistCursor() {
        Cursor cursor = this.mDB.query(TABLE_BLACKLIST, new String[] { "_id", "url", "enabled" },
                null, null, null, null, "url asc");

        return cursor;
    }

    public HashSet<String> getAllEnabledBlacklistItems() {
        HashSet<String> list = new HashSet<String>();
        Cursor cursor = this.mDB.query(TABLE_BLACKLIST, new String[] { "_id", "url", "enabled" },
                "enabled is 1", null, null, null, "url asc");
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
        mDB.delete(TABLE_REDIRECTION_LIST, "_id=" + rowId, null);
    }

    public void updateRedirectionItemURL(long rowId, String url, String ip) {
        ContentValues args = new ContentValues();
        args.put("url", url);
        args.put("ip", ip);
        mDB.update(TABLE_REDIRECTION_LIST, args, "_id=" + rowId, null);
    }

    public void updateRedirectionItemStatus(long rowId, Integer status) {
        ContentValues args = new ContentValues();
        args.put("enabled", status);
        mDB.update(TABLE_REDIRECTION_LIST, args, "_id=" + rowId, null);
    }

    public Cursor getRedirectionCursor() {
        Cursor cursor = this.mDB.query(TABLE_REDIRECTION_LIST, new String[] { "_id", "url", "ip",
                "enabled" }, null, null, null, null, "url asc");

        return cursor;
    }

    /* HELPER */

    private static class OpenHelper extends SQLiteOpenHelper {
        OpenHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        public long insertHostsSource(SQLiteStatement insertStmt, String url) {
            insertStmt.bindString(1, url);
            insertStmt.bindString(2, "1"); // default is enabled
            return insertStmt.executeInsert();
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATE_HOSTS_SOURCES);
            db.execSQL(CREATE_WHITELIST);
            db.execSQL(CREATE_BLACKLIST);
            db.execSQL(CREATE_REDIRECTION_LIST);

            // fill default hosts sources
            SQLiteStatement insertStmt;
            String insertHostsSources = "insert into " + TABLE_HOSTS_SOURCES
                    + "(url, enabled) values (?, ?)";
            insertStmt = db.compileStatement(insertHostsSources);

            // http://winhelp2002.mvps.org/hosts.htm
            insertHostsSource(insertStmt, "http://www.mvps.org/winhelp2002/hosts.txt");

            // http://hosts-file.net - This file contains ad/tracking servers in the hpHosts
            // database.
            insertHostsSource(insertStmt, "http://hosts-file.net/ad_servers.asp");

            // not working, because no file GET:
            // insertHostsFile(insertStmt,
            // "http://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=1&mimetype=plaintext");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(Constants.TAG, "Upgrading database...");

            // just stack the update SQLs, because of no return in the cases they are all executed
            switch (oldVersion) {
            case 1:
                db.execSQL(CREATE_WHITELIST);
                db.execSQL(CREATE_BLACKLIST);
                db.execSQL(CREATE_REDIRECTION_LIST);
            case 2:
            case 3:
            case 4:
            default:
                break;
            }

            // drop-recreate upgrade:
            // db.execSQL("DROP TABLE IF EXISTS " + TABLE_HOSTS_SOURCES);
            // onCreate(db);
        }
    }
}