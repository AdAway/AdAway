package org.adaway.db.dao

import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import org.adaway.db.entity.HostListItem
import java.util.Optional

@Dao
interface HostListItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg item: HostListItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(items: List<HostListItem>)

    @Update
    fun update(item: HostListItem)

    @Delete
    fun delete(item: HostListItem)

    @Query("DELETE FROM hosts_lists WHERE source_id = 1 AND host = :host")
    fun deleteUserFromHost(host: String)

    @Query("SELECT * FROM hosts_lists WHERE type = :type AND host LIKE :query AND ((:includeSources == 0 AND source_id == 1) || (:includeSources == 1)) GROUP BY host ORDER BY host ASC")
    fun loadList(type: Int, includeSources: Boolean, query: String): PagingSource<Int, HostListItem>

    @get:Query("SELECT * FROM hosts_lists ORDER BY host ASC")
    val all: List<HostListItem>

    @get:Query("SELECT * FROM hosts_lists WHERE source_id = 1")
    val userList: List<HostListItem>

    @Query("SELECT id FROM hosts_lists WHERE host = :host AND source_id = 1 LIMIT 1")
    fun getHostId(host: String): Optional<Int>

    @Query("SELECT COUNT(DISTINCT host) FROM hosts_lists WHERE type = 0 AND enabled = 1")
    fun getBlockedHostCount(): LiveData<Int>

    @Query("SELECT COUNT(DISTINCT host) FROM hosts_lists WHERE type = 1 AND enabled = 1")
    fun getAllowedHostCount(): LiveData<Int>

    @Query("SELECT COUNT(DISTINCT host) FROM hosts_lists WHERE type = 2 AND enabled = 1")
    fun getRedirectHostCount(): LiveData<Int>

    @Query("DELETE FROM hosts_lists WHERE source_id = :sourceId")
    fun clearSourceHosts(sourceId: Int)
}
