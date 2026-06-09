package org.adaway.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.adaway.db.entity.HostEntry
import org.adaway.db.entity.HostListItem
import org.adaway.db.entity.ListType
import org.adaway.db.entity.ListType.REDIRECTED
import java.util.regex.Pattern

@Dao
interface HostEntryDao {
    @Query("DELETE FROM `host_entries`")
    fun clear()

    @Query("INSERT INTO `host_entries` SELECT DISTINCT `host`, `type`, `redirection` FROM `hosts_lists` WHERE `type` = 0 AND `enabled` = 1")
    fun importBlocked()

    @get:Query("SELECT host FROM hosts_lists WHERE type = 1 AND enabled = 1")
    val enabledAllowedHosts: List<String>

    @Query("DELETE FROM `host_entries` WHERE `host` LIKE :hostPattern")
    fun allowHost(hostPattern: String)

    @get:Query("SELECT * FROM hosts_lists WHERE type = 2 AND enabled = 1 ORDER BY host ASC, source_id DESC")
    val enabledRedirectedHosts: List<HostListItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun redirectHost(redirection: HostEntry)

    fun sync() {
        clear()
        importBlocked()
        for (allowedHost in enabledAllowedHosts) {
            val hostPattern = A_CHAR_PATTERN.matcher(
                ANY_CHAR_PATTERN.matcher(allowedHost).replaceAll("%")
            ).replaceAll("_")
            allowHost(hostPattern)
        }
        for (redirectedHost in enabledRedirectedHosts) {
            val entry = HostEntry().apply {
                host = redirectedHost.host
                type = REDIRECTED
                redirection = redirectedHost.redirection
            }
            redirectHost(entry)
        }
    }

    @get:Query("SELECT * FROM `host_entries` ORDER BY `host`")
    val all: List<HostEntry>

    @Query("SELECT `type` FROM `host_entries` WHERE `host` == :host LIMIT 1")
    fun getTypeOfHost(host: String): ListType

    @Query("SELECT IFNULL((SELECT `type` FROM `host_entries` WHERE `host` == :host LIMIT 1), 1)")
    fun getTypeForHost(host: String): ListType

    @Query("SELECT * FROM `host_entries` WHERE `host` == :host LIMIT 1")
    fun getEntry(host: String): HostEntry?

    companion object {
        private val ANY_CHAR_PATTERN: Pattern = Pattern.compile("\\*")
        private val A_CHAR_PATTERN: Pattern = Pattern.compile("\\?")
    }
}
