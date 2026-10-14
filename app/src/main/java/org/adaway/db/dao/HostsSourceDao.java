package org.adaway.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import org.adaway.db.entity.HostsSource;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static androidx.room.OnConflictStrategy.IGNORE;

/**
 * This interface is the DAO for {@link HostsSource} entities.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
@Dao
public interface HostsSourceDao {
    String SOURCE_ACTIVE_ROWS =
            "`source_id` = :id AND (:id = 1 OR `generation` = " +
                    "(SELECT `active_generation` FROM `hosts_meta` WHERE `id` = 0))";
    String SOURCE_ACTIVE_ENABLED_ROWS = SOURCE_ACTIVE_ROWS + " AND `enabled` = 1";

    @Insert(onConflict = IGNORE)
    void insert(HostsSource source);

    @Insert(onConflict = IGNORE)
    void insertAll(List<HostsSource> sources);

    @Update
    void update(HostsSource source);

    @Delete
    void delete(HostsSource source);

    @Query("SELECT * FROM hosts_sources WHERE enabled = 1 AND id != 1 ORDER BY url ASC")
    List<HostsSource> getEnabled();

    default void toggleEnabled(HostsSource source) {
        int id = source.getId();
        boolean enabled = !source.isEnabled();
        source.setEnabled(enabled);
        applySourceSelection(id, enabled);
    }

    @Query("UPDATE hosts_sources SET enabled = :enabled WHERE id =:id")
    void setSourceEnabled(int id, boolean enabled);

    @Query("UPDATE hosts_lists SET enabled = :enabled WHERE source_id =:id")
    void setSourceItemsEnabled(int id, boolean enabled);

    @Transaction
    default void applySourceSelection(int id, boolean enabled) {
        setSourceEnabled(id, enabled);
        setSourceItemsEnabled(id, enabled);
    }

    @Transaction
    default void applySourceSelections(Set<String> enabledUrls) {
        for (HostsSource source : getAll()) {
            String url = source.getUrl();
            if (url == null) {
                continue;
            }
            boolean shouldEnable = enabledUrls.contains(url);
            if (source.isEnabled() == shouldEnable) {
                continue;
            }
            setSourceEnabled(source.getId(), shouldEnable);
            setSourceItemsEnabled(source.getId(), shouldEnable);
        }
    }

    @Query("SELECT * FROM hosts_sources WHERE id = :id")
    Optional<HostsSource> getById(int id);

    @Query("SELECT * FROM hosts_sources WHERE url = :url LIMIT 1")
    Optional<HostsSource> getByUrl(String url);

    @Query("SELECT * FROM hosts_sources WHERE id != 1 ORDER BY label ASC")
    List<HostsSource> getAll();

    @Query("SELECT * FROM hosts_sources WHERE id != 1 ORDER BY label ASC")
    LiveData<List<HostsSource>> loadAll();

    @Query("UPDATE hosts_sources SET last_modified_online = :dateTime WHERE id = :id")
    void updateOnlineModificationDate(int id, ZonedDateTime dateTime);

    @Query("UPDATE hosts_sources SET last_modified_local = :localModificationDate, last_modified_online = :onlineModificationDate WHERE id = :id")
    void updateModificationDates(int id, ZonedDateTime localModificationDate, ZonedDateTime onlineModificationDate);

    @Query("UPDATE hosts_sources SET entityTag = :entityTag WHERE id = :id")
    void updateEntityTag(int id, String entityTag);

    @Query("UPDATE hosts_sources SET " +
            "size = (SELECT count(id) FROM hosts_lists WHERE source_id = :id), " +
            "active_rule_count = (SELECT count(id) FROM hosts_lists " +
            "WHERE source_id = :id AND enabled = 1), " +
            "blocked_count = (SELECT count(id) FROM hosts_lists " +
            "WHERE source_id = :id AND enabled = 1 AND type = 0), " +
            "blocked_exact_count = (SELECT count(id) FROM hosts_lists " +
            "WHERE source_id = :id AND enabled = 1 AND type = 0 AND kind = 0), " +
            "allowed_count = (SELECT count(id) FROM hosts_lists " +
            "WHERE source_id = :id AND enabled = 1 AND type = 1), " +
            "redirected_count = (SELECT count(id) FROM hosts_lists " +
            "WHERE source_id = :id AND enabled = 1 AND type = 2) " +
            "WHERE id = :id")
    void updateSize(int id);

    @Query("WITH stats AS (" +
            "SELECT COUNT(id) AS size, " +
            "COALESCE(SUM(CASE WHEN enabled = 1 THEN 1 ELSE 0 END), 0) " +
            "AS active_rule_count, " +
            "COALESCE(SUM(CASE WHEN enabled = 1 AND type = 0 THEN 1 ELSE 0 END), 0) " +
            "AS blocked_count, " +
            "COALESCE(SUM(CASE WHEN enabled = 1 AND type = 0 AND kind = 0 " +
            "THEN 1 ELSE 0 END), 0) AS blocked_exact_count, " +
            "COALESCE(SUM(CASE WHEN enabled = 1 AND type = 1 THEN 1 ELSE 0 END), 0) " +
            "AS allowed_count, " +
            "COALESCE(SUM(CASE WHEN enabled = 1 AND type = 2 THEN 1 ELSE 0 END), 0) " +
            "AS redirected_count " +
            "FROM hosts_lists WHERE source_id = :id AND generation = :generation) " +
            "UPDATE hosts_sources SET " +
            "size = (SELECT size FROM stats), " +
            "active_rule_count = (SELECT active_rule_count FROM stats), " +
            "blocked_count = (SELECT blocked_count FROM stats), " +
            "blocked_exact_count = (SELECT blocked_exact_count FROM stats), " +
            "allowed_count = (SELECT allowed_count FROM stats), " +
            "redirected_count = (SELECT redirected_count FROM stats) " +
            "WHERE id = :id")
    void updateSizeForGeneration(int id, int generation);

    @Query("UPDATE hosts_sources SET " +
            "size = (SELECT count(id) FROM hosts_lists WHERE " + SOURCE_ACTIVE_ROWS + "), " +
            "active_rule_count = (SELECT count(id) FROM hosts_lists WHERE " +
            SOURCE_ACTIVE_ENABLED_ROWS + "), " +
            "blocked_count = (SELECT count(id) FROM hosts_lists WHERE " +
            SOURCE_ACTIVE_ENABLED_ROWS + " AND type = 0), " +
            "blocked_exact_count = (SELECT count(id) FROM hosts_lists WHERE " +
            SOURCE_ACTIVE_ENABLED_ROWS + " AND type = 0 AND kind = 0), " +
            "allowed_count = (SELECT count(id) FROM hosts_lists WHERE " +
            SOURCE_ACTIVE_ENABLED_ROWS + " AND type = 1), " +
            "redirected_count = (SELECT count(id) FROM hosts_lists WHERE " +
            SOURCE_ACTIVE_ENABLED_ROWS + " AND type = 2) " +
            "WHERE id = :id")
    void updateActiveRuleStats(int id);

    @Query("UPDATE hosts_sources SET size = :size, active_rule_count = :activeRuleCount, " +
            "blocked_count = :blockedCount, blocked_exact_count = :blockedExactCount, " +
            "allowed_count = :allowedCount, redirected_count = :redirectedCount " +
            "WHERE id = :id")
    void updateRuleStats(int id, int size, int activeRuleCount, int blockedCount,
            int blockedExactCount, int allowedCount, int redirectedCount);

    // An enabled subscription needs an update until its first successful download.
    @Query("SELECT count(id) FROM hosts_sources WHERE enabled = 1 AND id != 1 " +
            "AND (last_modified_local IS NULL OR last_modified_online > last_modified_local)")
    LiveData<Integer> countOutdated();

    @Query("SELECT count(id) FROM hosts_sources WHERE enabled = 1 AND id != 1 " +
            "AND last_modified_online <= last_modified_local")
    LiveData<Integer> countUpToDate();

    @Query("UPDATE hosts_sources SET last_modified_local = NULL, last_modified_online = NULL, " +
            "entityTag = NULL, size = 0, active_rule_count = 0, blocked_count = 0, " +
            "blocked_exact_count = 0, allowed_count = 0, redirected_count = 0 " +
            "WHERE id = :id")
    void clearProperties(int id);

    @Query("UPDATE hosts_sources SET skipped_count = :skippedCount WHERE id = :id")
    void updateSkippedCount(int id, int skippedCount);

    @Query("UPDATE hosts_sources SET last_download_error = :error WHERE id = :id")
    void updateDownloadError(int id, String error);

    @Query("UPDATE hosts_sources SET last_download_error = NULL WHERE id = :id")
    void clearDownloadError(int id);

    @Query("DELETE FROM hosts_sources WHERE id != 1")
    void deleteAll();
}
