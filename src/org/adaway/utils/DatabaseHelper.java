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

public class DatabaseHelper {

    static final String TAG = "AdAway";

    private static final String DATABASE_NAME = "adaway.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_HOSTS_SOURCES = "hosts_sources";

    private Context context;
    private SQLiteDatabase db;

    private SQLiteStatement insertStmt;
    private static final String INSERT = "insert into " + TABLE_HOSTS_SOURCES
            + "(url, enabled) values (?, ?)";

    public DatabaseHelper(Context context) {
        this.context = context;
        OpenHelper openHelper = new OpenHelper(this.context);
        this.db = openHelper.getWritableDatabase();
        this.insertStmt = this.db.compileStatement(INSERT);
    }

    /**
     * Close the database helper.
     * 
     * TODO: needed?
     */
    public void close() {
        db.close();
    }

    public long insertHostsSource(String url) {
        insertStmt.bindString(1, url);
        insertStmt.bindString(2, "1"); // default is enabled
        return insertStmt.executeInsert();
    }

    public void deleteHostsSource(long rowId) {
        db.delete(TABLE_HOSTS_SOURCES, "_id=" + rowId, null);
    }

    public void updateHostsSource(long rowId, String url) {
        ContentValues args = new ContentValues();
        args.put("url", url);
        db.update(TABLE_HOSTS_SOURCES, args, "_id=" + rowId, null);
    }

    /**
     * Change status
     * 
     * @param rowId
     * @param status
     */
    public void changeStatus(long rowId, Integer status) {
        ContentValues args = new ContentValues();
        args.put("enabled", status);
        db.update(TABLE_HOSTS_SOURCES, args, "_id=" + rowId, null);
    }

    public void deleteAllHostsSources() {
        db.delete(TABLE_HOSTS_SOURCES, null, null);
    }

    public Cursor getHostsSourcesCursor() {
        Cursor cursor = this.db.query(TABLE_HOSTS_SOURCES,
                new String[] { "_id", "url", "enabled" }, null, null, null, null, "url asc");

        return cursor;
    }

    public ArrayList<String> getAllEnabledHostsSources() {
        ArrayList<String> list = new ArrayList<String>();
        Cursor cursor = this.db.query(TABLE_HOSTS_SOURCES,
                new String[] { "_id", "url", "enabled" }, "enabled is 1", null, null, null,
                "url desc");
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

    private static class OpenHelper extends SQLiteOpenHelper {
        OpenHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        public long insertHostsFile(SQLiteStatement insertStmt, String url) {
            insertStmt.bindString(1, url);
            insertStmt.bindString(2, "1"); // default is enabled
            return insertStmt.executeInsert();
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE_HOSTS_SOURCES
                    + "(_id INTEGER PRIMARY KEY, url TEXT, enabled INTEGER)");

            // fill default hosts sources
            // TODO: This seems to be also done on upgrading! Problem?
            SQLiteStatement insertStmt;
            String INSERT = "insert into " + TABLE_HOSTS_SOURCES + "(url, enabled) values (?, ?)";
            insertStmt = db.compileStatement(INSERT);

            insertHostsFile(insertStmt, "http://www.mvps.org/winhelp2002/hosts.txt");
            // not working, because no file GET:
            // insertHostsFile(insertStmt,
            // "http://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=1&mimetype=plaintext");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database, this will drop tables and recreate.");
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_HOSTS_SOURCES);
            onCreate(db);
        }
    }
}