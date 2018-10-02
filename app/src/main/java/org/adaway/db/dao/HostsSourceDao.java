package org.adaway.db.dao;

import android.arch.lifecycle.LiveData;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

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

    @Query("SELECT * FROM hosts_sources WHERE enabled = 1 ORDER BY url ASC")
    List<HostsSource> getEnabled();

    @Query("SELECT * FROM hosts_sources ORDER BY url ASC")
    List<HostsSource> getAll();

    @Query("SELECT * FROM hosts_sources ORDER BY url ASC")
    LiveData<List<HostsSource>> loadAll();

    @Query("UPDATE hosts_sources SET last_modified_online = :date WHERE url = :url")
    void updateOnlineModificationDate(String url, Date date);

    @Query("UPDATE hosts_sources SET last_modified_local = :date WHERE enabled = 1")
    void updateEnabledLocalModificationDates(Date date);

    @Query("UPDATE hosts_sources SET last_modified_local = NULL")
    void clearLocalModificationDates();
}
