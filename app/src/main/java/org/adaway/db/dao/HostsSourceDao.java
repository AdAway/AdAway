package org.adaway.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import org.adaway.db.entity.HostsSource;

import java.util.Date;
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

    @Query("SELECT * FROM hosts_sources WHERE id != 1 ORDER BY url ASC")
    List<HostsSource> getAll();

    @Query("SELECT id FROM hosts_sources")
    int[] getAllIds();

    @Query("SELECT * FROM hosts_sources WHERE id != 1 ORDER BY url ASC")
    LiveData<List<HostsSource>> loadAll();

    @Query("UPDATE hosts_sources SET last_modified_online = :date WHERE url = :url")
    void updateOnlineModificationDate(String url, Date date);

    @Query("UPDATE hosts_sources SET last_modified_local = :date WHERE enabled = 1")
    void updateEnabledLocalModificationDates(Date date);

    @Query("UPDATE hosts_sources SET last_modified_local = NULL")
    void clearLocalModificationDates();

    @Query("SELECT count(id) FROM hosts_sources WHERE enabled = 1 AND last_modified_online > last_modified_local + 24 * 60 * 60 * 1000")
    LiveData<Integer> countOutdated();

    @Query("SELECT count(id) FROM hosts_sources WHERE enabled = 1 AND last_modified_online <= last_modified_local + 24 * 60 * 60 * 1000")
    LiveData<Integer> countUpToDate();
}
