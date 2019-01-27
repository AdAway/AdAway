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

import java.util.Arrays;

import org.adaway.provider.AdAwayContract.Blacklist;
import org.adaway.provider.AdAwayContract.HostsSources;
import org.adaway.provider.AdAwayContract.RedirectionList;
import org.adaway.provider.AdAwayContract.Whitelist;
import org.adaway.provider.AdAwayDatabase.Tables;
import org.adaway.util.Constants;
import org.adaway.util.Log;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;

import androidx.annotation.NonNull;

import android.text.TextUtils;

public class AdAwayProvider extends ContentProvider {
    private static final int HOSTS_SOURCES = 100;
    private static final int HOSTS_SOURCES_ID = 101;
    private static final int WHITELIST = 200;
    private static final int WHITELIST_ID = 201;
    private static final int BLACKLIST = 300;
    private static final int BLACKLIST_ID = 301;
    private static final int REDIRECTION_LIST = 400;
    private static final int REDIRECTION_LIST_ID = 401;
    private static final UriMatcher sUriMatcher = buildUriMatcher();
    private AdAwayDatabase mAdAwayDatabase;

    /**
     * Build and return a {@link UriMatcher} that catches all {@link Uri} variations supported by
     * this {@link ContentProvider}.
     */
    private static UriMatcher buildUriMatcher() {
        final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
        final String authority = AdAwayContract.CONTENT_AUTHORITY;

        // Hosts sources
        matcher.addURI(authority, AdAwayContract.PATH_HOSTS_SOURCES, HOSTS_SOURCES);
        matcher.addURI(authority, AdAwayContract.PATH_HOSTS_SOURCES + "/#", HOSTS_SOURCES_ID);

        // Whitelist
        matcher.addURI(authority, AdAwayContract.PATH_WHITELIST, WHITELIST);
        matcher.addURI(authority, AdAwayContract.PATH_WHITELIST + "/#", WHITELIST_ID);

        // Blacklist
        matcher.addURI(authority, AdAwayContract.PATH_BLACKLIST, BLACKLIST);
        matcher.addURI(authority, AdAwayContract.PATH_BLACKLIST + "/#", BLACKLIST_ID);

        // Redirection list
        matcher.addURI(authority, AdAwayContract.PATH_REDIRECTION_LIST, REDIRECTION_LIST);
        matcher.addURI(authority, AdAwayContract.PATH_REDIRECTION_LIST + "/#", REDIRECTION_LIST_ID);

        return matcher;
    }

    @Override
    public boolean onCreate() {
        final Context context = getContext();
        mAdAwayDatabase = new AdAwayDatabase(context);
        return true;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        final int match = sUriMatcher.match(uri);
        switch (match) {
            case HOSTS_SOURCES:
                return HostsSources.CONTENT_TYPE;
            case HOSTS_SOURCES_ID:
                return HostsSources.CONTENT_ITEM_TYPE;
            case WHITELIST:
                return Whitelist.CONTENT_TYPE;
            case WHITELIST_ID:
                return Whitelist.CONTENT_ITEM_TYPE;
            case BLACKLIST:
                return Blacklist.CONTENT_TYPE;
            case BLACKLIST_ID:
                return Blacklist.CONTENT_ITEM_TYPE;
            case REDIRECTION_LIST:
                return RedirectionList.CONTENT_TYPE;
            case REDIRECTION_LIST_ID:
                return RedirectionList.CONTENT_ITEM_TYPE;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        Log.d(Constants.TAG, "insert(uri=" + uri + ", values=" + values.toString() + ")");

        final SQLiteDatabase db = mAdAwayDatabase.getWritableDatabase();

        Uri rowUri = null;
        long rowId;
        try {
            final int match = sUriMatcher.match(uri);
            switch (match) {
                case HOSTS_SOURCES:
                    rowId = db.insertOrThrow(Tables.HOSTS_SOURCES, null, values);
                    rowUri = HostsSources.buildUri(Long.toString(rowId));
                    break;
                case WHITELIST:
                    rowId = db.insertOrThrow(Tables.WHITELIST, null, values);
                    rowUri = Whitelist.buildUri(Long.toString(rowId));
                    break;
                case BLACKLIST:
                    rowId = db.insertOrThrow(Tables.BLACKLIST, null, values);
                    rowUri = Blacklist.buildUri(Long.toString(rowId));
                    break;
                case REDIRECTION_LIST:
                    rowId = db.insertOrThrow(Tables.REDIRECTION_LIST, null, values);
                    rowUri = RedirectionList.buildUri(Long.toString(rowId));
                    break;
                default:
                    throw new UnsupportedOperationException("Unknown uri: " + uri);
            }
        } catch (SQLiteConstraintException e) {
            Log.e(Constants.TAG, "Constraint exception on insert! Entry already existing?");
        }

        // notify of changes in db
        getContext().getContentResolver().notifyChange(uri, null);

        return rowUri;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Log.v(Constants.TAG, "query(uri=" + uri + ", proj=" + Arrays.toString(projection) + ")");

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        SQLiteDatabase db = mAdAwayDatabase.getReadableDatabase();

        final int match = sUriMatcher.match(uri);
        switch (match) {
            case HOSTS_SOURCES:
                qb.setTables(Tables.HOSTS_SOURCES);
                break;
            case WHITELIST:
                qb.setTables(Tables.WHITELIST);
                break;
            case BLACKLIST:
                qb.setTables(Tables.BLACKLIST);
                break;
            case REDIRECTION_LIST:
                qb.setTables(Tables.REDIRECTION_LIST);
                break;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }

        Cursor cursor = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
        // notify through cursor
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        Log.v(Constants.TAG, "update(uri=" + uri + ", values=" + values.toString() + ")");

        final SQLiteDatabase db = mAdAwayDatabase.getWritableDatabase();

        int count = 0;
        try {
            final int match = sUriMatcher.match(uri);
            switch (match) {
                case HOSTS_SOURCES_ID:
                    count = db.update(Tables.HOSTS_SOURCES, values,
                            buildDefaultSelection(uri, selection), selectionArgs);
                    break;
                case WHITELIST_ID:
                    count = db.update(Tables.WHITELIST, values, buildDefaultSelection(uri, selection),
                            selectionArgs);
                    break;
                case BLACKLIST_ID:
                    count = db.update(Tables.BLACKLIST, values, buildDefaultSelection(uri, selection),
                            selectionArgs);
                    break;
                case REDIRECTION_LIST_ID:
                    count = db.update(Tables.REDIRECTION_LIST, values,
                            buildDefaultSelection(uri, selection), selectionArgs);
                    break;
                default:
                    throw new UnsupportedOperationException("Unknown uri: " + uri);
            }
        } catch (SQLiteConstraintException e) {
            Log.e(Constants.TAG, "Constraint exception on update! Entry already existing?");
        }

        // notify of changes in db
        getContext().getContentResolver().notifyChange(uri, null);

        return count;
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        Log.v(Constants.TAG, "delete(uri=" + uri + ")");

        final SQLiteDatabase db = mAdAwayDatabase.getWritableDatabase();

        int count;
        final int match = sUriMatcher.match(uri);
        switch (match) {
            case HOSTS_SOURCES_ID:
                count = db.delete(Tables.HOSTS_SOURCES, buildDefaultSelection(uri, selection),
                        selectionArgs);
                break;
            case WHITELIST_ID:
                count = db.delete(Tables.WHITELIST, buildDefaultSelection(uri, selection),
                        selectionArgs);
                break;
            case BLACKLIST_ID:
                count = db.delete(Tables.BLACKLIST, buildDefaultSelection(uri, selection),
                        selectionArgs);
                break;
            case REDIRECTION_LIST_ID:
                count = db.delete(Tables.REDIRECTION_LIST, buildDefaultSelection(uri, selection),
                        selectionArgs);
                break;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }

        // notify of changes in db
        getContext().getContentResolver().notifyChange(uri, null);

        return count;
    }

    /**
     * Build default selection statement. If no extra selection is specified only build where clause
     * with rowId
     *
     * @param uri
     * @param selection
     * @return
     */
    private String buildDefaultSelection(Uri uri, String selection) {
        String rowId = uri.getPathSegments().get(1);
        String where = "";
        if (!TextUtils.isEmpty(selection)) {
            where = " AND (" + selection + ")";
        }

        return BaseColumns._ID + "=" + rowId + where;
    }
}