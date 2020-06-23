package org.adaway.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import org.adaway.db.entity.HostsSource;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * This interface is the DAO for {@link HostsSource} entities.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
@Dao
public interface HostsSourceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
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

    @Query("SELECT * FROM hosts_sources WHERE id != 1 ORDER BY url ASC")
    List<HostsSource> getAll();

    @Query("SELECT * FROM hosts_sources WHERE id != 1 ORDER BY url ASC")
    LiveData<List<HostsSource>> loadAll();

    @Query("UPDATE hosts_sources SET last_modified_local = :localModificationDate, last_modified_online = :onlineModificationDate WHERE id = :id")
    void updateModificationDates(int id, ZonedDateTime localModificationDate, ZonedDateTime onlineModificationDate);

    @Query("UPDATE hosts_sources SET last_modified_online = :dateTime WHERE id = :id")
    void updateOnlineModificationDate(int id, ZonedDateTime dateTime);

    @Query("SELECT count(id) FROM hosts_sources WHERE enabled = 1 AND last_modified_online > last_modified_local")
    LiveData<Integer> countOutdated();

    @Query("SELECT count(id) FROM hosts_sources WHERE enabled = 1 AND last_modified_online <= last_modified_local")
    LiveData<Integer> countUpToDate();
}
