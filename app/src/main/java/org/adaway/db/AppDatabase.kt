package org.adaway.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import org.adaway.R
import org.adaway.db.Migrations.MIGRATION_1_2
import org.adaway.db.Migrations.MIGRATION_2_3
import org.adaway.db.Migrations.MIGRATION_3_4
import org.adaway.db.Migrations.MIGRATION_4_5
import org.adaway.db.Migrations.MIGRATION_5_6
import org.adaway.db.Migrations.MIGRATION_6_7
import org.adaway.db.converter.ListTypeConverter
import org.adaway.db.converter.ZonedDateTimeConverter
import org.adaway.db.dao.HostEntryDao
import org.adaway.db.dao.HostListItemDao
import org.adaway.db.dao.HostsSourceDao
import org.adaway.db.entity.HostEntry
import org.adaway.db.entity.HostListItem
import org.adaway.db.entity.HostsSource
import org.adaway.db.entity.HostsSource.USER_SOURCE_ID
import org.adaway.db.entity.HostsSource.USER_SOURCE_URL
import org.adaway.util.CoroutineDispatchers

@Database(entities = [HostsSource::class, HostListItem::class, HostEntry::class], version = 7)
@TypeConverters(ListTypeConverter::class, ZonedDateTimeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hostsSourceDao(): HostsSourceDao

    abstract fun hostsListItemDao(): HostListItemDao

    abstract fun hostEntryDao(): HostEntryDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        @JvmStatic
        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(AppDatabase::class.java) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app.db"
                )
                    .addCallback(
                        object : Callback() {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                CoroutineDispatchers.ioExecutor().execute {
                                    initialize(context, requireNotNull(instance))
                                }
                            }
                        }
                    )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7
                    )
                    .build()
                    .also { instance = it }
            }
        }

        private fun initialize(context: Context, database: AppDatabase) {
            val hostsSourceDao = database.hostsSourceDao()
            if (hostsSourceDao.all.isNotEmpty()) {
                return
            }

            val userSource = HostsSource().apply {
                label = context.getString(R.string.hosts_user_source)
                id = USER_SOURCE_ID
                url = USER_SOURCE_URL
                setAllowEnabled(true)
                setRedirectEnabled(true)
            }
            hostsSourceDao.insert(userSource)

            hostsSourceDao.insert(
                HostsSource().apply {
                    label = context.getString(R.string.hosts_adaway_source)
                    url = "https://adaway.org/hosts.txt"
                }
            )
            hostsSourceDao.insert(
                HostsSource().apply {
                    label = context.getString(R.string.hosts_stevenblack_source)
                    url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
                }
            )
            hostsSourceDao.insert(
                HostsSource().apply {
                    label = context.getString(R.string.hosts_peterlowe_source)
                    url = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext"
                }
            )
        }
    }
}
