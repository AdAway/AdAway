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
import android.database.Cursor;
import android.support.annotation.Nullable;

import org.adaway.provider.AdAwayContract.Blacklist;
import org.adaway.provider.AdAwayContract.HostsSources;
import org.adaway.provider.AdAwayContract.RedirectionList;
import org.adaway.provider.AdAwayContract.Whitelist;

public class ProviderHelper {
    /**
     * Private constructor.
     */
    private ProviderHelper() {

    }

    /*
     * ONLY FOR MIGRATION TO ROOM / NOT A PUBLIC API.
     */
    @Nullable
    static Cursor getHostsSourcesCursor(Context context) {
        return context.getContentResolver().query(
                HostsSources.CONTENT_URI,
                new String[]{
                        HostsSources.URL,
                        HostsSources.ENABLED,
                        HostsSources.LAST_MODIFIED_LOCAL,
                        HostsSources.LAST_MODIFIED_ONLINE
                },
                null,
                null,
                HostsSources.DEFAULT_SORT
        );
    }

    @Nullable
    static Cursor getBlackListItemsCursor(Context context) {
        return context.getContentResolver().query(
                Blacklist.CONTENT_URI,
                new String[]{
                        Blacklist.HOSTNAME,
                        Blacklist.ENABLED
                },
                null,
                null,
                Blacklist.DEFAULT_SORT
        );
    }

    @Nullable
    static Cursor getWhiteListItemsCursor(Context context) {
        return context.getContentResolver().query(
                Whitelist.CONTENT_URI,
                new String[]{
                        Whitelist.HOSTNAME,
                        Whitelist.ENABLED
                },
                null,
                null,
                Whitelist.DEFAULT_SORT
        );
    }

    @Nullable
    static Cursor getRedirectionListItemsCursor(Context context) {
        return context.getContentResolver().query(
                RedirectionList.CONTENT_URI,
                new String[]{
                        RedirectionList.HOSTNAME,
                        RedirectionList.ENABLED,
                        RedirectionList.IP
                },
                null,
                null,
                Whitelist.DEFAULT_SORT
        );
    }
}
