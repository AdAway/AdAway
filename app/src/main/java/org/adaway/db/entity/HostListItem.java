package org.adaway.db.entity;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.NonNull;

/**
 * This entity represents a black, white or redirection list item.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
@Entity(tableName = "hosts_lists")
public class HostListItem {
    @PrimaryKey
    @NonNull
    private String host;

    @NonNull
    private ListType type;

    @NonNull
    private Boolean enabled;

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

    @NonNull
    public Boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(@NonNull Boolean enabled) {
        this.enabled = enabled;
    }

    public String getRedirection() {
        return redirection;
    }

    public void setRedirection(String redirection) {
        this.redirection = redirection;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HostListItem hostList = (HostListItem) o;

        if (!host.equals(hostList.host)) return false;
        if (type != hostList.type) return false;
        if (!enabled.equals(hostList.enabled)) return false;
        return redirection != null ? redirection.equals(hostList.redirection) : hostList.redirection == null;
    }

    @Override
    public int hashCode() {
        int result = host.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + enabled.hashCode();
        result = 31 * result + (redirection != null ? redirection.hashCode() : 0);
        return result;
    }
}
