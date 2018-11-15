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

import android.net.Uri;
import android.provider.BaseColumns;

public class AdAwayContract {

    public interface HostsSourcesColumns {
        String URL = "url";
        String LAST_MODIFIED_LOCAL = "last_modified_local";
        String LAST_MODIFIED_ONLINE = "last_modified_online";
        String ENABLED = "enabled";
    }

    public interface ListColumns {
        String HOSTNAME = "url"; // is url because of legacy reasons
        String ENABLED = "enabled";
    }

    public interface WhitelistColumns extends ListColumns {

    }

    public interface BlacklistColumns extends ListColumns {

    }

    public interface RedirectionListColumns extends ListColumns {
        String IP = "ip";
    }

    public static final String CONTENT_AUTHORITY = "org.adaway";

    private static final Uri BASE_CONTENT_URI = Uri.parse("content://" + CONTENT_AUTHORITY);

    public static final String PATH_HOSTS_SOURCES = "hostssources";

    public static final String PATH_WHITELIST = "whitelist";

    public static final String PATH_BLACKLIST = "blacklist";

    public static final String PATH_REDIRECTION_LIST = "redirectionlist";

    public static class HostsSources implements HostsSourcesColumns, BaseColumns {
        public static final Uri CONTENT_URI = BASE_CONTENT_URI.buildUpon()
                .appendPath(PATH_HOSTS_SOURCES).build();

        /**
         * Use if multiple items get returned
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.adaway.hostssources";

        /**
         * Use if a single item is returned
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.adaway.hostssources";

        /**
         * Default "ORDER BY" clause.
         */
        public static final String DEFAULT_SORT = HostsSourcesColumns.URL + " ASC";

        public static Uri buildUri(String id) {
            return CONTENT_URI.buildUpon().appendPath(id).build();
        }

        public static String getId(Uri uri) {
            return uri.getLastPathSegment();
        }
    }

    public static class Whitelist implements WhitelistColumns, BaseColumns {
        public static final Uri CONTENT_URI = BASE_CONTENT_URI.buildUpon()
                .appendPath(PATH_WHITELIST).build();

        /**
         * Use if multiple items get returned
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.adaway.whitelist";

        /**
         * Use if a single item is returned
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.adaway.whitelist";

        /**
         * Default "ORDER BY" clause.
         */
        public static final String DEFAULT_SORT = WhitelistColumns.HOSTNAME + " ASC";

        public static Uri buildUri(String id) {
            return CONTENT_URI.buildUpon().appendPath(id).build();
        }

        public static String getId(Uri uri) {
            return uri.getLastPathSegment();
        }
    }

    public static class Blacklist implements BlacklistColumns, BaseColumns {
        public static final Uri CONTENT_URI = BASE_CONTENT_URI.buildUpon()
                .appendPath(PATH_BLACKLIST).build();

        /**
         * Use if multiple items get returned
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.adaway.blacklist";

        /**
         * Use if a single item is returned
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.adaway.blacklist";

        /**
         * Default "ORDER BY" clause.
         */
        public static final String DEFAULT_SORT = BlacklistColumns.HOSTNAME + " ASC";

        public static Uri buildUri(String id) {
            return CONTENT_URI.buildUpon().appendPath(id).build();
        }

        public static String getId(Uri uri) {
            return uri.getLastPathSegment();
        }
    }

    public static class RedirectionList implements RedirectionListColumns, BaseColumns {
        public static final Uri CONTENT_URI = BASE_CONTENT_URI.buildUpon()
                .appendPath(PATH_REDIRECTION_LIST).build();

        /**
         * Use if multiple items get returned
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.adaway.redirectionlist";

        /**
         * Use if a single item is returned
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.adaway.redirectionlist";

        /**
         * Default "ORDER BY" clause.
         */
        public static final String DEFAULT_SORT = RedirectionListColumns.HOSTNAME + " ASC";

        public static Uri buildUri(String id) {
            return CONTENT_URI.buildUpon().appendPath(id).build();
        }

        public static String getId(Uri uri) {
            return uri.getLastPathSegment();
        }
    }

    private AdAwayContract() {
    }
}
