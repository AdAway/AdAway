package org.adaway.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * This entity represents an entry of the built hosts file.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
@Entity(
        tableName = "host_entries",
        indices = {@Index(value = "host", unique = true)}
)
public class HostEntry {
    @PrimaryKey
    @NonNull
    private String host;
    @NonNull
    private ListType type;
    private String redirection;

    @NonNull
    public String getHost() {
        return host;
    }

    public void setHost(@NonNull String host) {
        this.host = host;
    }

    @NonNull
    public ListType getType() {
        return type;
    }

    public void setType(@NonNull ListType type) {
        this.type = type;
    }

    public String getRedirection() {
        return redirection;
    }

    public void setRedirection(String redirection) {
        this.redirection = redirection;
    }
}
