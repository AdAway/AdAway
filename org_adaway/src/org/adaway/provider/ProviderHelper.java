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

package org.adaway.provider;

import org.adaway.provider.AdAwayContract.Blacklist;
import org.adaway.provider.AdAwayContract.HostsSources;
import org.adaway.provider.AdAwayContract.RedirectionList;
import org.adaway.provider.AdAwayContract.Whitelist;

import android.content.ContentValues;
import android.content.Context;

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

    /* WHITELIST */

    public static void insertWhitelistItem(Context context, String hostname) {
        ContentValues values = new ContentValues();
        values.put(Whitelist.URL, hostname);
        values.put(Whitelist.ENABLED, true); // default is enabled
        context.getContentResolver().insert(Whitelist.CONTENT_URI, values);
    }

    public static void deleteWhitelistItem(Context context, long rowId) {
        context.getContentResolver().delete(Whitelist.buildUri(Long.toString(rowId)), null, null);
    }

    public static void updateWhitelistItemHostname(Context context, long rowId, String hostname) {
        ContentValues values = new ContentValues();
        values.put(Whitelist.URL, hostname);
        context.getContentResolver().update(Whitelist.buildUri(Long.toString(rowId)), values, null,
                null);
    }

    public static void updateWhitelistItemEnabled(Context context, long rowId, boolean enabled) {
        ContentValues values = new ContentValues();
        values.put(Whitelist.ENABLED, enabled);
        context.getContentResolver().update(Whitelist.buildUri(Long.toString(rowId)), values, null,
                null);
    }

    /* BLACKLIST */

    public static void insertBlacklistItem(Context context, String hostname) {
        ContentValues values = new ContentValues();
        values.put(Blacklist.URL, hostname);
        values.put(Blacklist.ENABLED, true); // default is enabled
        context.getContentResolver().insert(Blacklist.CONTENT_URI, values);
    }

    public static void deleteBlacklistItem(Context context, long rowId) {
        context.getContentResolver().delete(Blacklist.buildUri(Long.toString(rowId)), null, null);
    }

    public static void updateBlacklistItemHostname(Context context, long rowId, String hostname) {
        ContentValues values = new ContentValues();
        values.put(Blacklist.URL, hostname);
        context.getContentResolver().update(Blacklist.buildUri(Long.toString(rowId)), values, null,
                null);
    }

    public static void updateBlacklistItemEnabled(Context context, long rowId, boolean enabled) {
        ContentValues values = new ContentValues();
        values.put(Blacklist.ENABLED, enabled);
        context.getContentResolver().update(Blacklist.buildUri(Long.toString(rowId)), values, null,
                null);
    }

    /* REDIRECTION LIST */

    public static void insertRedirectionListItem(Context context, String hostname, String ip) {
        ContentValues values = new ContentValues();
        values.put(RedirectionList.URL, hostname);
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
        values.put(RedirectionList.URL, hostname);
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

}
