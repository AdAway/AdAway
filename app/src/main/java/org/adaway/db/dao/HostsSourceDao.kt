package org.adaway.db.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import org.adaway.db.entity.HostsSource
import java.time.ZonedDateTime
import java.util.Optional

@Dao
interface HostsSourceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(source: HostsSource)

    @Update
    fun update(source: HostsSource)

    @Delete
    fun delete(source: HostsSource)

    @get:Query("SELECT * FROM hosts_sources WHERE enabled = 1 AND id != 1 ORDER BY url ASC")
    val enabled: List<HostsSource>

    fun toggleEnabled(source: HostsSource) {
        val id = source.id
        val enabled = !source.isEnabled
        source.isEnabled = enabled
        setSourceEnabled(id, enabled)
        setSourceItemsEnabled(id, enabled)
    }

    @Query("UPDATE hosts_sources SET enabled = :enabled WHERE id =:id")
    fun setSourceEnabled(id: Int, enabled: Boolean)

    @Query("UPDATE hosts_lists SET enabled = :enabled WHERE source_id =:id")
    fun setSourceItemsEnabled(id: Int, enabled: Boolean)

    @Query("SELECT * FROM hosts_sources WHERE id = :id")
    fun getById(id: Int): Optional<HostsSource>

    @get:Query("SELECT * FROM hosts_sources WHERE id != 1 ORDER BY label ASC")
    val all: List<HostsSource>

    @Query("SELECT * FROM hosts_sources WHERE id != 1 ORDER BY label ASC")
    fun loadAll(): LiveData<List<HostsSource>>

    @Query("UPDATE hosts_sources SET last_modified_online = :dateTime WHERE id = :id")
    fun updateOnlineModificationDate(id: Int, dateTime: ZonedDateTime)

    @Query("UPDATE hosts_sources SET last_modified_local = :localModificationDate, last_modified_online = :onlineModificationDate WHERE id = :id")
    fun updateModificationDates(
        id: Int,
        localModificationDate: ZonedDateTime,
        onlineModificationDate: ZonedDateTime
    )

    @Query("UPDATE hosts_sources SET entityTag = :entityTag WHERE id = :id")
    fun updateEntityTag(id: Int, entityTag: String)

    @Query("UPDATE hosts_sources SET size = (SELECT count(id) FROM hosts_lists WHERE source_id = :id) WHERE id = :id")
    fun updateSize(id: Int)

    @Query("SELECT count(id) FROM hosts_sources WHERE enabled = 1 AND last_modified_online > last_modified_local")
    fun countOutdated(): LiveData<Int>

    @Query("SELECT count(id) FROM hosts_sources WHERE enabled = 1 AND last_modified_online <= last_modified_local")
    fun countUpToDate(): LiveData<Int>

    @Query("UPDATE hosts_sources SET last_modified_local = NULL, last_modified_online = NULL, entityTag = NULL, size = 0 WHERE id = :id")
    fun clearProperties(id: Int)
}
