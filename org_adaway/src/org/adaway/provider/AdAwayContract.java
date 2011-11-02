package org.adaway.provider;

import android.net.Uri;
import android.provider.BaseColumns;

public class AdAwayContract {

    interface HostsSourcesColumns {
        String URL = "url";
        String LAST_MODIFIED_LOCAL = "last_modified_local";
        String LAST_MODIFIED_ONLINE = "last_modified_online";
        String ENABLED = "enabled";
    }

    interface WhitelistColumns {
        String URL = "url"; // TODO: rename to hostname
        String ENABLED = "enabled";
    }

    interface BlacklistColumns {
        String URL = "url";// TODO: rename to hostname
        String ENABLED = "enabled";
    }

    interface RedirectionListColumns {
        String URL = "url";// TODO: rename to hostname
        String IP = "ip";
        String ENABLED = "enabled";
    }

    public static final String CONTENT_AUTHORITY = "org.adaway";

    private static final Uri BASE_CONTENT_URI = Uri.parse("content://" + CONTENT_AUTHORITY);

    public static final String PATH_HOSTS_SORCES = "hostssources";

    public static final String PATH_WHITELIST = "whitelist";

    public static final String PATH_BLACKLIST = "blacklist";

    public static final String PATH_REDIRECTION_LIST = "redirectionlist";

    public static class HostsSources implements HostsSourcesColumns, BaseColumns {
        public static final Uri CONTENT_URI = BASE_CONTENT_URI.buildUpon()
                .appendPath(PATH_HOSTS_SORCES).build();

        /** Use if multiple items get returned */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.adaway.hostssource";

        /** Use if a single item is returned */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.adaway.hostssource";

        /** Default "ORDER BY" clause. */
        public static final String DEFAULT_SORT = HostsSourcesColumns.URL + " ASC";

        // public static Uri buildShowUri(String showId) {
        // return CONTENT_URI.buildUpon().appendPath(showId).build();
        // }
        //
        // public static String getShowId(Uri uri) {
        // return uri.getLastPathSegment();
        // }
    }

    public static class Whitelist implements WhitelistColumns, BaseColumns {
        public static final Uri CONTENT_URI = BASE_CONTENT_URI.buildUpon()
                .appendPath(PATH_WHITELIST).build();

        /** Use if multiple items get returned */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.adaway.whitelist";

        /** Use if a single item is returned */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.adaway.whitelist";

        /** Default "ORDER BY" clause. */
        public static final String DEFAULT_SORT = WhitelistColumns.URL + " ASC";

        // public static Uri buildShowUri(String showId) {
        // return CONTENT_URI.buildUpon().appendPath(showId).build();
        // }
        //
        // public static String getShowId(Uri uri) {
        // return uri.getLastPathSegment();
        // }
    }

    public static class Blacklist implements BlacklistColumns, BaseColumns {
        public static final Uri CONTENT_URI = BASE_CONTENT_URI.buildUpon()
                .appendPath(PATH_BLACKLIST).build();

        /** Use if multiple items get returned */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.adaway.blacklist";

        /** Use if a single item is returned */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.adaway.blacklist";

        /** Default "ORDER BY" clause. */
        public static final String DEFAULT_SORT = BlacklistColumns.URL + " ASC";

        // public static Uri buildShowUri(String showId) {
        // return CONTENT_URI.buildUpon().appendPath(showId).build();
        // }
        //
        // public static String getShowId(Uri uri) {
        // return uri.getLastPathSegment();
        // }
    }

    public static class RedirectionList implements HostsSourcesColumns, BaseColumns {
        public static final Uri CONTENT_URI = BASE_CONTENT_URI.buildUpon()
                .appendPath(PATH_REDIRECTION_LIST).build();

        /** Use if multiple items get returned */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.adaway.redirectionlist";

        /** Use if a single item is returned */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.adaway.redirectionlist";

        /** Default "ORDER BY" clause. */
        public static final String DEFAULT_SORT = RedirectionListColumns.URL + " ASC";

        // public static Uri buildShowUri(String showId) {
        // return CONTENT_URI.buildUpon().appendPath(showId).build();
        // }
        //
        // public static String getShowId(Uri uri) {
        // return uri.getLastPathSegment();
        // }
    }

    private AdAwayContract() {
    }
}
