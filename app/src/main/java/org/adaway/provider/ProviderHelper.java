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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.Nullable;

import org.adaway.provider.AdAwayContract.Blacklist;
import org.adaway.provider.AdAwayContract.HostsSources;
import org.adaway.provider.AdAwayContract.ListColumns;
import org.adaway.provider.AdAwayContract.RedirectionList;
import org.adaway.provider.AdAwayContract.Whitelist;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.THashSet;

public class ProviderHelper {

    /* HOSTS SOURCES */

    public static void insertHostsSource(Context context, String url) {
        ContentValues values = new ContentValues();
        values.put(HostsSources.URL, url);
        values.put(HostsSources.ENABLED, true); // default is enabled
        values.put(HostsSources.LAST_MODIFIED_LOCAL, 0); // last_modified_local starts at 0
        values.put(HostsSources.LAST_MODIFIED_ONLINE, 0); // last_modified_online starts at 0
        context.getContentResolver().insert(HostsSources.CONTENT_URI, values);
    }

    public static void deleteHostsSource(Context context, long rowId) {
        context.getContentResolver()
                .delete(HostsSources.buildUri(Long.toString(rowId)), null, null);
    }

    public static void updateHostsSourceUrl(Context context, long rowId, String url) {
        ContentValues values = new ContentValues();
        values.put(HostsSources.URL, url);
        context.getContentResolver().update(HostsSources.buildUri(Long.toString(rowId)), values,
                null, null);
    }

    public static void updateHostsSourceEnabled(Context context, long rowId, boolean enabled) {
        ContentValues values = new ContentValues();
        values.put(HostsSources.ENABLED, enabled);
        context.getContentResolver().update(HostsSources.buildUri(Long.toString(rowId)), values,
                null, null);
    }

    public static void updateHostsSourceLastModifiedLocal(Context context, long rowId,
                                                          long last_modified_local) {
        ContentValues values = new ContentValues();
        values.put(HostsSources.LAST_MODIFIED_LOCAL, last_modified_local);
        context.getContentResolver().update(HostsSources.buildUri(Long.toString(rowId)), values,
                null, null);
    }

    public static void updateHostsSourceLastModifiedOnline(Context context, long rowId,
                                                           long last_modified_online) {
        ContentValues values = new ContentValues();
        values.put(HostsSources.LAST_MODIFIED_ONLINE, last_modified_online);
        context.getContentResolver().update(HostsSources.buildUri(Long.toString(rowId)), values,
                null, null);
    }

    public static @Nullable Cursor getEnabledHostsSourcesCursor(Context context) {
        return context.getContentResolver().query(
                HostsSources.CONTENT_URI,
                new String[]{HostsSources._ID, HostsSources.URL,
                        HostsSources.LAST_MODIFIED_LOCAL, HostsSources.LAST_MODIFIED_ONLINE,
                        HostsSources.ENABLED}, HostsSources.ENABLED + "=1", null,
                HostsSources.DEFAULT_SORT);
    }

    /**
     * Returns all hosts sources that are enabled as ArrayList
     *
     * @param context
     * @return
     */
    public static ArrayList<String> getEnabledHostsSourcesArrayList(Context context) {
        ArrayList<String> list = new ArrayList<>();
        try (Cursor cursor = getEnabledHostsSourcesCursor(context)) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    list.add(cursor.getString(cursor.getColumnIndexOrThrow(HostsSources.URL)));
                } while (cursor.moveToNext());
            }
        }
        return list;
    }

    /**
     * Go through all enabled hosts sources and set local last modified to online last modified
     *
     * @param context
     * @return
     */
    public static void updateAllEnabledHostsSourcesLastModifiedLocalFromOnline(Context context) {
        try (Cursor cursor = getEnabledHostsSourcesCursor(context)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idCol = cursor.getColumnIndex(HostsSources._ID);
                int lastModifiedOnlineCol = cursor.getColumnIndex(HostsSources.LAST_MODIFIED_ONLINE);

                long lastModifiedOnline;
                long id;

                do {
                    lastModifiedOnline = cursor.getLong(lastModifiedOnlineCol);
                    id = cursor.getLong(idCol);

                    // set last_modified_local to last modified_online
                    updateHostsSourceLastModifiedLocal(context, id, lastModifiedOnline);

                } while (cursor.moveToNext());
            }
        }
    }

    /* BLACKLIST */

    /**
     * Insert blacklist item into database.
     *
     * @param context  The application context.
     * @param hostname The hostname of the blacklist item to insert.
     */
    public static void insertBlacklistItem(Context context, String hostname) {
        ProviderHelper.insertListItem(context, Blacklist.CONTENT_URI, hostname);
    }

    public static void deleteBlacklistItem(Context context, long rowId) {
        context.getContentResolver().delete(Blacklist.buildUri(Long.toString(rowId)), null, null);
    }

    public static void updateBlacklistItemHostname(Context context, long rowId, String hostname) {
        ContentValues values = new ContentValues();
        values.put(Blacklist.HOSTNAME, hostname);
        context.getContentResolver().update(Blacklist.buildUri(Long.toString(rowId)), values, null,
                null);
    }

    public static void updateBlacklistItemEnabled(Context context, long rowId, boolean enabled) {
        ContentValues values = new ContentValues();
        values.put(Blacklist.ENABLED, enabled);
        context.getContentResolver().update(Blacklist.buildUri(Long.toString(rowId)), values, null,
                null);
    }

    private static Cursor getEnabledBlacklistCursor(Context context) {
        return context.getContentResolver().query(Blacklist.CONTENT_URI,
                new String[]{Blacklist._ID, Blacklist.HOSTNAME, Blacklist.ENABLED},
                Blacklist.ENABLED + "=1", null, Blacklist.DEFAULT_SORT);
    }

    /**
     * Returns all blacklist items, that are enabled as THashSet
     *
     * @param context
     * @return
     */
    public static Set<String> getEnabledBlacklistHashSet(Context context) {
        Set<String> list = new THashSet<>();

        try (Cursor cursor = getEnabledBlacklistCursor(context)) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    list.add(cursor.getString(cursor.getColumnIndexOrThrow(Blacklist.HOSTNAME)));
                } while (cursor.moveToNext());
            }
        }

        return list;
    }

    /**
     * Import blacklist items into database.
     *
     * @param context   The application context.
     * @param blacklist The blacklist items.
     */
    public static void importBlacklist(Context context, Set<String> blacklist) {
        ProviderHelper.importList(context, Blacklist.CONTENT_URI, blacklist);
    }

    /* WHITELIST */

    /**
     * Insert blacklist item into database.
     *
     * @param context  The application context.
     * @param hostname The hostname of the blacklist item to insert.
     */
    public static void insertWhitelistItem(Context context, String hostname) {
        ProviderHelper.insertListItem(context, Whitelist.CONTENT_URI, hostname);
    }

    /**
     * Delete whitelist item.
     *
     * @param context The application context.
     * @param rowId   The whitelist item row identifier to delete.
     */
    public static void deleteWhitelistItem(Context context, long rowId) {
        context.getContentResolver().delete(Whitelist.buildUri(Long.toString(rowId)), null, null);
    }

    /**
     * Update whitelist item hostname.
     *
     * @param context  The application context.
     * @param rowId    The whitelist item row identifier to update.
     * @param hostname The hostname to set to the whitelist item.
     */
    public static void updateWhitelistItemHostname(Context context, long rowId, String hostname) {
        ContentValues values = new ContentValues();
        values.put(Whitelist.HOSTNAME, hostname);
        context.getContentResolver().update(Whitelist.buildUri(Long.toString(rowId)), values, null,
                null);
    }

    /**
     * Update whitelist item enabled status.
     *
     * @param context The application context.
     * @param rowId   The whitelist item row identifier to update.
     * @param enabled The enabled status to set to the whitelist item.
     */
    public static void updateWhitelistItemEnabled(Context context, long rowId, boolean enabled) {
        ContentValues values = new ContentValues();
        values.put(Whitelist.ENABLED, enabled);
        context.getContentResolver().update(Whitelist.buildUri(Long.toString(rowId)), values, null,
                null);
    }

    private static Cursor getEnabledWhitelistCursor(Context context) {
        return context.getContentResolver().query(Whitelist.CONTENT_URI,
                new String[]{Whitelist._ID, Whitelist.HOSTNAME, Whitelist.ENABLED},
                Whitelist.ENABLED + "=1", null, Whitelist.DEFAULT_SORT);
    }

    /**
     * Returns all enabled whitelist items
     *
     * @param context The application context.
     * @return All enabled whitelist items.
     */
    public static Set<String> getEnabledWhitelistHashSet(Context context) {
        Set<String> list = new THashSet<>();
        try (Cursor cursor = getEnabledWhitelistCursor(context)) {
            if (cursor.moveToFirst()) {
                do {
                    list.add(cursor.getString(cursor.getColumnIndexOrThrow(Whitelist.HOSTNAME)));
                } while (cursor.moveToNext());
            }
        }
        return list;
    }

    /**
     * Import whitelist items into database.
     *
     * @param context   The application context.
     * @param whitelist The whitelist items.
     */
    public static void importWhitelist(Context context, Set<String> whitelist) {
        ProviderHelper.importList(context, Whitelist.CONTENT_URI, whitelist);
    }

    /* REDIRECTION LIST */

    public static void insertRedirectionListItem(Context context, String hostname, String ip) {
        ContentValues values = new ContentValues();
        values.put(RedirectionList.HOSTNAME, hostname);
        values.put(RedirectionList.IP, ip);
        values.put(RedirectionList.ENABLED, true); // default is enabled
        context.getContentResolver().insert(RedirectionList.CONTENT_URI, values);
    }

    public static void deleteRedirectionListItem(Context context, long rowId) {
        context.getContentResolver().delete(RedirectionList.buildUri(Long.toString(rowId)), null,
                null);
    }

    public static void updateRedirectionListItemHostnameAndIp(Context context, long rowId,
                                                              String hostname, String ip) {
        ContentValues values = new ContentValues();
        values.put(RedirectionList.HOSTNAME, hostname);
        values.put(RedirectionList.IP, ip);
        context.getContentResolver().update(RedirectionList.buildUri(Long.toString(rowId)), values,
                null, null);
    }

    public static void updateRedirectionListItemEnabled(Context context, long rowId, boolean enabled) {
        ContentValues values = new ContentValues();
        values.put(RedirectionList.ENABLED, enabled);
        context.getContentResolver().update(RedirectionList.buildUri(Long.toString(rowId)), values,
                null, null);
    }

    public static Cursor getEnabledRedirectionListCursor(Context context) {
        return context.getContentResolver().query(
                RedirectionList.CONTENT_URI,
                new String[]{RedirectionList._ID, RedirectionList.HOSTNAME, RedirectionList.IP,
                        RedirectionList.ENABLED}, RedirectionList.ENABLED + "=1", null,
                RedirectionList.DEFAULT_SORT);
    }

    public static Map<String, String> getEnabledRedirectionListHashMap(Context context) {
        Map<String, String> list = new THashMap<>();
        try (Cursor cursor = getEnabledRedirectionListCursor(context)) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    list.put(cursor.getString(cursor.getColumnIndexOrThrow(RedirectionList.HOSTNAME)),
                            cursor.getString(cursor.getColumnIndexOrThrow(RedirectionList.IP)));
                } while (cursor.moveToNext());
            }
        }

        return list;
    }

    /**
     * Import redirect list items into database.
     *
     * @param context  The application context.
     * @param redirect The redirect list items.
     */
    public static void importRedirectionList(Context context, Map<String, String> redirect) {
        ProviderHelper.importList(context, RedirectionList.CONTENT_URI, RedirectionList.IP, redirect);
    }

    /**
     * Insert list item into database.
     *
     * @param context  The application context.
     * @param tableUrl The URL of the table to insert list item into.
     * @param hostname The hostname of the list item to insert.
     */
    private static void insertListItem(Context context, Uri tableUrl, String hostname) {
        ContentValues values = new ContentValues();
        values.put(ListColumns.HOSTNAME, hostname);
        values.put(ListColumns.ENABLED, true); // default is enabled
        context.getContentResolver().insert(tableUrl, values);
    }

    /**
     * Import list items into database.
     *
     * @param context  The application context.
     * @param tableUrl The URL of the table to insert list items into.
     * @param list     The list items.
     */
    private static void importList(Context context, Uri tableUrl, Set<String> list) {
        // build values array based on set
        ContentValues[] values = new ContentValues[list.size()];
        int i = 0;
        for (String item : list) {
            ContentValues contentValues = new ContentValues();
            contentValues.put(ListColumns.HOSTNAME, item);
            contentValues.put(ListColumns.ENABLED, true); // default is enabled
            values[i] = contentValues;
            i++;
        }
        // insert as bulk operation
        context.getContentResolver().bulkInsert(tableUrl, values);
    }

    /**
     * Import list items into database.
     *
     * @param context     The application context.
     * @param tableUrl    The URL of the table to insert list items into.
     * @param valueColumn The column name for the item value.
     * @param list        The list items.
     */
    private static void importList(Context context, Uri tableUrl, String valueColumn,
                                   Map<String, String> list) {
        // build values array based on map
        ContentValues[] values = new ContentValues[list.size()];
        int i = 0;
        for (HashMap.Entry<String, String> item : list.entrySet()) {
            ContentValues contentValues = new ContentValues();
            contentValues.put(ListColumns.HOSTNAME, item.getKey());
            contentValues.put(valueColumn, item.getValue());
            contentValues.put(ListColumns.ENABLED, true); // default is enabled
            values[i] = contentValues;
            i++;
        }
        // insert as bulk operation
        context.getContentResolver().bulkInsert(tableUrl, values);
    }
}
