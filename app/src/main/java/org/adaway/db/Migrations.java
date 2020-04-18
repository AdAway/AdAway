package org.adaway.db;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.db.entity.HostsSource.USER_SOURCE_URL;

/**
 * This class declares database schema migrations.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class Migrations {
    /**
     * The migration script from v1 to v2.
     */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Add hosts sources id column and migrate data
            database.execSQL("CREATE TABLE `hosts_sources_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `url` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `last_modified_local` INTEGER, `last_modified_online` INTEGER)");
            database.execSQL("INSERT INTO `hosts_sources_new` (`id`, `url`, `enabled`) VALUES (" + USER_SOURCE_ID + ", '" + USER_SOURCE_URL + "', 1)");
            database.execSQL("INSERT INTO `hosts_sources_new` (`url`, `enabled`, `last_modified_local`, `last_modified_online`) SELECT `url`, `enabled`, `last_modified_local`, `last_modified_online` FROM `hosts_sources`");
            database.execSQL("DROP TABLE `hosts_sources`");
            database.execSQL("ALTER TABLE `hosts_sources_new` RENAME TO `hosts_sources`");
            // Add hosts list source id and migrate data
            database.execSQL("CREATE TABLE `hosts_lists_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `host` TEXT NOT NULL, `type` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `redirection` TEXT, `source_id` INTEGER NOT NULL, FOREIGN KEY(`source_id`) REFERENCES `hosts_sources`(`id`) ON UPDATE CASCADE ON DELETE CASCADE )");
            database.execSQL("INSERT INTO `hosts_lists_new` (`host`, `type`, `enabled`, `redirection`, `source_id`) SELECT `host`, `type`, `enabled`, `redirection`, " + USER_SOURCE_ID + " FROM `hosts_lists`");
            database.execSQL("DROP TABLE `hosts_lists`");
            database.execSQL("ALTER TABLE `hosts_lists_new` RENAME TO `hosts_lists`");
            // Create index
            database.execSQL("CREATE UNIQUE INDEX `index_hosts_sources_url` ON `hosts_sources` (`url`)");
            database.execSQL("CREATE UNIQUE INDEX `index_hosts_lists_host` ON `hosts_lists` (`host`)");
            database.execSQL("CREATE INDEX `index_hosts_lists_source_id` ON `hosts_lists` (`source_id`)");
        }
    };
    /**
     * The migration script from v2 to v3.
     */
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE VIEW `host_entries` AS SELECT `host`, `type`, `redirection` FROM `hosts_lists` WHERE `enabled` = 1 AND ((`type` = 0 AND `host` NOT LIKE (SELECT `host` FROM `hosts_lists` WHERE `enabled` = 1 and `type` = 1)) OR `type` = 2) ORDER BY `host` ASC, `type` DESC, `redirection` ASC");
        }
    };
}
