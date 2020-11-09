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

    /**
     * Migration script from v3 to v4.
     */
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Remove unique constraint to hosts_lists.host column
            database.execSQL("DROP INDEX `index_hosts_lists_host`");
            database.execSQL("CREATE INDEX `index_hosts_lists_host` ON `hosts_lists` (`host`)");
            // Update host_entries view
            database.execSQL("DROP VIEW `host_entries`");
            database.execSQL("CREATE VIEW `host_entries` AS SELECT `host`, `type`, `redirection` FROM `hosts_lists` WHERE `enabled` = 1 AND ((`type` = 0 AND `host` NOT LIKE (SELECT `host` FROM `hosts_lists` WHERE `enabled` = 1 and `type` = 1)) OR `type` = 2) GROUP BY `host` ORDER BY `host` ASC, `type` DESC, `redirection` ASC");
        }
    };

    /**
     * Migration script from v4 to v5.
     */
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Remove host_entries view
            database.execSQL("DROP VIEW `host_entries`");
            // Create new host_entries table
            database.execSQL("CREATE TABLE IF NOT EXISTS `host_entries` (`host` TEXT NOT NULL, `type` INTEGER NOT NULL, `redirection` TEXT, PRIMARY KEY(`host`))");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_host_entries_host` ON `host_entries` (`host`)");
        }
    };

    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Update hosts_sources table
            database.execSQL("ALTER TABLE `hosts_sources` ADD `label` TEXT NOT NULL DEFAULT \"\"");
            database.execSQL("ALTER TABLE `hosts_sources` ADD `allowEnabled` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `hosts_sources` ADD `redirectEnabled` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `hosts_sources` ADD `size` INTEGER NOT NULL DEFAULT 0");
            // Set default values to new source attributes
            database.execSQL("UPDATE `hosts_sources` SET `label` = `url`");
            // Update user hosts list
            database.execSQL("UPDATE `hosts_sources` SET `url` = \"content://org.adaway/user/hosts\", `allowEnabled` = 1, `redirectEnabled` = 1 WHERE `url` = \"file://app/user/hosts\"");
            // Update default hosts source label
            database.execSQL("UPDATE `hosts_sources` SET `label` = \"AdAway official hosts\" WHERE `url` = \"https://adaway.org/hosts.txt\"");
            database.execSQL("UPDATE `hosts_sources` SET `label` = \"StevenBlack Unified hosts\" WHERE `url` = \"https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts\"");
            database.execSQL("UPDATE `hosts_sources` SET `label` = \"Pete Lowe blocklist hosts\" WHERE `url` = \"https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext\"");
            // Reset local date to rebuild cache
            database.execSQL("UPDATE `hosts_sources` SET `last_modified_local` = NULL");
            // Update hosts source date format
            database.execSQL("UPDATE `hosts_sources` SET `last_modified_online` = `last_modified_online` / 1000");
            // Clear previous file type hosts sources
            database.execSQL("DELETE FROM `hosts_sources` WHERE `url` LIKE \"file://%\"");
        }
    };
}
