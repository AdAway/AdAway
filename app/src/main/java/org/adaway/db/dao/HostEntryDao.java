package org.adaway.db.dao;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import org.adaway.db.entity.HostEntry;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.ListType;

import java.util.List;
import java.util.regex.Pattern;

import static androidx.room.OnConflictStrategy.REPLACE;
import static org.adaway.db.entity.ListType.REDIRECTED;

/**
 * This interface is the DAO for {@link HostEntry} records.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
@Dao
public interface HostEntryDao {
    Pattern ANY_CHAR_PATTERN = Pattern.compile("\\*");
    Pattern A_CHAR_PATTERN = Pattern.compile("\\?");

    @Query("DELETE FROM `host_entries`")
    void clear();

    @Query("INSERT INTO `host_entries` SELECT DISTINCT `host`, `type`, `redirection` FROM `hosts_lists` WHERE `type` = 0 AND `enabled` = 1")
    void importBlocked();

    @Query("SELECT host FROM hosts_lists WHERE type = 1 AND enabled = 1")
    List<String> getEnabledAllowedHosts();

    @Query("DELETE FROM `host_entries` WHERE `host` LIKE :hostPattern")
    void allowHost(String hostPattern);

    @Query("SELECT * FROM hosts_lists WHERE type = 2 AND enabled = 1 ORDER BY host ASC, source_id DESC")
    List<HostListItem> getEnabledRedirectedHosts();

    @Insert(onConflict = REPLACE)
    void redirectHost(HostEntry redirection);

    /**
     * Synchronize the host entries based on the current hosts lists table records.
     */
    default void sync() {
        clear();
        importBlocked();
        for (String allowedHost : getEnabledAllowedHosts()) {
            allowedHost = ANY_CHAR_PATTERN.matcher(allowedHost).replaceAll("%");
            allowedHost = A_CHAR_PATTERN.matcher(allowedHost).replaceAll("_");
            allowHost(allowedHost);
        }
        for (HostListItem redirectedHost : getEnabledRedirectedHosts()) {
            HostEntry entry = new HostEntry();
            entry.setHost(redirectedHost.getHost());
            entry.setType(REDIRECTED);
            entry.setRedirection(redirectedHost.getRedirection());
            redirectHost(entry);
        }
    }

    @Query("SELECT * FROM `host_entries` ORDER BY `host`")
    List<HostEntry> getAll();

    @Query("SELECT `type` FROM `host_entries` WHERE `host` == :host LIMIT 1")
    ListType getTypeOfHost(String host);

    @Query("SELECT IFNULL((SELECT `type` FROM `host_entries` WHERE `host` == :host LIMIT 1), 1)")
    ListType getTypeForHost(String host);

    @Nullable
    @Query("SELECT * FROM `host_entries` WHERE `host` == :host LIMIT 1")
    HostEntry getEntry(String host);
}
