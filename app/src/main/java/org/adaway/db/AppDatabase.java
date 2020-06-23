package org.adaway.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;

import org.adaway.db.converter.ListTypeConverter;
import org.adaway.db.converter.ZonedDateTimeConverter;
import org.adaway.db.dao.HostEntryDao;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.HostEntry;
import org.adaway.util.AppExecutors;

import static org.adaway.db.Migrations.MIGRATION_1_2;
import static org.adaway.db.Migrations.MIGRATION_2_3;
import static org.adaway.db.Migrations.MIGRATION_3_4;
import static org.adaway.db.Migrations.MIGRATION_4_5;
import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.db.entity.HostsSource.USER_SOURCE_URL;

/**
 * This class is the application database based on Room.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
@Database(entities = {HostsSource.class, HostListItem.class, HostEntry.class}, version = 5)
@TypeConverters({ListTypeConverter.class, ZonedDateTimeConverter.class})
public abstract class AppDatabase extends RoomDatabase {
    /**
     * The database singleton instance.
     */
    private static volatile AppDatabase instance;

    /**
     * Get the database instance.
     *
     * @param context The application context.
     * @return The database instance.
     */
    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "app.db"
                    ).addCallback(new Callback() {
                        @Override
                        public void onCreate(@NonNull SupportSQLiteDatabase db) {
                            AppExecutors.getInstance().diskIO().execute(
                                    () -> AppDatabase.initialize(instance)
                            );
                        }
                    }).addMigrations(
                            MIGRATION_1_2,
                            MIGRATION_2_3,
                            MIGRATION_3_4,
                            MIGRATION_4_5
                    ).build();
                }
            }
        }
        return instance;
    }

    /**
     * Initialize the database content.
     */
    private static void initialize(AppDatabase database) {
        // Check if there is no hosts source
        HostsSourceDao hostsSourceDao = database.hostsSourceDao();
        if (!hostsSourceDao.getAll().isEmpty()) {
            return;
        }
        // User source
        HostsSource userSource = new HostsSource();
        userSource.setId(USER_SOURCE_ID);
        userSource.setUrl(USER_SOURCE_URL);
        userSource.setEnabled(true);
        hostsSourceDao.insert(userSource);
        // AdAway's own mobile hosts
        HostsSource source1 = new HostsSource();
        source1.setUrl("https://adaway.org/hosts.txt");
        source1.setEnabled(true);
        hostsSourceDao.insert(source1);
        // https://github.com/StevenBlack/hosts
        HostsSource source2 = new HostsSource();
        source2.setUrl("https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts");
        source2.setEnabled(true);
        hostsSourceDao.insert(source2);
        // https://pgl.yoyo.org/adservers/
        HostsSource source3 = new HostsSource();
        source3.setUrl("https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext");
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

    /**
     * Get the hosts entry DAO.
     *
     * @return The hosts entry DAO.
     */
    public abstract HostEntryDao hostEntryDao();
}
