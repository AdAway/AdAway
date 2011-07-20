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

package org.adaway;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import java.util.ArrayList;

public class HostsDatabase {

    private static final String DATABASE_NAME = "adaway.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_NAME = "hosts_files";

    private Context context;
    private SQLiteDatabase db;

    private SQLiteStatement insertStmt;
    private static final String INSERT = "insert into " + TABLE_NAME
            + "(url, enabled) values (?, ?)";

    public HostsDatabase(Context context) {
        this.context = context;
        OpenHelper openHelper = new OpenHelper(this.context);
        this.db = openHelper.getWritableDatabase();
        this.insertStmt = this.db.compileStatement(INSERT);
    }

    public long insertHostsFile(String url) {
        this.insertStmt.bindString(1, url);
        this.insertStmt.bindString(2, "1"); // default is enabled
        return this.insertStmt.executeInsert();
    }

    public void modifyEnabled(long rowId, Integer status) {
        ContentValues args = new ContentValues();
        args.put("enabled", status);
        db.update(TABLE_NAME, args, "_id=" + rowId, null);
    }

    public void deleteAll() {
        this.db.delete(TABLE_NAME, null, null);
    }

    public Cursor getHostsCursor() {
        Cursor cursor = this.db.query(TABLE_NAME, new String[] { "_id", "url", "enabled" }, null,
                null, null, null, "url desc");

        return cursor;
    }

    public ArrayList<String> getAllEnabledHosts() {
        ArrayList<String> list = new ArrayList<String>();
        Cursor cursor = this.db.query(TABLE_NAME, new String[] { "_id", "url", "enabled" },
                "enabled is 1", null, null, null, "url desc");
        if (cursor.moveToFirst()) {
            do {
                list.add(cursor.getString(0));
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

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE_NAME
                    + "(_id INTEGER PRIMARY KEY, url TEXT, enabled INTEGER)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w("Example", "Upgrading database, this will drop tables and recreate.");
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
            onCreate(db);
        }
    }
}