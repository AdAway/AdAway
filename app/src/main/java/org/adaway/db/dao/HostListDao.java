package org.adaway.db.dao;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import org.adaway.db.entity.HostList;

import java.util.List;

@Dao
public interface HostListDao {
    @Insert
    void insert(HostList list);

    @Update
    void update(HostList list);

    @Delete
    void delete(HostList list);

    @Query("SELECT * FROM hosts_lists ORDER BY host ASC")
    List<HostList> getAll();

    @Query("SELECT * FROM hosts_lists WHERE type = 0 ORDER BY host ASC")
    List<HostList> getBlackList();

    @Query("SELECT * FROM hosts_lists WHERE type = 1 ORDER BY host ASC")
    List<HostList> getWhiteList();

    @Query("SELECT * FROM hosts_lists WHERE type = 2 ORDER BY host ASC")
    List<HostList> getRedirectionList();
}
