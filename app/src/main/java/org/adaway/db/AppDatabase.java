package org.adaway.db;

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.arch.persistence.room.TypeConverters;
import android.content.Context;
import android.support.annotation.NonNull;

import org.adaway.db.converter.DateConverter;
import org.adaway.db.converter.ListTypeConverter;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.util.AppExecutors;

@Database(entities = {HostsSource.class, HostListItem.class}, version = 1)
@TypeConverters({DateConverter.class, ListTypeConverter.class})
public abstract class AppDatabase extends RoomDatabase {
    /**
     * The database singleton instance.
     */
    private static volatile AppDatabase INSTANCE;

    /**
     * Get the database instance.
     *
     * @param context The application context.
     * @return The database instance.
     */
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "app.db"
                    ).addCallback(new Callback() {
                        @Override
                        public void onCreate(@NonNull SupportSQLiteDatabase db) {
                            AppExecutors.getInstance().diskIO().execute(() -> AppDatabase.initialize(INSTANCE));
                        }
                    }).build();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Initialize the database content.
     */
    private static void initialize(AppDatabase db) {
        HostsSourceDao hostsSourceDao = db.hostsSourceDao();
        // https://hosts-file.net
        HostsSource source1 = new HostsSource();
        source1.setUrl("https://hosts-file.net/ad_servers.txt");
        source1.setEnabled(true);
        hostsSourceDao.insert(source1);
        // https://pgl.yoyo.org/adservers/
        HostsSource source2 = new HostsSource();
        source2.setUrl("https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext");
        source2.setEnabled(true);
        hostsSourceDao.insert(source2);
        // AdAway's own mobile hosts
        HostsSource source3 = new HostsSource();
        source3.setUrl("https://adaway.org/hosts.txt");
        source3.setEnabled(true);
        hostsSourceDao.insert(source3);
    }

    /**
     * Get the hosts source DAO.
     *
     * @return The hosts source DAO.
     */
    public abstract HostsSourceDao hostsSourceDao();

    /**
     * Get the hosts list item DAO.
     *
     * @return The hosts list item DAO.
     */
    public abstract HostListItemDao hostsListItemDao();
}
