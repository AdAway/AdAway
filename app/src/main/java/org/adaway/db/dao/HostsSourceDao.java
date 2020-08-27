package org.adaway.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import org.adaway.db.entity.HostsSource;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static androidx.room.OnConflictStrategy.IGNORE;

/**
 * This interface is the DAO for {@link HostsSource} entities.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
@Dao
public interface HostsSourceDao {
    @Insert(onConflict = IGNORE)
    void insert(HostsSource source);

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
        setSourceEnabled(id, enabled);
        setSourceItemsEnabled(id, enabled);
    }

    @Query("UPDATE hosts_sources SET enabled = :enabled WHERE id =:id")
    void setSourceEnabled(int id, boolean enabled);

    @Query("UPDATE hosts_lists SET enabled = :enabled WHERE source_id =:id")
    void setSourceItemsEnabled(int id, boolean enabled);

    @Query("SELECT * FROM hosts_sources WHERE id = :id")
    Optional<HostsSource> getById(int id);

    @Query("SELECT * FROM hosts_sources WHERE id != 1 ORDER BY label ASC")
    List<HostsSource> getAll();

    @Query("SELECT * FROM hosts_sources WHERE id != 1 ORDER BY label ASC")
    LiveData<List<HostsSource>> loadAll();

    @Query("UPDATE hosts_sources SET last_modified_online = :dateTime WHERE id = :id")
    void updateOnlineModificationDate(int id, ZonedDateTime dateTime);

    @Query("UPDATE hosts_sources SET last_modified_local = :localModificationDate, last_modified_online = :onlineModificationDate WHERE id = :id")
    void updateModificationDates(int id, ZonedDateTime localModificationDate, ZonedDateTime onlineModificationDate);

    @Query("UPDATE hosts_sources SET size = (SELECT count(id) FROM hosts_lists WHERE source_id = :id) WHERE id = :id")
    void updateSize(int id);

    @Query("SELECT count(id) FROM hosts_sources WHERE enabled = 1 AND last_modified_online > last_modified_local")
    LiveData<Integer> countOutdated();

    @Query("SELECT count(id) FROM hosts_sources WHERE enabled = 1 AND last_modified_online <= last_modified_local")
    LiveData<Integer> countUpToDate();

    @Query("UPDATE hosts_sources SET last_modified_local = null, size = 0 WHERE id = :id")
    void clearProperties(int id);
}
