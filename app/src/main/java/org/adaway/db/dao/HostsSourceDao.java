package org.adaway.db.dao;

import android.arch.lifecycle.LiveData;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import org.adaway.db.entity.HostsSource;

import java.util.List;

@Dao
public interface HostsSourceDao {
    @Insert
    void insert(HostsSource source);

    @Update
    void update(HostsSource source);

    @Delete
    void delete(HostsSource source);

    @Query("SELECT * FROM hosts_sources ORDER BY url ASC")
    List<HostsSource> getAll();

    @Query("SELECT * FROM hosts_sources ORDER BY url ASC")
    LiveData<List<HostsSource>> loadAll();
}
