package org.adaway.provider;

import android.content.Context;
import android.database.Cursor;

import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.util.Constants;
import org.adaway.util.Log;

import java.util.Date;

/**
 * This class is an helper to move from {@link AdAwayDatabase} (based on {@link android.database.sqlite.SQLiteOpenHelper}) to {@link AppDatabase} (based on Room).
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class RoomMigrationHelper {
    /**
     * Private constructor.
     */
    private RoomMigrationHelper() {

    }

    /**
     * Migrate data from {@link AdAwayDatabase} to {@link AppDatabase}.
     *
     * @param context  The application context.
     * @param database The {@link AppDatabase} instance.
     */
    public static void migrateToRoom(Context context, AppDatabase database) {
        // Get dao
        HostsSourceDao hostsSourceDao = database.hostsSourceDao();
        HostListItemDao hostListItemDao = database.hostsListItemDao();
        // Migrate data
        migrateHostsSources(context, hostsSourceDao);
        migrateBlackListItems(context, hostListItemDao);
        migrateWhiteListItems(context, hostListItemDao);
        migrateRedirectionListItems(context, hostListItemDao);
    }

    private static void migrateHostsSources(Context context, HostsSourceDao hostsSourceDao) {
        // Extract hosts sources
        try (Cursor cursor = ProviderHelper.getHostsSourcesCursor(context)) {
            if (cursor == null) {
                Log.w(Constants.TAG, "Unable to extract hosts sources from old database.");
            } else {
                while (cursor.moveToNext()) {
                    HostsSource source = new HostsSource();
                    source.setUrl(cursor.getString(0));
                    source.setEnabled(cursor.getInt(1) == 1);
                    source.setLastLocalModification(new Date(cursor.getLong(2)));
                    source.setLastOnlineModification(new Date(cursor.getLong(3)));
                    hostsSourceDao.insert(source);
                }
            }
        }
    }

    private static void migrateBlackListItems(Context context, HostListItemDao hostListItemDao) {
        try (Cursor cursor = ProviderHelper.getBlackListItemsCursor(context)) {
            if (cursor == null) {
                Log.w(Constants.TAG, "Unable to extract black list from old database.");
            } else {
                while (cursor.moveToNext()) {
                    HostListItem item = new HostListItem();
                    item.setHost(cursor.getString(0));
                    item.setType(ListType.BLACK_LIST);
                    item.setEnabled(cursor.getInt(1) == 1);
                    hostListItemDao.insert(item);
                }
            }
        }
    }

    private static void migrateWhiteListItems(Context context, HostListItemDao hostListItemDao) {
        try (Cursor cursor = ProviderHelper.getWhiteListItemsCursor(context)) {
            if (cursor == null) {
                Log.w(Constants.TAG, "Unable to extract white list from old database.");
            } else {
                while (cursor.moveToNext()) {
                    HostListItem item = new HostListItem();
                    item.setHost(cursor.getString(0));
                    item.setType(ListType.WHITE_LIST);
                    item.setEnabled(cursor.getInt(1) == 1);
                    hostListItemDao.insert(item);
                }
            }
        }
    }

    private static void migrateRedirectionListItems(Context context, HostListItemDao hostListItemDao) {
        try (Cursor cursor = ProviderHelper.getRedirectionListItemsCursor(context)) {
            if (cursor == null) {
                Log.w(Constants.TAG, "Unable to extract redirection list from old database.");
            } else {
                while (cursor.moveToNext()) {
                    HostListItem item = new HostListItem();
                    item.setHost(cursor.getString(0));
                    item.setType(ListType.REDIRECTION_LIST);
                    item.setEnabled(cursor.getInt(1) == 1);
                    item.setRedirection(cursor.getString(2));
                    hostListItemDao.insert(item);
                }
            }
        }
    }
}
