package org.adaway.db.dao;

import androidx.lifecycle.LiveData;
import androidx.paging.DataSource;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

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

    @Query("DELETE FROM hosts_lists WHERE source_id = 1 AND host = :host")
    void deleteUserFromHost(String host);

    @Query("SELECT host FROM hosts_lists WHERE type = 0 AND enabled = 1")
    List<String> getEnabledBlackListHosts();

    @Query("SELECT host FROM hosts_lists WHERE type = 1 AND enabled = 1")
    List<String> getEnabledWhiteListHosts();

    @Query("SELECT * FROM hosts_lists WHERE type = 2 AND enabled = 1")
    List<HostListItem> getEnabledRedirectList();

    @Query("SELECT * FROM hosts_lists WHERE type = :type AND host LIKE :query AND ((:includeSources == 0 AND source_id == 1) || (:includeSources == 1)) ORDER BY host ASC")
    DataSource.Factory<Integer, HostListItem> loadList(int type, boolean includeSources, String query);

    @Query("SELECT * FROM hosts_lists ORDER BY host ASC")
    List<HostListItem> getAll();

    @Query("SELECT * FROM hosts_lists WHERE source_id = 1")
    List<HostListItem> getUserList();

    @Query("SELECT * FROM hosts_lists WHERE source_id = 1")
    LiveData<List<HostListItem>> loadUserList();

    @Query("SELECT COUNT(id) FROM hosts_lists WHERE type = 0 AND enabled = 1")
    LiveData<Integer> getBlockedHostCount();

    @Query("SELECT COUNT(id) FROM hosts_lists WHERE type = 1 AND enabled = 1")
    LiveData<Integer> getAllowedHostCount();

    @Query("SELECT COUNT(id) FROM hosts_lists WHERE type = 2 AND enabled = 1")
    LiveData<Integer> getRedirectHostCount();

    @Query("SELECT COUNT(id)>0 FROM hosts_lists WHERE type = 0 AND enabled = 1 AND host = :host")
    boolean isHostBlocked(String host);

    @Query("DELETE FROM hosts_lists WHERE source_id = :sourceId")
    void clearSourceHosts(int sourceId);
}
