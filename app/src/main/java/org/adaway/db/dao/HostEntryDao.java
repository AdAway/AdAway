package org.adaway.db.dao;

import androidx.room.Dao;
import androidx.room.Query;

import org.adaway.db.entity.ListType;
import org.adaway.db.view.HostEntry;

import java.util.List;

/**
 * This interface is the DAO for {@link HostEntry} records.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
@Dao
public interface HostEntryDao {
    @Query("SELECT * FROM `host_entries`")
    List<HostEntry> getAll();

    @Query("SELECT `type` FROM `host_entries` WHERE `host` == :host")
    ListType getTypeOfHost(String host);
}
