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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.adaway.provider.AdAwayContract.Blacklist;
import org.adaway.provider.AdAwayContract.HostsSources;
import org.adaway.provider.AdAwayContract.RedirectionList;
import org.adaway.provider.AdAwayContract.Whitelist;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import gnu.trove.set.hash.THashSet;
import gnu.trove.map.hash.THashMap;

public class ProviderHelper {

    /* HOSTS SOURCES */

    public static void insertHostsSource(Context context, String url) {
        ContentValues values = new ContentValues();
        values.put(HostsSources.URL, url);
        values.put(HostsSources.ENABLED, true); // default is enabled
        values.put(HostsSources.LAST_MODIFIED_LOCAL, 0); // last_modified_local starts at 0
        values.put(HostsSources.LAST_MODIFIED_ONLINE, 0); // last_modified_onlinestarts at 0
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

    public static Cursor getEnabledHostsSourcesCursor(Context context) {
        return context.getContentResolver().query(
                HostsSources.CONTENT_URI,
                new String[] { HostsSources._ID, HostsSources.URL,
                        HostsSources.LAST_MODIFIED_LOCAL, HostsSources.LAST_MODIFIED_ONLINE,
                        HostsSources.ENABLED }, HostsSources.ENABLED + "=1", null,
                HostsSources.DEFAULT_SORT);
    }

    /**
     * Returns all hosts sources that are enabled as ArrayList
     * 
     * @param context
     * @return
     */
    public static ArrayList<String> getEnabledHostsSourcesArrayList(Context context) {
        ArrayList<String> list = new ArrayList<String>();
        Cursor cursor = getEnabledHostsSourcesCursor(context);

        if (cursor.moveToFirst()) {
            do {
                list.add(cursor.getString(cursor.getColumnIndexOrThrow(HostsSources.URL)));
            } while (cursor.moveToNext());
        }
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
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
        Cursor cursor = getEnabledHostsSourcesCursor(context);
        int idCol = cursor.getColumnIndex(HostsSources._ID);
        int lastModifiedOnlineCol = cursor.getColumnIndex(HostsSources.LAST_MODIFIED_ONLINE);

        long lastModifiedOnline;
        long id;

        if (cursor.moveToFirst()) {
            do {
                lastModifiedOnline = cursor.getLong(lastModifiedOnlineCol);
                id = cursor.getLong(idCol);

                // set last_modified_local to last modified_online
                updateHostsSourceLastModifiedLocal(context, id, lastModifiedOnline);

            } while (cursor.moveToNext());
        }
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }
    }

    /* BLACKLIST */

    public static void insertBlacklistItem(Context context, String hostname) {
        ContentValues values = new ContentValues();
        values.put(Blacklist.HOSTNAME, hostname);
        values.put(Blacklist.ENABLED, true); // default is enabled
        context.getContentResolver().insert(Blacklist.CONTENT_URI, values);
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

    public static Cursor getEnabledBlacklistCursor(Context context) {
        return context.getContentResolver().query(Blacklist.CONTENT_URI,
                new String[] { Blacklist._ID, Blacklist.HOSTNAME, Blacklist.ENABLED },
                Blacklist.ENABLED + "=1", null, Blacklist.DEFAULT_SORT);
    }

    /**
     * Returns all blacklist items, that are enabled as THashSet
     * 
     * @param context
     * @return
     */
    public static THashSet<String> getEnabledBlacklistHashSet(Context context) {
        THashSet<String> list = new THashSet<String>();
        Cursor cursor = getEnabledBlacklistCursor(context);

        if (cursor.moveToFirst()) {
            do {
                list.add(cursor.getString(cursor.getColumnIndexOrThrow(Blacklist.HOSTNAME)));
            } while (cursor.moveToNext());
        }
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return list;
    }

    /**
     * Imports blacklist from THashSet<String> into database of AdAway
     * 
     * @param context
     * @param whitelist
     */
    public static void importBlacklist(Context context, THashSet<String> blacklist) {
        ContentValues[] values = new ContentValues[blacklist.size()];

        // build values array based on THashSet
        Iterator<String> itr = blacklist.iterator();
        int i = 0;
        while (itr.hasNext()) {
            values[i] = new ContentValues();
            values[i].put(Blacklist.HOSTNAME, itr.next());
            values[i].put(Blacklist.ENABLED, true); // default is enabled

            i++;
        }

        // insert as bulk operation
        context.getContentResolver().bulkInsert(Blacklist.CONTENT_URI, values);
    }

    /* WHITELIST */

    public static void insertWhitelistItem(Context context, String hostname) {
        ContentValues values = new ContentValues();
        values.put(Whitelist.HOSTNAME, hostname);
        values.put(Whitelist.ENABLED, true); // default is enabled
        context.getContentResolver().insert(Whitelist.CONTENT_URI, values);
    }

    public static void deleteWhitelistItem(Context context, long rowId) {
        context.getContentResolver().delete(Whitelist.buildUri(Long.toString(rowId)), null, null);
    }

    public static void updateWhitelistItemHostname(Context context, long rowId, String hostname) {
        ContentValues values = new ContentValues();
        values.put(Whitelist.HOSTNAME, hostname);
        context.getContentResolver().update(Whitelist.buildUri(Long.toString(rowId)), values, null,
                null);
    }

    public static void updateWhitelistItemEnabled(Context context, long rowId, boolean enabled) {
        ContentValues values = new ContentValues();
        values.put(Whitelist.ENABLED, enabled);
        context.getContentResolver().update(Whitelist.buildUri(Long.toString(rowId)), values, null,
                null);
    }

    public static Cursor getEnabledWhitelistCursor(Context context) {
        return context.getContentResolver().query(Whitelist.CONTENT_URI,
                new String[] { Whitelist._ID, Whitelist.HOSTNAME, Whitelist.ENABLED },
                Whitelist.ENABLED + "=1", null, Whitelist.DEFAULT_SORT);
    }

    /**
     * Returns all whitelist items, that are enabled as THashSet
     * 
     * @param context
     * @return
     */
    public static THashSet<String> getEnabledWhitelistHashSet(Context context) {
        THashSet<String> list = new THashSet<String>();
        Cursor cursor = getEnabledWhitelistCursor(context);

        if (cursor.moveToFirst()) {
            do {
                list.add(cursor.getString(cursor.getColumnIndexOrThrow(Whitelist.HOSTNAME)));
            } while (cursor.moveToNext());
        }
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return list;
    }

    /**
     * Imports whitelist from THashSet<String> into database of AdAway
     * 
     * @param context
     * @param whitelist
     */
    public static void importWhitelist(Context context, THashSet<String> whitelist) {
        ContentValues[] values = new ContentValues[whitelist.size()];

        // build values array based on THashSet
        Iterator<String> itr = whitelist.iterator();
        int i = 0;
        while (itr.hasNext()) {
            values[i] = new ContentValues();
            values[i].put(Whitelist.HOSTNAME, itr.next());
            values[i].put(Whitelist.ENABLED, true); // default is enabled

            i++;
        }

        // insert as bulk operation
        context.getContentResolver().bulkInsert(Whitelist.CONTENT_URI, values);
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
                new String[] { RedirectionList._ID, RedirectionList.HOSTNAME, RedirectionList.IP,
                        RedirectionList.ENABLED }, RedirectionList.ENABLED + "=1", null,
                RedirectionList.DEFAULT_SORT);
    }

    public static THashMap<String, String> getEnabledRedirectionListHashMap(Context context) {
        THashMap<String, String> list = new THashMap<String, String>();
        Cursor cursor = getEnabledRedirectionListCursor(context);

        if (cursor.moveToFirst()) {
            do {
                list.put(cursor.getString(cursor.getColumnIndexOrThrow(RedirectionList.HOSTNAME)),
                        cursor.getString(cursor.getColumnIndexOrThrow(RedirectionList.IP)));
            } while (cursor.moveToNext());
        }
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return list;
    }

    /**
     * Imports redirection list from THashMap<String, String> into database of AdAway, where keys are
     * hostnames and values are ip addresses.
     * 
     * @param context
     * @param whitelist
     */
    public static void importRedirectionList(Context context,
            THashMap<String, String> redirectionList) {
        ContentValues[] values = new ContentValues[redirectionList.size()];

        int i = 0;
        for (HashMap.Entry<String, String> item : redirectionList.entrySet()) {
            values[i] = new ContentValues();
            values[i].put(RedirectionList.HOSTNAME, item.getKey());
            values[i].put(RedirectionList.IP, item.getValue());
            values[i].put(RedirectionList.ENABLED, true); // default is enabled

            i++;
        }

        // insert as bulk operation
        context.getContentResolver().bulkInsert(RedirectionList.CONTENT_URI, values);
    }

}
