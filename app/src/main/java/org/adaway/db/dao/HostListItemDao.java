package org.adaway.db.dao;

import android.arch.lifecycle.LiveData;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import org.adaway.db.entity.HostListItem;

import java.util.List;

/**
 * This interface is the DAO for {@link HostListItem} entities.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
@Dao
public interface HostListItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(HostListItem... item);

    @Update
    void update(HostListItem item);

    @Delete
    void delete(HostListItem item);

    @Query("SELECT host FROM hosts_lists WHERE type = 0 AND enabled = 1")
    List<String> getEnabledBlackListHosts();

    @Query("SELECT host FROM hosts_lists WHERE type = 1 AND enabled = 1")
    List<String> getEnabledWhiteListHosts();

    @Query("SELECT * FROM hosts_lists WHERE type = 2 AND enabled = 1")
    List<HostListItem> getEnabledRedirectList();

    @Query("SELECT * FROM hosts_lists ORDER BY host ASC")
    List<HostListItem> getAll();

    @Query("SELECT * FROM hosts_lists WHERE type = 0 ORDER BY host ASC")
    LiveData<List<HostListItem>> loadBlackList();

    @Query("SELECT * FROM hosts_lists WHERE type = 1 ORDER BY host ASC")
    LiveData<List<HostListItem>> loadWhiteList();

    @Query("SELECT * FROM hosts_lists WHERE type = 2 ORDER BY host ASC")
    LiveData<List<HostListItem>> loadRedirectionList();
}
